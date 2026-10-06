package com.genowhirl.breathe.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.genowhirl.breathe.model.Phase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    val canVaryStrength: Boolean get() = vibrator?.hasAmplitudeControl() == true

    /** A short, recognisable pattern for each stage. */
    fun cue(cue: Cue, strength: Float) {
        val amp = amplitude(strength)
        val effect = when (cue) {
            Cue.INHALE -> waveform(longArrayOf(0, 140), intArrayOf(0, amp))
            Cue.EXHALE -> waveform(longArrayOf(0, 90, 110, 90), intArrayOf(0, amp, 0, amp))
            Cue.HOLD_IN, Cue.HOLD_OUT -> waveform(longArrayOf(0, 45), intArrayOf(0, (amp * 0.7f).roundToInt().coerceAtLeast(1)))
            Cue.TICK -> waveform(longArrayOf(0, 15), intArrayOf(0, (amp * 0.5f).roundToInt().coerceAtLeast(1)))
            Cue.FINISH -> waveform(
                longArrayOf(0, 220, 140, 220, 140, 400),
                intArrayOf(0, amp, 0, amp, 0, amp),
            )
        }
        vibrate(effect)
    }

    /**
     * Vibration that follows the breath: it swells over the inhale and fades over the exhale.
     * Falls back to a stage cue on devices that cannot vary vibration strength.
     */
    fun wave(phase: Phase, durationMs: Long, strength: Float) {
        if (!canVaryStrength) {
            cue(phase.toCue(), strength)
            return
        }
        when (phase) {
            Phase.INHALE, Phase.EXHALE -> {
                val steps = (durationMs / 60).toInt().coerceIn(2, 80)
                val stepMs = durationMs / steps
                val max = amplitude(strength)
                val min = (max * 0.08f).roundToInt().coerceAtLeast(1)
                val timings = LongArray(steps) { stepMs }
                val amps = IntArray(steps) { i ->
                    val t = i.toDouble() / (steps - 1)
                    val eased = (1 - cos(PI * t)) / 2
                    val level = if (phase == Phase.INHALE) eased else 1 - eased
                    (min + (max - min) * level).roundToInt().coerceIn(1, 255)
                }
                vibrate(VibrationEffect.createWaveform(timings, amps, -1))
            }
            else -> cue(phase.toCue(), strength * 0.6f)
        }
    }

    fun cancel() {
        vibrator?.cancel()
    }

    private fun amplitude(strength: Float): Int = (1 + strength.coerceIn(0f, 1f) * 254).roundToInt()

    private fun waveform(timings: LongArray, amps: IntArray): VibrationEffect =
        if (canVaryStrength) {
            VibrationEffect.createWaveform(timings, amps, -1)
        } else {
            // Without amplitude control, only on/off timings can be expressed.
            VibrationEffect.createWaveform(timings, -1)
        }

    private fun vibrate(effect: VibrationEffect) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_MEDIA))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        }
    }
}

fun Phase.toCue(): Cue = when (this) {
    Phase.INHALE -> Cue.INHALE
    Phase.HOLD_IN -> Cue.HOLD_IN
    Phase.EXHALE -> Cue.EXHALE
    Phase.HOLD_OUT -> Cue.HOLD_OUT
}
