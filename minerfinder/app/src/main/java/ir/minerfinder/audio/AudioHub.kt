package ir.minerfinder.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.shareIn
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A chunk of mono samples in −1..1 at [sampleRate], or, when recording could
 * not start or broke off, an empty chunk carrying the [error].
 */
class AudioChunk(val samples: FloatArray, val sampleRate: Int, val error: Throwable? = null)

class AudioUnavailableException(message: String) : Exception(message)

/**
 * One recording per input device, shared by every source listening to it
 * (the acoustic detector and the coil both read audio). Recording starts with
 * the first listener and stops half a second after the last one leaves.
 */
class AudioHub(
    private val context: Context,
    private val inputs: AudioInputs,
    private val scope: CoroutineScope,
) {
    private val shared = HashMap<Int, SharedFlow<AudioChunk>>()

    @Synchronized
    fun chunks(deviceId: Int): SharedFlow<AudioChunk> = shared.getOrPut(deviceId) {
        // A failure must reach the listeners as data: an exception escaping
        // shareIn would end up in the app scope and crash the process.
        record(deviceId)
            .catch { emit(AudioChunk(FloatArray(0), SAMPLE_RATE, it)) }
            .shareIn(scope, SharingStarted.WhileSubscribed(500), replay = 0)
    }

    @SuppressLint("MissingPermission")
    private fun record(deviceId: Int): Flow<AudioChunk> = callbackFlow {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw AudioUnavailableException("RECORD_AUDIO not granted")
        }
        val record = open(SAMPLE_RATE, AudioFormat.ENCODING_PCM_FLOAT)
            ?: open(SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT)
            ?: throw AudioUnavailableException("AudioRecord could not be opened")
        inputs.device(deviceId)?.let { record.setPreferredDevice(it) }
        record.startRecording()
        val rate = record.sampleRate
        val isFloat = record.audioFormat == AudioFormat.ENCODING_PCM_FLOAT
        val chunkSize = rate / 10
        val running = AtomicBoolean(true)
        val reader = thread(name = "audio-$deviceId", priority = Thread.MAX_PRIORITY) {
            val floats = FloatArray(chunkSize)
            val shorts = ShortArray(chunkSize)
            while (running.get()) {
                val n = if (isFloat) {
                    record.read(floats, 0, chunkSize, AudioRecord.READ_BLOCKING)
                } else {
                    record.read(shorts, 0, chunkSize).also { count ->
                        for (i in 0 until count) floats[i] = shorts[i] / 32768f
                    }
                }
                if (n < 0) {
                    trySend(AudioChunk(FloatArray(0), rate, AudioUnavailableException("read error $n")))
                    break
                }
                if (n > 0) trySend(AudioChunk(floats.copyOf(n), rate))
            }
        }
        awaitClose {
            running.set(false)
            runCatching { record.stop() }
            reader.join(500)
            record.release()
        }
    }

    @SuppressLint("MissingPermission")
    private fun open(rate: Int, encoding: Int): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, encoding)
        if (min <= 0) return null
        val bytesPerSample = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
        val record = runCatching {
            AudioRecord.Builder()
                .setAudioSource(audioSource())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(min, rate / 2 * bytesPerSample))
                .build()
        }.getOrNull() ?: return null
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        return record
    }

    /**
     * UNPROCESSED where the phone supports it: no noise suppression, which
     * would treat a steady fan tone as exactly the noise to remove. Otherwise
     * VOICE_RECOGNITION, which at least skips most processing.
     */
    private fun audioSource(): Int {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        return if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    companion object {
        const val SAMPLE_RATE = 48_000
    }
}
