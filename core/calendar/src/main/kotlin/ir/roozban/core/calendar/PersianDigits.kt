package ir.roozban.core.calendar

/** Conversion between ASCII, Persian (۰-۹) and Arabic-Indic (٠-٩) digits. */
object PersianDigits {
    private const val PERSIAN_ZERO = '۰'
    private const val ARABIC_ZERO = '٠'

    fun isAnyDigit(c: Char): Boolean = toAsciiDigit(c) != null

    /** Returns the ASCII digit for any supported digit character, or null. */
    fun toAsciiDigit(c: Char): Char? = when (c) {
        in '0'..'9' -> c
        in PERSIAN_ZERO..(PERSIAN_ZERO + 9) -> '0' + (c - PERSIAN_ZERO)
        in ARABIC_ZERO..(ARABIC_ZERO + 9) -> '0' + (c - ARABIC_ZERO)
        else -> null
    }

    fun toAscii(text: String): String = buildString(text.length) {
        for (c in text) append(toAsciiDigit(c) ?: c)
    }

    fun toPersian(text: String): String = buildString(text.length) {
        for (c in text) {
            val ascii = toAsciiDigit(c)
            append(if (ascii != null) PERSIAN_ZERO + (ascii - '0') else c)
        }
    }

    fun format(number: Int): String = toPersian(number.toString())

    fun format(number: Long): String = toPersian(number.toString())

    /** Zero-padded two-digit Persian number, e.g. 5 -> «۰۵». */
    fun format2(number: Int): String = toPersian("%02d".format(number))
}
