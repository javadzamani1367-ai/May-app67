package ir.roozban.ai.core.audio

/** Offline speech to text (whisper.cpp in the `:ai` process). */
interface SpeechRecognizer {
    /**
     * Transcribes 16 kHz mono PCM16 Persian speech. [prompt] biases the vocabulary (project
     * and label names). Throws [ir.roozban.ai.core.LlmException] when no model is available.
     */
    suspend fun transcribe(samples: ShortArray, prompt: String? = null): String
}

/** Cleans whisper output for an input box: no bracketed noise tags, single spaces, Persian ی/ک. */
object Transcript {
    private val TAGS = Regex("\\[[^\\]]*]|\\([^)]*\\)|♪")
    private val SPACES = Regex("\\s+")

    fun clean(text: String): String = text.replace(TAGS, " ")
        .replace('ي', 'ی').replace('ك', 'ک')
        .replace(SPACES, " ").trim()
        .trimEnd('.', '،', '…').trim()
}
