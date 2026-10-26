package ir.ilam.inspection.ui.centers

import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/** How a cell of the statistics reads on the phone, and what its edit box starts with. */
object CenterFormat {

    fun show(type: String, value: Any?): String {
        if (value == null || value.toString().isBlank()) return ""
        return when (type) {
            "date" -> (value as? Number)?.let { PersianDate.format(it.toLong()) } ?: PersianNumbers.toPersian(value.toString())
            "money", "int" -> PersianNumbers.grouped((value as? Number)?.toDouble() ?: PersianNumbers.parseDoubleOrNull(value.toString()))
            "kw" -> PersianNumbers.toPersian(PersianNumbers.plain((value as? Number)?.toDouble()))
            "tick" -> "✓"
            else -> PersianNumbers.toPersian(value.toString())
        }
    }

    /** Edit text: Jalali for dates, plain digits for numbers, the text itself otherwise. */
    fun editText(type: String, value: Any?): String = when {
        value == null -> ""
        type == "date" && value is Number -> PersianDate.format(value.toLong())
        type in setOf("money", "int", "kw") && value is Number -> PersianNumbers.plain(value.toDouble())
        type == "tick" -> if (value.toString() == "0") "" else "1"
        else -> value.toString()
    }
}
