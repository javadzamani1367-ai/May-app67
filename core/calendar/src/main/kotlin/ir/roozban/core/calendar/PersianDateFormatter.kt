package ir.roozban.core.calendar

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Human-readable Persian formatting. All output uses Persian digits. */
object PersianDateFormatter {

    /** «پنجشنبه ۲ مهر ۱۴۰۵» */
    fun fullDate(date: JalaliDate): String =
        "${PersianNames.weekday(date.dayOfWeek)} ${dayMonthYear(date)}"

    /** «۲ مهر ۱۴۰۵» */
    fun dayMonthYear(date: JalaliDate): String =
        "${PersianDigits.format(date.day)} ${PersianNames.jalaliMonth(date.month)} ${PersianDigits.format(date.year)}"

    /** «۲ مهر» */
    fun dayMonth(date: JalaliDate): String =
        "${PersianDigits.format(date.day)} ${PersianNames.jalaliMonth(date.month)}"

    /** «۱۴۰۵/۰۷/۰۲» */
    fun numeric(date: JalaliDate): String =
        "${PersianDigits.format(date.year)}/${PersianDigits.format2(date.month)}/${PersianDigits.format2(date.day)}"

    /** «۱۷:۰۵» */
    fun time(time: LocalTime): String =
        "${PersianDigits.format2(time.hour)}:${PersianDigits.format2(time.minute)}"

    /** «۲۴ سپتامبر ۲۰۲۶» */
    fun gregorian(date: LocalDate): String =
        "${PersianDigits.format(date.dayOfMonth)} ${PersianNames.GREGORIAN_MONTHS[date.monthValue - 1]} ${PersianDigits.format(date.year)}"

    /** «۱۲ ربیع‌الثانی ۱۴۴۸», or null when out of range. */
    fun hijri(date: LocalDate, offsetDays: Int = 0): String? =
        HijriDates.from(date, offsetDays)?.let {
            "${PersianDigits.format(it.day)} ${PersianNames.HIJRI_MONTHS[it.month - 1]} ${PersianDigits.format(it.year)}"
        }

    /**
     * Short date relative to [today]: «امروز»، «فردا»، «دیروز»، weekday name within the coming week,
     * otherwise «۱۵ آبان» (with the year when it differs).
     */
    fun relativeDay(date: LocalDate, today: LocalDate): String {
        val diff = date.toEpochDay() - today.toEpochDay()
        return when {
            diff == 0L -> "امروز"
            diff == 1L -> "فردا"
            diff == 2L -> "پس‌فردا"
            diff == -1L -> "دیروز"
            diff in 3..6 -> PersianNames.weekday(date.dayOfWeek)
            else -> {
                val j = date.toJalali()
                if (j.year == today.toJalali().year) dayMonth(j) else dayMonthYear(j)
            }
        }
    }

    /** «فردا، ۱۷:۰۰» */
    fun relativeDateTime(dateTime: LocalDateTime, today: LocalDate): String =
        "${relativeDay(dateTime.toLocalDate(), today)}، ${time(dateTime.toLocalTime())}"
}
