package com.genowhirl.breathe.audio

import com.genowhirl.breathe.model.SoundStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class Cue { INHALE, HOLD_IN, EXHALE, HOLD_OUT, TICK, FINISH }

/**
 * Generates the cue sounds as 48 kHz stereo PCM, so the app ships without audio assets.
 *
 * Each instrument is modal synthesis: a handful of decaying partials tuned like the real object
 * (singing bowl, metal chime bar, marimba bar), each paired with a slightly detuned twin for a
 * slow shimmer, struck by a soft filtered-noise mallet. A small stereo reverb gives the dry
 * tones a room to ring in.
 */
object ToneSynth {
    /** The native rate of nearly every phone's audio path, so nothing gets resampled. */
    const val SAMPLE_RATE = 48_000
    const val CHANNELS = 2

    private class Mode(
        val ratio: Double,
        val amp: Double,
        /** Seconds for the partial to fall by 60 dB. */
        val t60: Double,
        /** Frequency offset in Hz of the twin partial; 0 for none. */
        val beat: Double = 0.0,
    )

    private class Instrument(
        val modes: List<Mode>,
        val seconds: Double,
        val attack: Double,
        val mallet: Double,
        val malletBrightness: Double,
        val reverb: Double,
        val pitchScale: Double = 1.0,
    )

    // A major pentatonic, so any two cues sound good together.
    private fun pitch(cue: Cue): Double = when (cue) {
        Cue.INHALE -> 659.26 // E5: the in-breath rises
        Cue.HOLD_IN -> 493.88 // B4
        Cue.EXHALE -> 440.00 // A4: the out-breath settles
        Cue.HOLD_OUT -> 369.99 // F#4
        Cue.TICK, Cue.FINISH -> 440.00
    }

    private fun gain(cue: Cue): Double = when (cue) {
        Cue.HOLD_IN, Cue.HOLD_OUT -> 0.62
        Cue.TICK -> 0.22
        else -> 1.0
    }

    private fun instrument(style: SoundStyle): Instrument = when (style) {
        SoundStyle.BOWL -> Instrument(
            modes = listOf(
                Mode(1.00, 1.00, 7.0, beat = 1.3),
                Mode(2.71, 0.42, 4.6, beat = 2.2),
                Mode(5.15, 0.16, 2.4, beat = 3.1),
                Mode(8.43, 0.05, 1.3),
            ),
            seconds = 4.0, attack = 0.006, mallet = 0.05, malletBrightness = 0.08, reverb = 0.24,
        )
        SoundStyle.CHIME -> Instrument(
            modes = listOf(
                Mode(1.000, 1.00, 3.2, beat = 0.9),
                Mode(2.756, 0.45, 1.7, beat = 1.7),
                Mode(5.404, 0.18, 0.8),
                Mode(8.933, 0.06, 0.4),
            ),
            seconds = 3.2, attack = 0.002, mallet = 0.04, malletBrightness = 0.25, reverb = 0.3,
            pitchScale = 2.0,
        )
        SoundStyle.SOFT -> Instrument(
            modes = listOf(
                Mode(1.0, 1.00, 1.6, beat = 0.6),
                Mode(2.0, 0.07, 0.9),
            ),
            seconds = 2.2, attack = 0.07, mallet = 0.0, malletBrightness = 0.0, reverb = 0.36,
        )
        SoundStyle.WOOD -> Instrument(
            modes = listOf(
                Mode(1.00, 1.00, 1.0),
                Mode(3.93, 0.32, 0.38),
                Mode(9.20, 0.07, 0.12),
            ),
            seconds = 1.6, attack = 0.002, mallet = 0.08, malletBrightness = 0.12, reverb = 0.18,
        )
        // Voice cues are spoken by TextToSpeech; the finish chord uses the bowl.
        SoundStyle.VOICE -> instrument(SoundStyle.BOWL)
    }

    /** Interleaved stereo 16-bit samples. */
    fun render(style: SoundStyle, cue: Cue): ShortArray {
        val dry = when (cue) {
            Cue.TICK -> tick()
            Cue.FINISH -> arpeggio(instrument(style))
            else -> strike(instrument(style), pitch(cue))
        }
        val inst = if (cue == Cue.TICK) null else instrument(style)
        val stereo = reverb(dry, wet = inst?.reverb ?: 0.08, tailSeconds = if (inst == null) 0.2 else 1.0)
        return toPcm(stereo, gain(cue))
    }

    private fun strike(inst: Instrument, f0: Double): FloatArray {
        val f = f0 * inst.pitchScale
        val n = (inst.seconds * SAMPLE_RATE).toInt()
        val out = FloatArray(n)
        for (mode in inst.modes) {
            val freq = f * mode.ratio
            if (freq > SAMPLE_RATE * 0.45) continue
            addPartial(out, freq, mode.amp, mode.t60, inst.attack)
            if (mode.beat != 0.0) addPartial(out, freq + mode.beat, mode.amp * 0.6, mode.t60 * 0.9, inst.attack)
        }
        if (inst.mallet > 0) addMallet(out, inst.mallet, inst.malletBrightness)
        fadeOut(out, 0.25)
        return out
    }

    private fun arpeggio(inst: Instrument): FloatArray {
        // A major: A4, C#5, E5, rolled gently.
        val notes = listOf(440.00, 554.37, 659.26)
        val gap = (0.32 * SAMPLE_RATE).toInt()
        val parts = notes.map { strike(inst, it) }
        val out = FloatArray(parts.maxOf { it.size } + gap * (notes.size - 1))
        parts.forEachIndexed { i, p -> for (s in p.indices) out[s + i * gap] += p[s] * 0.75f }
        return out
    }

    private fun tick(): FloatArray {
        val n = (0.12 * SAMPLE_RATE).toInt()
        val out = FloatArray(n)
        addPartial(out, 1650.0, 1.0, 0.07, 0.0015)
        addPartial(out, 2900.0, 0.25, 0.03, 0.001)
        addMallet(out, 0.15, 0.2)
        return out
    }

    private fun addPartial(out: FloatArray, freq: Double, amp: Double, t60: Double, attack: Double) {
        val decay = ln(1000.0) / t60 / SAMPLE_RATE
        val step = 2 * PI * freq / SAMPLE_RATE
        val attackSamples = max(1.0, attack * SAMPLE_RATE)
        var env = amp
        val k = exp(-decay)
        for (i in out.indices) {
            val a = if (i < attackSamples) 0.5 - 0.5 * cos(PI * i / attackSamples) else 1.0
            out[i] += (env * a * sin(step * i)).toFloat()
            env *= k
            if (env < 1e-5) break
        }
    }

    /** A soft felt-mallet thump: noise, low-passed, a few milliseconds long. */
    private fun addMallet(out: FloatArray, amp: Double, brightness: Double) {
        val rnd = Random(11)
        val n = (0.012 * SAMPLE_RATE).toInt().coerceAtMost(out.size)
        var lp1 = 0.0
        var lp2 = 0.0
        for (i in 0 until n) {
            val env = sin(PI * i / n) * exp(-3.0 * i / n)
            lp1 += brightness * ((rnd.nextDouble() * 2 - 1) - lp1)
            lp2 += brightness * (lp1 - lp2)
            out[i] += (lp2 * env * amp * 6).toFloat()
        }
    }

    private fun fadeOut(out: FloatArray, seconds: Double) {
        val n = (seconds * SAMPLE_RATE).toInt().coerceAtMost(out.size)
        for (i in 0 until n) {
            val idx = out.size - n + i
            out[idx] *= (0.5 + 0.5 * cos(PI * i / n)).toFloat()
        }
    }

    /**
     * A small Schroeder/Freeverb-style room: parallel damped comb filters into all-pass
     * diffusers, with slightly different delays per side for width. Returns interleaved stereo.
     */
    private fun reverb(dry: FloatArray, wet: Double, tailSeconds: Double): FloatArray {
        val n = dry.size + (tailSeconds * SAMPLE_RATE).toInt()
        val left = reverbChannel(dry, n, 0)
        val right = reverbChannel(dry, n, 23)
        val out = FloatArray(n * 2)
        val d = (1 - wet).toFloat()
        val w = wet.toFloat()
        for (i in 0 until n) {
            val x = if (i < dry.size) dry[i] else 0f
            out[2 * i] = x * d + left[i] * w
            out[2 * i + 1] = x * d + right[i] * w
        }
        return out
    }

    private fun reverbChannel(dry: FloatArray, n: Int, spread: Int): FloatArray {
        val scale = SAMPLE_RATE / 44_100.0
        val combDelays = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491).map { ((it + spread) * scale).toInt() }
        val allpassDelays = intArrayOf(556, 441, 341).map { ((it + spread) * scale).toInt() }
        val feedback = 0.80f
        val damp = 0.3f
        val acc = FloatArray(n)
        for (delay in combDelays) {
            val buf = FloatArray(delay)
            var idx = 0
            var store = 0f
            for (i in 0 until n) {
                val x = if (i < dry.size) dry[i] * 0.12f else 0f
                val y = buf[idx]
                store = y * (1 - damp) + store * damp
                buf[idx] = x + store * feedback
                idx = if (idx + 1 == delay) 0 else idx + 1
                acc[i] += y
            }
        }
        for (delay in allpassDelays) {
            val buf = FloatArray(delay)
            var idx = 0
            for (i in 0 until n) {
                val b = buf[idx]
                val y = -acc[i] + b
                buf[idx] = acc[i] + b * 0.5f
                idx = if (idx + 1 == delay) 0 else idx + 1
                acc[i] = y
            }
        }
        return acc
    }

    private fun toPcm(signal: FloatArray, gain: Double): ShortArray {
        val peak = signal.maxOf { abs(it) }.takeIf { it > 0f } ?: 1f
        val scale = (0.8 * gain * Short.MAX_VALUE / peak).toFloat()
        return ShortArray(signal.size) { (signal[it] * scale).toInt().coerceIn(-32767, 32767).toShort() }
    }
}
