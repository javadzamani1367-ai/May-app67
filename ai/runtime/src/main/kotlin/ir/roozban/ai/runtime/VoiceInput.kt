package ir.roozban.ai.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.ai.core.DeviceTier
import ir.roozban.ai.core.LlmException
import ir.roozban.ai.core.audio.Pcm
import ir.roozban.ai.core.audio.SpeechRecognizer
import ir.roozban.ai.core.audio.Transcript
import ir.roozban.ai.core.audio.Vad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface Recording {
    /** 0..1 loudness for the animation. */
    data class Level(val value: Float, val speaking: Boolean) : Recording

    /** Finished: [samples] holds the speech (silence trimmed); empty when nothing was said. */
    data class Done(val samples: ShortArray, val reason: Vad.EndReason?) : Recording
}

/**
 * Voice input: records from the microphone until the user stops talking (or taps stop), then
 * transcribes offline with the active speech model in the `:ai` process.
 */
@Singleton
class VoiceInput @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: RemoteLlmEngine,
    private val models: ModelManager,
) : SpeechRecognizer {

    val hasModel: Boolean get() = models.state.value.activeSpeech != null

    /**
     * Records 16 kHz mono until [Vad] decides the user is done or the collector is cancelled
     * (tap to stop: cancel, then use [stopAndTake]). Needs RECORD_AUDIO.
     */
    @SuppressLint("MissingPermission")
    fun record(stop: () -> Boolean = { false }): Flow<Recording> = flow {
        val rate = Pcm.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw LlmException("میکروفون در دسترس نیست.")
        }
        val vad = Vad()
        val frame = ShortArray(rate * 30 / 1000)
        var all = ShortArray(rate * 10)
        var size = 0
        try {
            recorder.startRecording()
            while (currentCoroutineContext().isActive && vad.state != Vad.State.DONE && !stop()) {
                val n = recorder.read(frame, 0, frame.size)
                if (n <= 0) continue
                if (size + n > all.size) all = all.copyOf(all.size * 2)
                System.arraycopy(frame, 0, all, size, n)
                size += n
                vad.feed(frame, n)
                emit(Recording.Level(vad.level, vad.state == Vad.State.SPEAKING))
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        val spoken = vad.heardSpeech || stop()
        emit(Recording.Done(if (spoken) Pcm.trim(all.copyOf(size)) else ShortArray(0), vad.endReason))
    }.flowOn(Dispatchers.IO)

    private companion object {
        /** Set from the Persian speech eval (tools/speech_eval). */
        const val BEAM = 1
    }

    override suspend fun transcribe(samples: ShortArray, prompt: String?): String = withContext(Dispatchers.IO) {
        val model = models.state.value.activeSpeech ?: throw LlmException("مدل تشخیص گفتار نصب نیست.")
        if (samples.isEmpty()) return@withContext ""
        val file = File(context.cacheDir, "voice-${System.nanoTime()}.pcm")
        try {
            Pcm.writeRaw(file, samples)
            val threads = DeviceTier.threadsFor(Runtime.getRuntime().availableProcessors())
            val text = engine.withService { s -> s.transcribe(model.file.path, file.path, prompt.orEmpty(), threads, BEAM) ?: throw LlmException(s.lastError()) }
            Transcript.clean(text)
        } finally {
            file.delete()
        }
    }
}
