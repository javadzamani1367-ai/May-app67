package ir.roozban.core.timeparser

import ir.roozban.core.calendar.PersianDigits

internal enum class TokenType { WORD, NUMBER, CLOCK, DATE, PUNCT }

/**
 * A token with its canonical [text] and its [start]/[end] offsets (end exclusive) in the
 * **original** input, so that matches can be highlighted and removed from the title exactly.
 */
internal data class Token(
    val text: String,
    val type: TokenType,
    val start: Int,
    val end: Int,
) {
    val intValue: Int? get() = if (type == TokenType.NUMBER) text.toIntOrNull() else null

    fun isWord(vararg words: String): Boolean = type == TokenType.WORD && text in words
}

/**
 * Splits Persian free text into tokens.
 *
 * Normalization happens per character and never changes offsets:
 * Arabic ي/ك → ی/ک, all digits → ASCII, diacritics and tatweel are skipped,
 * and ZWNJ (نیم‌فاصله) separates tokens so «سه‌شنبه‌ی» becomes «سه» «شنبه» «ی».
 * Word tokens are then mapped to a canonical spelling via [Lexicon.canonical]
 * (e.g. colloquial «دیگه» → «دیگر»).
 */
internal object Tokenizer {

    private fun normalizeLetter(c: Char): Char = when (c) {
        'ي', 'ى' -> 'ی'
        'ك' -> 'ک'
        'ۀ', 'ة' -> 'ه'
        'أ', 'إ' -> 'ا'
        else -> c
    }

    /** Harakat, tanwin, superscript alef and tatweel: dropped from token text. */
    private fun isIgnorable(c: Char): Boolean = c in 'ً'..'ٟ' || c == 'ٰ' || c == 'ـ'

    private fun isLetter(c: Char): Boolean = !isIgnorable(c) && Character.isLetter(c)

    fun tokenize(input: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        val n = input.length
        while (i < n) {
            val c = input[i]
            when {
                PersianDigits.isAnyDigit(c) -> {
                    val start = i
                    val sb = StringBuilder()
                    while (i < n && PersianDigits.isAnyDigit(input[i])) sb.append(PersianDigits.toAsciiDigit(input[i++]))
                    // Clock «17:30» / «۵:۳۰»
                    if (i + 1 < n && input[i] == ':' && PersianDigits.isAnyDigit(input[i + 1])) {
                        sb.append(':'); i++
                        while (i < n && PersianDigits.isAnyDigit(input[i])) sb.append(PersianDigits.toAsciiDigit(input[i++]))
                        tokens += Token(sb.toString(), TokenType.CLOCK, start, i)
                        continue
                    }
                    // Date «1405/7/15» or «7/15»
                    if (i + 1 < n && (input[i] == '/' || input[i] == '-') && PersianDigits.isAnyDigit(input[i + 1])) {
                        val sep = input[i]
                        var parts = 1
                        while (i + 1 < n && input[i] == sep && PersianDigits.isAnyDigit(input[i + 1]) && parts < 3) {
                            sb.append('/'); i++; parts++
                            while (i < n && PersianDigits.isAnyDigit(input[i])) sb.append(PersianDigits.toAsciiDigit(input[i++]))
                        }
                        tokens += Token(sb.toString(), TokenType.DATE, start, i)
                        continue
                    }
                    tokens += Token(sb.toString(), TokenType.NUMBER, start, i)
                }
                isLetter(c) -> {
                    val start = i
                    val sb = StringBuilder()
                    var end = i
                    while (i < n && (isLetter(input[i]) || isIgnorable(input[i]))) {
                        if (!isIgnorable(input[i])) {
                            sb.append(normalizeLetter(input[i]))
                            end = i + 1
                        }
                        i++
                    }
                    tokens += Token(Lexicon.canonical(sb.toString()), TokenType.WORD, start, end)
                }
                c.isWhitespace() || c == '‌' || c == '‍' || isIgnorable(c) -> i++
                else -> {
                    tokens += Token(c.toString(), TokenType.PUNCT, i, i + 1)
                    i++
                }
            }
        }
        return tokens
    }
}
