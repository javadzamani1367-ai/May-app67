package ir.ilam.inspection.util

/**
 * Digit shaping. Persian digits are for display only; anything that reaches the
 * database, a file name or the sync protocol keeps latin digits.
 */
object PersianNumbers {

    private const val PERSIAN = "۰۱۲۳۴۵۶۷۸۹"
    private const val ARABIC = "٠١٢٣٤٥٦٧٨٩"

    fun toPersian(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(if (ch in '0'..'9') PERSIAN[ch - '0'] else ch)
        }
        return sb.toString()
    }

    fun toPersian(value: Long): String = toPersian(value.toString())

    fun toPersian(value: Int): String = toPersian(value.toString())

    /** Trims a trailing `.0` so amperage reads `۶۳` rather than `۶۳٫۰`. */
    fun toPersian(value: Double?): String {
        if (value == null) return ""
        val text = if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
        return toPersian(text).replace('.', '٫')
    }

    /** Accepts persian or arabic-indic digits typed by the user. */
    fun toLatin(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val p = PERSIAN.indexOf(ch)
            val a = ARABIC.indexOf(ch)
            sb.append(
                when {
                    p >= 0 -> '0' + p
                    a >= 0 -> '0' + a
                    ch == '٫' -> '.'
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    fun parseDoubleOrNull(input: String?): Double? =
        toLatin(input).trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    fun parseIntOrNull(input: String?): Int? =
        toLatin(input).trim().takeIf { it.isNotEmpty() }?.toIntOrNull()

    /** Groups thousands for readable watt totals: `۱۲٬۵۰۰`. */
    fun grouped(value: Double?): String {
        if (value == null) return ""
        val whole = value.toLong()
        val text = whole.toString().reversed().chunked(3).joinToString("٬").reversed()
        return toPersian(text)
    }
}
