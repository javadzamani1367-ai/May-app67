package ir.roozban.ai.core.audio

/** Offline speech to text (whisper.cpp in the `:ai` process). */
interface SpeechRecognizer {
    /**
     * Transcribes 16 kHz mono PCM16 Persian speech. [prompt] biases the vocabulary (project
     * and label names). Throws [ir.roozban.ai.core.LlmException] when no model is available.
     */
    suspend fun transcribe(samples: ShortArray, prompt: String? = null): String
}

/**
 * Cleans whisper output for an input box: no bracketed noise tags, single spaces, Persian ی/ک,
 * the usual half-spaces (می‌روم، کتاب‌ها، بزرگ‌تر) and no looping repeats.
 */
object Transcript {
    private val TAGS = Regex("\\[[^\\]]*]|\\([^)]*\\)|♪")
    private val SPACES = Regex("\\s+")
    private const val ZWNJ = '\u200C'
    private const val LETTER = "[\\u0621-\\u064A\\u067E\\u0686\\u0698\\u06A9\\u06AF\\u06CC]"

    /** «می روم» → «می‌روم», «نمی خواهم» → «نمی‌خواهم». */
    private val PREFIX = Regex("(?<!$LETTER)(ن?می) (?=$LETTER{2,})")

    /** «کتاب ها» → «کتاب‌ها», «بزرگ تر» → «بزرگ‌تر». */
    private val SUFFIX = Regex("(?<=$LETTER) (ها|های|هایی|هایم|هایت|هایش|هایمان|هایتان|هایشان|تر|ترین|ترها)(?!$LETTER)")

    /** The same words four times or more in a row: a model loop, kept once. */
    private val LOOP = Regex("((?:\\S+ ){0,6}?\\S+)(?: \\1){3,}")

    fun clean(text: String): String = text.replace(TAGS, " ")
        .replace('ي', 'ی').replace('ك', 'ک').replace('ى', 'ی')
        .replace(SPACES, " ").trim()
        .replace(PREFIX) { "${it.groupValues[1]}$ZWNJ" }
        .replace(SUFFIX) { "$ZWNJ${it.groupValues[1]}" }
        .replace(LOOP) { it.groupValues[1] }
        .trimEnd('.', '،', '…').trim()
}
