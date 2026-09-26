package ir.roozban.ai.runtime

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import ir.roozban.ai.core.LlmException
import ir.roozban.ai.core.audio.Pcm
import ir.roozban.ai.core.audio.Segmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DictationEvent {
    /** 0..1 loudness, for the animation. */
    data class Level(val value: Float, val speaking: Boolean) : DictationEvent

    /** Pieces recorded but not yet turned into text. */
    data class Pending(val count: Int) : DictationEvent

    /** The next piece of text, in order. */
    data class Text(val text: String) : DictationEvent
}

/**
 * Long dictation without a length limit: the microphone keeps recording while finished pieces
 * ([Segmenter]) are transcribed one after another with the same Persian speech model as voice
 * input. Recording stops when [stop] turns true; the flow then finishes the remaining pieces
 * and completes. Needs RECORD_AUDIO.
 */
@Singleton
class Dictation @Inject constructor(private val voice: VoiceInput) {

    val hasModel: Boolean get() = voice.hasModel

    /** [context] returns the text so far; its end is given to the model so sentences carry on naturally. */
    @SuppressLint("MissingPermission")
    fun run(stop: () -> Boolean, context: () -> String = { "" }): Flow<DictationEvent> = channelFlow {
        val pieces = Channel<ShortArray>(Channel.UNLIMITED)
        val pending = AtomicInteger(0)
        val transcriber = launch {
            for (piece in pieces) {
                val prompt = context().takeLast(PROMPT_CHARS)
                val text = runCatching { voice.transcribe(piece, prompt.ifBlank { null }) }.getOrElse { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    ""
                }
                send(DictationEvent.Pending(pending.decrementAndGet()))
                if (text.isNotBlank()) send(DictationEvent.Text(text))
            }
        }
        withContext(Dispatchers.IO) {
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
            val segmenter = Segmenter()
            val frame = ShortArray(rate * 30 / 1000)
            try {
                recorder.startRecording()
                while (isActive && !stop()) {
                    val n = recorder.read(frame, 0, frame.size)
                    if (n <= 0) continue
                    segmenter.feed(frame, n)?.let {
                        send(DictationEvent.Pending(pending.incrementAndGet()))
                        pieces.send(it)
                    }
                    send(DictationEvent.Level(segmenter.level, segmenter.speaking))
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
            segmenter.flush()?.let {
                send(DictationEvent.Pending(pending.incrementAndGet()))
                pieces.send(it)
            }
            send(DictationEvent.Level(0f, false))
        }
        pieces.close()
        transcriber.join()
    }

    private companion object {
        const val PROMPT_CHARS = 120
    }
}
