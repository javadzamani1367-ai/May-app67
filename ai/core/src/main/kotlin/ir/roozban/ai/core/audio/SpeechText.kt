package ir.roozban.ai.core.audio

/**
 * Prepares text for reading aloud: pieces a synthesizer can take one at a time, so playback
 * starts after the first sentence and a whole page can be read without limit.
 */
object SpeechText {
    const val MAX_CHUNK = 280

    fun chunks(text: String, max: Int = MAX_CHUNK): List<String> {
        val clean = spellNumbers(normalize(text))
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

    /**
     * Numbers as Persian words, so every voice reads them the same way: times («9:30» → «نه و سی
     * دقیقه»), percentages, and plain numbers up to the trillions. Runs after [normalize] (ASCII digits).
     */
    fun spellNumbers(text: String): String {
        var t = TIME.replace(text) { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h > 24 || min > 59) m.value else words(h.toLong()) + if (min == 0) "" else " و " + words(min.toLong()) + " دقیقه"
        }
        t = PERCENT.replace(t) { m -> words(m.groupValues[1].toLong()) + " درصد" }
        t = NUMBER.replace(t) { m ->
            val (whole, fraction) = m.value.split('.', '٫').let { it[0] to it.getOrNull(1) }
            val w = whole.toLongOrNull()?.let(::words) ?: return@replace m.value
            if (fraction.isNullOrEmpty()) w else w + " ممیز " + fraction.map { words((it - '0').toLong()) }.joinToString(" ")
        }
        return t
    }

    /** 0 … 999 999 999 999 in words. */
    fun words(n: Long): String {
        if (n == 0L) return "صفر"
        if (n < 0) return "منفی " + words(-n)
        val parts = mutableListOf<String>()
        var rest = n
        var scale = 0
        while (rest > 0 && scale < SCALES.size) {
            val group = (rest % 1000).toInt()
            // «هزار», not «یک هزار».
            if (group != 0) parts.add(0, if (group == 1 && scale == 1) "هزار" else below1000(group) + SCALES[scale])
            rest /= 1000
            scale++
        }
        return parts.joinToString(" و ")
    }

    private fun below1000(n: Int): String {
        val out = mutableListOf<String>()
        if (n >= 100) out += HUNDREDS[n / 100]
        val r = n % 100
        when {
            r == 0 -> Unit
            r < 20 -> out += ONES[r]
            else -> {
                out += TENS[r / 10]
                if (r % 10 != 0) out += ONES[r % 10]
            }
        }
        return out.joinToString(" و ")
    }

    private val ONES = listOf(
        "", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه", "ده",
        "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده",
    )
    private val TENS = listOf("", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود")
    private val HUNDREDS = listOf("", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد", "هشتصد", "نهصد")
    private val SCALES = listOf("", " هزار", " میلیون", " میلیارد")
    private val TIME = Regex("(?<![\\d:])(\\d{1,2}):(\\d{2})(?![\\d:])")
    private val PERCENT = Regex("(\\d{1,9})\\s?[%٪]")
    private val NUMBER = Regex("\\d{1,12}(?:[.٫]\\d{1,3})?")

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
