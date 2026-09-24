package ir.roozban.feature.reports

import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.domain.Period

/** «۱ ساعت و ۲۰ دقیقه», «۴۵ دقیقه». */
fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${PersianDigits.format(m)} دقیقه"
        m == 0 -> "${PersianDigits.format(h)} ساعت"
        else -> "${PersianDigits.format(h)} ساعت و ${PersianDigits.format(m)} دقیقه"
    }
}

/** Short form for chart labels: «۱٫۵س», «۴۵د». */
fun formatMinutesShort(minutes: Int): String =
    if (minutes >= 60) PersianDigits.toPersian(String.format(java.util.Locale.US, "%.1f", minutes / 60f)).replace('.', '٫') + "س" else PersianDigits.format(minutes) + "د"

/** «۴ تا ۱۰ مهر ۱۴۰۵» or «۲۷ شهریور تا ۲ مهر ۱۴۰۵», or «مهر ۱۴۰۵» for a whole month. */
fun formatPeriod(period: Period, wholeMonth: Boolean): String {
    val s = period.start.toJalali()
    val e = period.end.toJalali()
    if (wholeMonth) return "${PersianNames.jalaliMonth(s.month)} ${PersianDigits.format(s.year)}"
    val start = if (s.month == e.month) PersianDigits.format(s.day) else "${PersianDigits.format(s.day)} ${PersianNames.jalaliMonth(s.month)}"
    val startYear = if (s.year != e.year) " ${PersianDigits.format(s.year)}" else ""
    return "$start$startYear تا ${PersianDigits.format(e.day)} ${PersianNames.jalaliMonth(e.month)} ${PersianDigits.format(e.year)}"
}

/** «۱۰ صبح», «۳ بعدازظهر» for an hour of day. */
fun formatHour(hour: Int): String {
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    val part = when (hour) {
        in 0..4 -> "بامداد"
        in 5..11 -> "صبح"
        12, 13 -> "ظهر"
        in 14..16 -> "بعدازظهر"
        in 17..19 -> "عصر"
        else -> "شب"
    }
    return "${PersianDigits.format(h12)} $part"
}

/** «+۲۰٪» / «−۵٪» versus the previous period; null without a baseline. */
fun formatChange(current: Int, previous: Int): String? {
    if (previous <= 0) return null
    val pct = Math.round((current - previous) * 100f / previous)
    return when {
        pct > 0 -> "+${PersianDigits.format(pct)}٪"
        pct < 0 -> "−${PersianDigits.format(-pct)}٪"
        else -> "۰٪"
    }
}
