package ir.roozban.ai.core.audio

/**
 * Prepares text for reading aloud: pieces a synthesizer can take one at a time, so playback
 * starts after the first sentence and a whole page can be read without limit.
 */
object SpeechText {
    const val MAX_CHUNK = 280

    fun chunks(text: String, max: Int = MAX_CHUNK): List<String> {
        val clean = normalize(text)
        if (clean.isEmpty()) return emptyList()
        val sentences = SENTENCE_END.split(clean).map { it.trim() }.filter { it.isNotEmpty() }
        return sentences.flatMap { split(it, max) }
    }

    /**
     * Persian and Arabic digits to ASCII (the phonemizer reads those as Persian numbers),
     * markdown and list marks, emoji and zero-width marks other than the ZWNJ dropped.
     */
    fun normalize(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                in '۰'..'۹' -> append('0' + (c - '۰'))
                in '٠'..'٩' -> append('0' + (c - '٠'))
                'ي' -> append('ی')
                'ك' -> append('ک')
                '*', '#', '_', '`', '•', '>', '|' -> append(' ')
                '\u200c' -> append(c)
                else -> if (Character.getType(c) == Character.SURROGATE.toInt() || Character.getType(c) == Character.FORMAT.toInt() ||
                    Character.getType(c) == Character.OTHER_SYMBOL.toInt()
                ) {
                    append(' ')
                } else {
                    append(c)
                }
            }
        }
    }.replace(SPACES, " ").replace(NEWLINES, "\n").trim()

    /** A long sentence is cut at a comma, else at the last space, near [max]. */
    private fun split(sentence: String, max: Int): List<String> {
        if (sentence.length <= max) return listOf(sentence)
        val out = mutableListOf<String>()
        var rest = sentence
        while (rest.length > max) {
            val window = rest.substring(0, max)
            val cut = listOf(window.lastIndexOf('،'), window.lastIndexOf(','), window.lastIndexOf('؛'))
                .maxOrNull()?.takeIf { it >= max / 3 }?.plus(1)
                ?: window.lastIndexOf(' ').takeIf { it >= max / 3 }
                ?: max
            out += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trim()
        }
        if (rest.isNotEmpty()) out += rest
        return out.filter { it.isNotEmpty() }
    }

    private val SENTENCE_END = Regex("(?<=[.!?؟؛])\\s+|\\n+")
    private val SPACES = Regex("[ \\t\\u00a0]+")
    private val NEWLINES = Regex("\\s*\\n\\s*")
}
