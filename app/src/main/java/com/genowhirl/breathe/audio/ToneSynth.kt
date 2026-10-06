package com.genowhirl.breathe.audio

import com.genowhirl.breathe.model.SoundStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

enum class Cue { INHALE, HOLD_IN, EXHALE, HOLD_OUT, TICK, FINISH }

/** Generates the cue sounds as 16-bit mono PCM, so the app ships without audio assets. */
object ToneSynth {
    const val SAMPLE_RATE = 44_100

    private class Partial(val ratio: Double, val amp: Double, val decay: Double, val detune: Double = 0.0)

    private fun pitch(cue: Cue): Double = when (cue) {
        Cue.INHALE -> 523.25 // C5: rising feeling for the in-breath
        Cue.HOLD_IN -> 392.00 // G4
        Cue.EXHALE -> 329.63 // E4: lower, settling
        Cue.HOLD_OUT -> 293.66 // D4
        Cue.TICK, Cue.FINISH -> 523.25
    }

    private fun gain(cue: Cue): Double = when (cue) {
        Cue.HOLD_IN, Cue.HOLD_OUT -> 0.6
        Cue.TICK -> 0.3
        else -> 1.0
    }

    fun render(style: SoundStyle, cue: Cue): ShortArray {
        if (cue == Cue.TICK) return normalize(tick(), gain(cue))
        val base = pitch(cue)
        val signal = if (cue == Cue.FINISH) {
            // A gentle arpeggio: C5, E5, G5.
            val notes = listOf(523.25, 659.25, 783.99)
            val single = notes.map { voice(style, it) }
            val offset = (0.28 * SAMPLE_RATE).toInt()
            val out = DoubleArray(single.maxOf { it.size } + offset * (notes.size - 1))
            single.forEachIndexed { n, s -> for (i in s.indices) out[i + n * offset] += s[i] * 0.8 }
            out
        } else {
            voice(style, base)
        }
        return normalize(signal, gain(cue))
    }

    private fun voice(style: SoundStyle, f: Double): DoubleArray = when (style) {
        SoundStyle.BOWL -> additive(
            f, seconds = 3.2, attack = 0.012,
            listOf(
                Partial(1.0, 1.0, 2.6, detune = 0.7),
                Partial(2.76, 0.45, 1.5, detune = 1.1),
                Partial(5.40, 0.20, 0.8),
                Partial(8.93, 0.08, 0.45),
            ),
        )
        SoundStyle.CHIME -> additive(
            f * 2, seconds = 2.0, attack = 0.003,
            listOf(
                Partial(1.0, 1.0, 1.2),
                Partial(2.0, 0.35, 0.7),
                Partial(3.0, 0.18, 0.4),
                Partial(4.16, 0.10, 0.25),
            ),
        )
        SoundStyle.SOFT -> additive(
            f, seconds = 1.2, attack = 0.06,
            listOf(Partial(1.0, 1.0, 0.45), Partial(2.0, 0.12, 0.3)),
        )
        SoundStyle.WOOD -> wood(f)
        // Voice cues are spoken by TextToSpeech; the finish chord still uses a bowl.
        SoundStyle.VOICE -> voice(SoundStyle.BOWL, f)
    }

    private fun additive(f: Double, seconds: Double, attack: Double, partials: List<Partial>): DoubleArray {
        val n = (seconds * SAMPLE_RATE).toInt()
        val out = DoubleArray(n)
        val attackSamples = (attack * SAMPLE_RATE).toInt().coerceAtLeast(1)
        val releaseSamples = (0.05 * SAMPLE_RATE).toInt()
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            var v = 0.0
            for (p in partials) {
                val env = p.amp * exp(-t / p.decay)
                v += env * sin(2 * PI * f * p.ratio * t)
                // A slightly detuned twin gives a slow, singing beat.
                if (p.detune != 0.0) v += env * 0.5 * sin(2 * PI * (f * p.ratio + p.detune) * t)
            }
            val a = if (i < attackSamples) 0.5 - 0.5 * cos(PI * i / attackSamples) else 1.0
            val r = if (i > n - releaseSamples) (n - i).toDouble() / releaseSamples else 1.0
            out[i] = v * a * r
        }
        return out
    }

    private fun wood(f: Double): DoubleArray {
        val n = (0.22 * SAMPLE_RATE).toInt()
        val out = DoubleArray(n)
        val rnd = Random(7)
        val body = f * 1.5
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val tone = exp(-t / 0.045) * (sin(2 * PI * body * t) + 0.4 * sin(2 * PI * body * 2.47 * t))
            val click = exp(-t / 0.004) * (rnd.nextDouble() * 2 - 1) * 0.35
            out[i] = tone + click
        }
        return out
    }

    private fun tick(): DoubleArray {
        val n = (0.05 * SAMPLE_RATE).toInt()
        return DoubleArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            exp(-t / 0.008) * sin(2 * PI * 2200 * t)
        }
    }

    private fun normalize(signal: DoubleArray, gain: Double): ShortArray {
        val peak = signal.maxOf { abs(it) }.takeIf { it > 0 } ?: 1.0
        val scale = 0.85 * gain * Short.MAX_VALUE / peak
        return ShortArray(signal.size) { (signal[it] * scale).toInt().toShort() }
    }
}
