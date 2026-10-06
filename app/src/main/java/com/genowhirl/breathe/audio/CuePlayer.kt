package com.genowhirl.breathe.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import com.genowhirl.breathe.R
import com.genowhirl.breathe.model.SoundStyle
import java.util.concurrent.Executors

/**
 * Plays cue sounds. Each (style, cue) pair gets a static AudioTrack that is rendered once and
 * replayed, which keeps latency low and works while the screen is off. All work happens on one
 * background thread, so callers never wait for sounds to be rendered.
 */
class CuePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "cue-player") }
    private val tracks = HashMap<Pair<SoundStyle, Cue>, AudioTrack>()
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    private var lastPlayAt = 0L

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Renders the sounds for [style] ahead of time so the first cue is not late. */
    fun prepare(style: SoundStyle) = submit {
        if (style == SoundStyle.VOICE) {
            ensureTts()
            track(style, Cue.FINISH)
        } else {
            // The phase cues first: they are needed within the first seconds.
            listOf(Cue.INHALE, Cue.HOLD_IN, Cue.EXHALE, Cue.HOLD_OUT, Cue.TICK, Cue.FINISH).forEach { track(style, it) }
        }
    }

    fun play(style: SoundStyle, cue: Cue, volume: Float) = submit {
        lastPlayAt = SystemClock.elapsedRealtime()
        if (style == SoundStyle.VOICE && cue != Cue.FINISH && cue != Cue.TICK) {
            speak(cue, volume)
        } else {
            track(style, cue)?.let { t ->
                runCatching {
                    t.setVolume(volume.coerceIn(0f, 1f))
                    if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
                    t.reloadStaticData()
                    t.play()
                }
            }
        }
    }

    /** Frees the audio resources, letting a sound that just started ring out first. */
    fun release() {
        submit {
            val ringOut = lastPlayAt + RING_OUT_MS - SystemClock.elapsedRealtime()
            if (ringOut > 0) Thread.sleep(ringOut)
            tracks.values.forEach { runCatching { it.release() } }
            tracks.clear()
            tts?.shutdown()
            tts = null
            ttsReady = false
        }
        worker.shutdown()
    }

    private fun submit(block: () -> Unit) {
        runCatching { worker.execute(block) }
    }

    private fun track(style: SoundStyle, cue: Cue): AudioTrack? {
        tracks[style to cue]?.let { return it }
        val pcm = ToneSynth.render(style, cue)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(ToneSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
                .also { it.write(pcm, 0, pcm.size) }
        }.getOrNull() ?: return null
        tracks[style to cue] = track
        return track
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.setAudioAttributes(attributes)
                tts?.setSpeechRate(0.85f)
            }
        }
    }

    private fun speak(cue: Cue, volume: Float) {
        ensureTts()
        if (!ttsReady) return
        val text = appContext.getString(
            when (cue) {
                Cue.INHALE -> R.string.say_inhale
                Cue.EXHALE -> R.string.say_exhale
                else -> R.string.say_hold
            },
        )
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f)) }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, cue.name)
    }

    private companion object {
        const val RING_OUT_MS = 3_500L
    }
}
