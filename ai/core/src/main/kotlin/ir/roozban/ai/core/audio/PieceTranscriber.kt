package ir.roozban.ai.core.audio

import kotlinx.coroutines.CancellationException

/**
 * Turns one dictated piece into text without losing any of it:
 * - a failed attempt is tried once more;
 * - when the model stops early (far too little text for the speech in the piece, which Whisper
 *   sometimes does), the piece is split at its quietest moment and each half is transcribed.
 */
class PieceTranscriber(
    private val transcribe: suspend (ShortArray) -> String,
    private val sampleRate: Int = Pcm.SAMPLE_RATE,
    /** Persian speech gives about 12 letters a second; much less means words were dropped. */
    private val minLettersPerSecond: Double = 4.0,
    private val minSplitSeconds: Double = 5.0,
) {
    suspend operator fun invoke(piece: ShortArray): String {
        val whole = attempt(piece)
        val seconds = piece.size / sampleRate.toDouble()
        if (seconds < minSplitSeconds || letters(whole) >= seconds * minLettersPerSecond) return whole
        val (a, b) = Pcm.splitAtQuietest(piece, sampleRate)
        val halves = listOf(attempt(a), attempt(b)).filter { it.isNotBlank() }.joinToString(" ")
        return if (letters(halves) > letters(whole)) halves else whole
    }

    private suspend fun attempt(samples: ShortArray): String {
        repeat(ATTEMPTS) {
            try {
                return transcribe(samples)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Try again; the second failure gives up on this piece.
            }
        }
        return ""
    }

    private fun letters(text: String) = text.count { it.isLetterOrDigit() }

    private companion object {
        const val ATTEMPTS = 2
    }
}
