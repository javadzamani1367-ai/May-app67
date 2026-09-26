package ir.roozban.ai.core.audio

import kotlin.math.max

/**
 * Cuts continuous dictation into pieces the speech model can take (Whisper reads at most 30 s),
 * at natural pauses, while recording goes on:
 * - after speech, a pause of [pauseMs] ends a piece once it is at least [minMs] long;
 * - past [softMaxMs] a shorter pause ([shortPauseMs]) is enough;
 * - at [hardMaxMs] the piece is cut wherever it is.
 * Long silence between sentences is dropped (only a short lead-in is kept), so memory stays small
 * however long the user talks. Loudness uses the same learned noise floor as [Vad].
 */
class Segmenter(
    private val sampleRate: Int = Pcm.SAMPLE_RATE,
    private val pauseMs: Int = 700,
    private val shortPauseMs: Int = 250,
    private val minMs: Int = 2_500,
    private val softMaxMs: Int = 20_000,
    private val hardMaxMs: Int = 28_000,
    private val leadInMs: Int = 300,
    private val marginDb: Double = 12.0,
    private val minSpeechDb: Double = -48.0,
) {
    /** 0..1 loudness of the last frame. */
    var level: Float = 0f
        private set

    var speaking: Boolean = false
        private set

    private var buffer = ShortArray(sampleRate * 8)
    private var size = 0
    private var heard = false
    private var silenceMs = 0.0
    private var voicedMs = 0.0
    private var noiseDb = -60.0
    private var frames = 0

    /** Feeds one frame; returns a finished piece when this frame ends one. */
    fun feed(frame: ShortArray, count: Int = frame.size): ShortArray? {
        if (count <= 0) return null
        val db = Vad.dbfs(frame, count)
        val ms = count * 1000.0 / sampleRate
        frames++
        level = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
        noiseDb = when {
            // Capped: talking from the very first moment must not be taken for noise.
            frames <= 3 -> if (frames == 1) minOf(db, INITIAL_FLOOR_DB) else minOf(noiseDb, db)
            db < noiseDb -> noiseDb * 0.7 + db * 0.3
            else -> noiseDb * 0.995 + db * 0.005
        }
        val voiced = db > max(noiseDb + marginDb, minSpeechDb)
        append(frame, count)

        if (voiced) {
            voicedMs += ms
            silenceMs = 0.0
            if (voicedMs >= MIN_SPEECH_MS) heard = true
        } else {
            voicedMs = 0.0
            silenceMs += ms
        }
        speaking = voiced && heard

        val lengthMs = size * 1000.0 / sampleRate
        if (!heard) {
            // Nothing said yet: keep only a short lead-in.
            val keep = sampleRate * leadInMs / 1000
            if (size > keep * 4) {
                System.arraycopy(buffer, size - keep, buffer, 0, keep)
                size = keep
            }
            return null
        }
        val end = when {
            lengthMs >= hardMaxMs -> true
            lengthMs >= softMaxMs && silenceMs >= shortPauseMs -> true
            lengthMs >= minMs && silenceMs >= pauseMs -> true
            else -> false
        }
        return if (end) take() else null
    }

    /** What is left when the user stops; null when it holds no speech. */
    fun flush(): ShortArray? = if (heard && size > 0) take() else null.also { size = 0 }

    private fun take(): ShortArray {
        val piece = Pcm.trim(buffer.copyOf(size))
        size = 0
        heard = false
        voicedMs = 0.0
        silenceMs = 0.0
        return piece
    }

    private fun append(frame: ShortArray, count: Int) {
        if (size + count > buffer.size) buffer = buffer.copyOf(max(buffer.size * 2, size + count))
        System.arraycopy(frame, 0, buffer, size, count)
        size += count
    }

    private companion object {
        const val MIN_SPEECH_MS = 150.0
        const val INITIAL_FLOOR_DB = -40.0
    }
}
