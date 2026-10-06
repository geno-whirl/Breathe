package com.genowhirl.breathe.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.genowhirl.breathe.model.Phase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Status { IDLE, RUNNING, PAUSED, FINISHED }

/**
 * Snapshot of the running session. Times use SystemClock.elapsedRealtime(), so observers can
 * compute smooth progress themselves between snapshots.
 */
data class SessionState(
    val status: Status = Status.IDLE,
    val phase: Phase = Phase.INHALE,
    val phaseStart: Long = 0L,
    val phaseDuration: Long = 1L,
    /** Progress within the phase, frozen while paused. */
    val pausedPhaseElapsed: Long = 0L,
    val cycle: Int = 1,
    /** Breathing time (excluding pauses) at the moment of [snapshotAt]. */
    val activeMillis: Long = 0L,
    val snapshotAt: Long = 0L,
) {
    val isActive: Boolean get() = status == Status.RUNNING || status == Status.PAUSED

    fun phaseElapsed(now: Long): Long = when (status) {
        Status.RUNNING -> (now - phaseStart).coerceIn(0L, phaseDuration)
        Status.PAUSED -> pausedPhaseElapsed
        else -> 0L
    }

    fun phaseProgress(now: Long): Float = phaseElapsed(now).toFloat() / phaseDuration.coerceAtLeast(1L)

    fun phaseRemaining(now: Long): Long = phaseDuration - phaseElapsed(now)

    fun activeMillis(now: Long): Long =
        if (status == Status.RUNNING) activeMillis + (now - snapshotAt).coerceAtLeast(0L) else activeMillis
}

object Session {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    internal fun publish(state: SessionState) {
        _state.value = state
    }

    fun toggle(context: Context) = send(context, BreathingService.ACTION_TOGGLE)
    fun start(context: Context) = send(context, BreathingService.ACTION_START)
    fun stop(context: Context) = send(context, BreathingService.ACTION_STOP)

    fun dismissFinished() {
        if (_state.value.status == Status.FINISHED) _state.value = SessionState()
    }

    private fun send(context: Context, action: String) {
        val intent = Intent(context, BreathingService::class.java).setAction(action)
        ContextCompat.startForegroundService(context, intent)
    }
}
