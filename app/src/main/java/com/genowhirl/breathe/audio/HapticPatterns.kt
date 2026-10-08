package com.genowhirl.breathe.audio

import com.genowhirl.breathe.model.Phase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * A vibration waveform: [timings] alternate off/on durations in ms, starting with "off", and
 * [amplitudes] holds the matching strengths (0 = off, 1..255 = on).
 */
class Waveform(val timings: LongArray, val amplitudes: IntArray) {
    val durationMs: Long get() = timings.sum()
}

/**
 * The shapes of the app's vibrations, free of Android so they can be unit-tested.
 *
 * Phone motors feel best as short, crisp taps rather than long buzzes, and they barely move
 * below roughly a fifth of full power, so every strength is mapped into the range a motor can
 * actually render.
 */
object HapticPatterns {
    /** Below this most motors produce nothing you can feel. */
    const val MIN_AMPLITUDE = 48

    /** [level] is the shape of the pattern (0..1); [strength] is the user's setting (0..1). */
    fun amplitude(level: Float, strength: Float): Int {
        val top = MIN_AMPLITUDE + (255 - MIN_AMPLITUDE) * strength.coerceIn(0f, 1f)
        return (MIN_AMPLITUDE + (top - MIN_AMPLITUDE) * level.coerceIn(0f, 1f)).roundToInt().coerceIn(1, 255)
    }

    /**
     * Stage cues as short taps: rising "ta-TA" to breathe in, falling "TA-ta" to breathe out,
     * a single soft tap for holds. Motors without strength control (older, slower to spin up)
     * get slightly longer taps so they are still felt.
     */
    fun cue(cue: Cue, strength: Float, canVaryStrength: Boolean): Waveform {
        fun a(level: Float) = if (canVaryStrength) amplitude(level, strength) else 255
        val k = if (canVaryStrength) 1.0 else 1.6
        fun ms(v: Int) = (v * k).toLong()
        return when (cue) {
            Cue.INHALE -> Waveform(longArrayOf(0, ms(26), 120, ms(38)), intArrayOf(0, a(0.55f), 0, a(1f)))
            Cue.EXHALE -> Waveform(longArrayOf(0, ms(38), 120, ms(26)), intArrayOf(0, a(1f), 0, a(0.5f)))
            Cue.HOLD_IN, Cue.HOLD_OUT -> Waveform(longArrayOf(0, ms(24)), intArrayOf(0, a(0.65f)))
            Cue.TICK -> Waveform(longArrayOf(0, ms(12)), intArrayOf(0, a(0.35f)))
            Cue.FINISH -> Waveform(
                longArrayOf(0, ms(40), 170, ms(40), 170, ms(80)),
                intArrayOf(0, a(1f), 0, a(1f), 0, a(0.8f)),
            )
        }
    }

    /**
     * A train of soft taps that follows the breath: during the inhale they grow stronger and
     * closer together, during the exhale they fade and spread out, like a slowing heartbeat.
     * Returns null for holds, which get a single cue instead.
     */
    fun breathTrain(phase: Phase, durationMs: Long, strength: Float, canVaryStrength: Boolean): Waveform? {
        if (phase != Phase.INHALE && phase != Phase.EXHALE) return null
        val pulse = if (canVaryStrength) 22L else 38L
        val timings = ArrayList<Long>()
        val amps = ArrayList<Int>()
        var t = 0L
        var gap = 0L
        while (t + pulse <= durationMs - 60 || timings.isEmpty()) {
            val p = (t.toDouble() / durationMs).coerceIn(0.0, 1.0)
            val eased = (1 - cos(PI * p)) / 2
            val level = (if (phase == Phase.INHALE) eased else 1 - eased).toFloat()
            timings += gap
            amps += 0
            timings += pulse
            amps += if (canVaryStrength) amplitude(0.2f + 0.8f * level, strength) else 255
            val interval = (480 - 270 * level).toLong() // 480 ms apart when empty, 210 ms when full
            t += interval
            gap = interval - pulse
        }
        return Waveform(timings.toLongArray(), amps.toIntArray())
    }
}
