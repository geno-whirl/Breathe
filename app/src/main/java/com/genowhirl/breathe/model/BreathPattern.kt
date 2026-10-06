package com.genowhirl.breathe.model

import kotlin.math.floor
import kotlin.math.roundToLong

enum class Phase {
    INHALE, HOLD_IN, EXHALE, HOLD_OUT;

    val next: Phase get() = entries[(ordinal + 1) % entries.size]
}

/** How the four integers of a [BreathPattern] are turned into time. */
enum class TimingMode {
    /** Each integer is the stage's length in seconds. */
    SECONDS,

    /** The integers are relative weights; the whole cycle lasts [BreathPattern.cycleSeconds]. */
    CYCLE_LENGTH,

    /** The integers are relative weights; the cycle length is 60 / [BreathPattern.cyclesPerMinute]. */
    PER_MINUTE,
}

data class BreathPattern(
    val inhale: Int = 4,
    val holdIn: Int = 4,
    val exhale: Int = 4,
    val holdOut: Int = 4,
    val mode: TimingMode = TimingMode.SECONDS,
    val cycleSeconds: Double = 16.0,
    val cyclesPerMinute: Double = 6.0,
) {
    fun units(phase: Phase): Int = when (phase) {
        Phase.INHALE -> inhale
        Phase.HOLD_IN -> holdIn
        Phase.EXHALE -> exhale
        Phase.HOLD_OUT -> holdOut
    }

    fun withUnits(phase: Phase, value: Int): BreathPattern {
        val v = value.coerceIn(0, MAX_UNITS)
        val updated = when (phase) {
            Phase.INHALE -> copy(inhale = v)
            Phase.HOLD_IN -> copy(holdIn = v)
            Phase.EXHALE -> copy(exhale = v)
            Phase.HOLD_OUT -> copy(holdOut = v)
        }
        // Never allow a pattern in which nothing happens.
        return if (updated.unitSum == 0) this else updated
    }

    val unitSum: Int get() = inhale + holdIn + exhale + holdOut

    val cycleMillis: Long
        get() = when (mode) {
            TimingMode.SECONDS -> unitSum * 1000L
            TimingMode.CYCLE_LENGTH -> (cycleSeconds.coerceIn(MIN_CYCLE_SECONDS, MAX_CYCLE_SECONDS) * 1000).roundToLong()
            TimingMode.PER_MINUTE -> (60_000.0 / cyclesPerMinute.coerceIn(MIN_PER_MINUTE, MAX_PER_MINUTE)).roundToLong()
        }

    val breathsPerMinute: Double get() = if (cycleMillis == 0L) 0.0 else 60_000.0 / cycleMillis

    /**
     * Duration of each phase in milliseconds, indexed by [Phase.ordinal]. The cycle is split in
     * proportion to the units and rounded so the parts add up to exactly [cycleMillis].
     */
    fun phaseMillis(): LongArray {
        val sum = unitSum
        if (sum == 0) return LongArray(4)
        val total = cycleMillis
        val exact = Phase.entries.map { total.toDouble() * units(it) / sum }
        val result = LongArray(4) { floor(exact[it]).toLong() }
        var remainder = total - result.sum()
        // Largest-remainder rounding; only phases that have units may receive the leftover ms.
        val order = Phase.entries.indices
            .filter { units(Phase.entries[it]) > 0 }
            .sortedByDescending { exact[it] - result[it] }
        var i = 0
        while (remainder > 0 && order.isNotEmpty()) {
            result[order[i % order.size]]++
            remainder--
            i++
        }
        return result
    }

    fun phaseMillis(phase: Phase): Long = phaseMillis()[phase.ordinal]

    /** Same timing regardless of the values stored for the modes that are not in use. */
    fun sameRhythmAs(other: BreathPattern): Boolean =
        inhale == other.inhale && holdIn == other.holdIn && exhale == other.exhale &&
            holdOut == other.holdOut && mode == other.mode && cycleMillis == other.cycleMillis

    companion object {
        const val MAX_UNITS = 99
        const val MIN_CYCLE_SECONDS = 1.0
        const val MAX_CYCLE_SECONDS = 600.0
        const val MIN_PER_MINUTE = 0.5
        const val MAX_PER_MINUTE = 60.0
    }
}

data class Preset(
    val id: String,
    val name: String,
    val pattern: BreathPattern,
    val builtIn: Boolean = false,
)

object BuiltInPresets {
    val all = listOf(
        Preset("box", "Box", BreathPattern(4, 4, 4, 4), builtIn = true),
        Preset("478", "4-7-8", BreathPattern(4, 7, 8, 0), builtIn = true),
        Preset(
            "coherent", "Coherent",
            BreathPattern(1, 0, 1, 0, mode = TimingMode.PER_MINUTE, cyclesPerMinute = 5.5),
            builtIn = true,
        ),
        Preset("calm", "Calm", BreathPattern(4, 0, 6, 0), builtIn = true),
        Preset("triangle", "Triangle", BreathPattern(4, 4, 4, 0), builtIn = true),
        Preset(
            "deep", "Deep box",
            BreathPattern(1, 1, 1, 1, mode = TimingMode.CYCLE_LENGTH, cycleSeconds = 24.0),
            builtIn = true,
        ),
    )
}
