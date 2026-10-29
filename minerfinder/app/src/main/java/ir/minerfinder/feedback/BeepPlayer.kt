package ir.minerfinder.feedback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * A short click at [FREQ_HZ], above the 100–5000 Hz band the acoustic
 * detector analyses, with a smooth envelope so it does not splatter into that
 * band. The phone's own microphone hears every beep; a beep inside the band,
 * repeating at a steady rate, could otherwise look like a miner and feed itself.
 */
class BeepPlayer {
    private val track: AudioTrack?

    init {
        val rate = 48_000
        val n = rate * DURATION_MS / 1000
        val pcm = ShortArray(n) { i ->
            val envelope = sin(PI * i / n) // half-sine fade in and out
            (envelope * 0.6 * Short.MAX_VALUE * sin(2 * PI * FREQ_HZ * i / rate)).toInt().toShort()
        }
        track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(n * 2)
                .build()
                .also { it.write(pcm, 0, n) }
        }.getOrNull()
    }

    fun beep() {
        val t = track ?: return
        runCatching {
            t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    fun release() {
        runCatching { track?.release() }
    }

    companion object {
        const val FREQ_HZ = 6_000.0
        const val DURATION_MS = 35
    }
}
