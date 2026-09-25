package ir.roozban.ai.core.audio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Energy-based voice activity detection for dictation: decides when the user started talking
 * and when they stopped. Works on 16 kHz mono PCM16 frames of any length (30 ms is typical).
 *
 * The noise floor is learned from the quietest frames, so a noisy room needs a louder voice
 * than a quiet one. Speech is a frame [marginDb] above the floor (and above [minSpeechDb]);
 * the recording ends after [endSilenceMs] of non-speech following speech, when nothing was
 * said for [noSpeechTimeoutMs], or at [maxMs].
 */
class Vad(
    private val sampleRate: Int = 16_000,
    private val marginDb: Double = 12.0,
    private val minSpeechDb: Double = -48.0,
    private val endSilenceMs: Int = 1_200,
    private val noSpeechTimeoutMs: Int = 8_000,
    private val maxMs: Int = 30_000,
    private val minSpeechMs: Int = 150,
) {
    enum class State { WAITING, SPEAKING, DONE }

    enum class EndReason { SILENCE_AFTER_SPEECH, NO_SPEECH, MAX_LENGTH }

    var state = State.WAITING
        private set

    var endReason: EndReason? = null
        private set

    /** Level of the last frame, 0..1, for the recording animation. */
    var level: Float = 0f
        private set

    private var noiseDb = -60.0
    private var elapsedMs = 0.0
    private var speechMs = 0.0
    private var silenceMs = 0.0
    private var frames = 0

    /** Feeds one frame; returns the state after it. */
    fun feed(frame: ShortArray, size: Int = frame.size): State {
        if (state == State.DONE || size == 0) return state
        val db = dbfs(frame, size)
        val ms = size * 1000.0 / sampleRate
        elapsedMs += ms
        frames++
        level = ((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()

        // The floor follows quiet frames quickly and loud ones slowly.
        noiseDb = when {
            frames <= 3 -> if (frames == 1) db else minOf(noiseDb, db)
            db < noiseDb -> noiseDb * 0.7 + db * 0.3
            else -> noiseDb * 0.995 + db * 0.005
        }
        val voiced = db > max(noiseDb + marginDb, minSpeechDb)

        when (state) {
            State.WAITING -> {
                speechMs = if (voiced) speechMs + ms else 0.0
                if (speechMs >= minSpeechMs) {
                    state = State.SPEAKING
                    heardSpeech = true
                }
                else if (elapsedMs >= noSpeechTimeoutMs) finish(EndReason.NO_SPEECH)
            }
            State.SPEAKING -> {
                silenceMs = if (voiced) 0.0 else silenceMs + ms
                if (silenceMs >= endSilenceMs) finish(EndReason.SILENCE_AFTER_SPEECH)
            }
            State.DONE -> Unit
        }
        if (state != State.DONE && elapsedMs >= maxMs) finish(EndReason.MAX_LENGTH)
        return state
    }

    /** True once speech was heard. */
    var heardSpeech = false
        private set

    private fun finish(reason: EndReason) {
        state = State.DONE
        endReason = reason
    }

    companion object {
        fun dbfs(frame: ShortArray, size: Int = frame.size): Double {
            if (size == 0) return -100.0
            var sum = 0.0
            for (i in 0 until size) {
                val v = frame[i] / 32768.0
                sum += v * v
            }
            val rms = sqrt(sum / size)
            return if (rms <= 1e-9) -100.0 else 20 * log10(rms)
        }
    }
}
