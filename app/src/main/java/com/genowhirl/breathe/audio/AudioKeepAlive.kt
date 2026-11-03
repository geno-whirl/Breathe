package com.genowhirl.breathe.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.random.Random

/**
 * Plays an inaudible looping stream while a session runs.
 *
 * With the screen off, Android and phone makers' battery managers can suspend the CPU or freeze
 * an app even though it holds a wake lock and runs a foreground service; the cue timer then
 * stops until the screen is turned on again. Apps that are actively playing audio are kept
 * running: the audio system holds the device awake while it mixes. The stream is a loop of
 * dither at the quietest level 16-bit audio can express (about -90 dB), so nothing is heard,
 * and no system silence detection treats the stream as stopped.
 */
class AudioKeepAlive {
    private var track: AudioTrack? = null

    @Synchronized
    fun start() {
        if (track != null) return
        val frames = ToneSynth.SAMPLE_RATE / 2
        val rnd = Random(3)
        val pcm = ShortArray(frames * 2) { if (rnd.nextBoolean()) 1 else -1 }
        track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(ToneSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
                .apply {
                    write(pcm, 0, pcm.size)
                    setLoopPoints(0, frames, -1)
                    play()
                }
        }.getOrNull()
    }

    @Synchronized
    fun stop() {
        track?.let { t ->
            runCatching {
                t.stop()
                t.release()
            }
        }
        track = null
    }
}
