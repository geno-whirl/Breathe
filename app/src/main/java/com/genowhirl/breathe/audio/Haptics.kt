package com.genowhirl.breathe.audio

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import com.genowhirl.breathe.model.Phase

class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    val canVaryStrength: Boolean get() = vibrator?.hasAmplitudeControl() == true || hasPrimitives

    private val hasAmplitude: Boolean get() = vibrator?.hasAmplitudeControl() == true

    /**
     * Factory-tuned effects (a slow swell, a quick fade, soft ticks) that feel much better than
     * hand-made timings. Available on many Android 12+ phones.
     */
    private val hasPrimitives: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibrator?.areAllPrimitivesSupported(
            VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
        ) == true
    }

    /** A short, recognisable pattern for each stage. */
    fun cue(cue: Cue, strength: Float) {
        val effect = if (hasPrimitives && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            primitiveCue(cue, strength)
        } else {
            waveform(HapticPatterns.cue(cue, strength, hasAmplitude))
        }
        vibrate(effect)
    }

    /**
     * Soft taps that follow the breath: stronger and closer together through the inhale, fading
     * and spreading out through the exhale. Holds get a single soft tap.
     */
    fun wave(phase: Phase, durationMs: Long, strength: Float) {
        val train = HapticPatterns.breathTrain(phase, durationMs, strength, hasAmplitude)
        if (train == null) {
            cue(phase.toCue(), strength * 0.7f)
        } else {
            vibrate(waveform(train))
        }
    }

    fun cancel() {
        vibrator?.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun primitiveCue(cue: Cue, strength: Float): VibrationEffect {
        val s = 0.35f + 0.65f * strength.coerceIn(0f, 1f)
        val c = VibrationEffect.startComposition()
        when (cue) {
            Cue.INHALE -> {
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, s)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, s * 0.7f)
            }
            Cue.EXHALE -> {
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, s, 60)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, s * 0.6f, 40)
            }
            Cue.HOLD_IN, Cue.HOLD_OUT -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s * 0.6f)
            Cue.TICK -> c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, s * 0.5f)
            Cue.FINISH -> {
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, s, 160)
                c.addPrimitive(VibrationEffect.Composition.PRIMITIVE_SLOW_RISE, s, 160)
            }
        }
        return c.compose()
    }

    private fun waveform(w: Waveform): VibrationEffect =
        if (hasAmplitude) {
            VibrationEffect.createWaveform(w.timings, w.amplitudes, -1)
        } else {
            // Without amplitude control, only on/off timings can be expressed.
            VibrationEffect.createWaveform(w.timings, -1)
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
