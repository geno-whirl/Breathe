package com.genowhirl.breathe.model

import kotlin.math.PI
import kotlin.math.cos

sealed interface RunnerEvent {
    data class PhaseStarted(val phase: Phase, val durationMs: Long, val cycle: Int) : RunnerEvent
    data object Finished : RunnerEvent
}

/**
 * Pure timekeeping for a breathing session. Times are monotonic milliseconds supplied by the
 * caller, which keeps this class free of Android and easy to test.
 *
 * The pattern and the session limit are read at every phase boundary, so edits made while a
 * session runs take effect from the next stage on.
 */
class BreathRunner(
    private val pattern: () -> BreathPattern,
    private val limit: () -> SessionLimit = { SessionLimit() },
) {
    var phase: Phase = Phase.INHALE
        private set
    var phaseStart: Long = 0L
        private set
    var phaseDuration: Long = 0L
        private set

    /** 1-based number of the cycle in progress. */
    var cycle: Int = 1
        private set
    var isPaused: Boolean = false
        private set
    var isFinished: Boolean = false
        private set

    private var activeBeforePhase = 0L
    private var pausedAt = 0L

    val phaseEnd: Long get() = phaseStart + phaseDuration

    fun start(now: Long): List<RunnerEvent> {
        cycle = 1
        activeBeforePhase = 0L
        isPaused = false
        isFinished = false
        val durations = pattern().phaseMillis()
        val first = firstNonEmpty(durations, Phase.INHALE)
        enter(first, now, durations[first.ordinal])
        return listOf(RunnerEvent.PhaseStarted(phase, phaseDuration, cycle))
    }

    fun pause(now: Long) {
        if (isPaused || isFinished) return
        isPaused = true
        pausedAt = now
    }

    fun resume(now: Long) {
        if (!isPaused) return
        phaseStart += now - pausedAt
        isPaused = false
    }

    /** Time spent breathing, excluding pauses. */
    fun activeMillis(now: Long): Long {
        val reference = if (isPaused) pausedAt else now
        return activeBeforePhase + (reference - phaseStart).coerceIn(0L, phaseDuration)
    }

    fun phaseElapsed(now: Long): Long {
        val reference = if (isPaused) pausedAt else now
        return (reference - phaseStart).coerceIn(0L, phaseDuration)
    }

    /** Advances past every phase boundary that lies at or before [now]. */
    fun update(now: Long): List<RunnerEvent> {
        if (isPaused || isFinished) return emptyList()
        val events = mutableListOf<RunnerEvent>()
        var guard = 0
        while (now >= phaseEnd && guard++ < 10_000) {
            val boundary = phaseEnd
            activeBeforePhase += phaseDuration
            val durations = pattern().phaseMillis()
            val next = firstNonEmpty(durations, phase.next)
            if (next.ordinal <= phase.ordinal) {
                // Wrapped around: cycle number [cycle] was completed.
                val l = limit()
                val timeUp = l.millis > 0 && activeBeforePhase >= l.millis
                val cyclesDone = l.cycles > 0 && cycle >= l.cycles
                if (timeUp || cyclesDone) {
                    isFinished = true
                    phaseStart = boundary
                    phaseDuration = 0
                    events += RunnerEvent.Finished
                    return events
                }
                cycle++
            }
            enter(next, boundary, durations[next.ordinal])
            events += RunnerEvent.PhaseStarted(phase, phaseDuration, cycle)
        }
        return events
    }

    private fun enter(p: Phase, start: Long, duration: Long) {
        phase = p
        phaseStart = start
        phaseDuration = duration.coerceAtLeast(1L)
    }

    private fun firstNonEmpty(durations: LongArray, from: Phase): Phase {
        var p = from
        repeat(4) {
            if (durations[p.ordinal] > 0) return p
            p = p.next
        }
        return Phase.INHALE
    }
}

/** How full the lungs are, from 0 to 1, given a phase and the fraction of it that has passed. */
fun breathLevel(phase: Phase, progress: Float, natural: Boolean = true): Float {
    val t = progress.coerceIn(0f, 1f)
    val eased = if (natural) ((1 - cos(PI * t)) / 2).toFloat() else t
    return when (phase) {
        Phase.INHALE -> eased
        Phase.HOLD_IN -> 1f
        Phase.EXHALE -> 1f - eased
        Phase.HOLD_OUT -> 0f
    }
}
