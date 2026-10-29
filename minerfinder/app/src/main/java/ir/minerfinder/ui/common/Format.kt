package ir.minerfinder.ui.common

import java.util.Locale

/** Numbers for display: Persian digits, Latin in storage. */
object Fa {
    private const val PERSIAN = "۰۱۲۳۴۵۶۷۸۹"

    fun digits(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in '0'..'9') PERSIAN[c - '0'] else if (c == '.') '٫' else c)
    }

    fun num(value: Double, decimals: Int = 0): String =
        digits(String.format(Locale.US, "%.${decimals}f", value))

    fun num(value: Float, decimals: Int = 0): String = num(value.toDouble(), decimals)

    fun int(value: Int): String = digits(value.toString())

    fun int(value: Long): String = digits(value.toString())
}
