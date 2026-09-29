package ir.roozban.core.calendar

import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

/** A calendar a date can be read in. */
enum class CalendarSystem { JALALI, GREGORIAN, HIJRI }

/** Years, months and days between two dates, counted in one calendar. */
data class Span(val years: Int, val months: Int, val days: Int)

/**
 * The time between two dates: total days and weeks, and years/months/days as people count them
 * in each calendar (the same interval is «۲ ماه و ۱ روز» in one calendar and «۲ ماه و ۳ روز» in
 * another, because month lengths differ).
 */
data class DateSpan(
    val from: LocalDate,
    val to: LocalDate,
    /** Negative when [to] is before [from]. */
    val totalDays: Long,
    val jalali: Span,
    val gregorian: Span,
    /** Null outside the range of the Hijri calendar data. */
    val hijri: Span?,
) {
    val weeks: Long get() = kotlin.math.abs(totalDays) / 7
    val weekDays: Long get() = kotlin.math.abs(totalDays) % 7

    companion object {
        /** With [includeEnd] both days count («از شنبه تا شنبه» = ۸ روز). */
        fun between(from: LocalDate, to: LocalDate, includeEnd: Boolean = false, hijriOffset: Int = 0): DateSpan {
            val forward = !to.isBefore(from)
            val a = if (forward) from else to
            val b0 = if (forward) to else from
            val b = if (includeEnd) b0.plusDays(1) else b0
            val days = ChronoUnit.DAYS.between(a, b)
            return DateSpan(
                from = from,
                to = to,
                totalDays = if (forward) days else -days,
                jalali = jalali(a, b),
                gregorian = Period.between(a, b).let { Span(it.years, it.months, it.days) },
                hijri = hijri(a, b, hijriOffset),
            )
        }

        private fun jalali(a: LocalDate, b: LocalDate): Span {
            val x = a.toJalali()
            val y = b.toJalali()
            return borrow(y.year - x.year, y.month - x.month, y.day - x.day) { year, month ->
                JalaliDate.monthLength(year, month)
            }.let { fix -> fix(y.year, y.month) }
        }

        private fun hijri(a: LocalDate, b: LocalDate, offset: Int): Span? {
            val x = HijriDates.from(a, offset) ?: return null
            val y = HijriDates.from(b, offset) ?: return null
            return borrow(y.year - x.year, y.month - x.month, y.day - x.day) { year, month ->
                hijriMonthLength(year, month, offset) ?: 30
            }.let { fix -> fix(y.year, y.month) }
        }

        /**
         * Normalizes raw differences: a negative day count borrows the length of the month before
         * the end date's month, a negative month count borrows a year.
         */
        private fun borrow(dy: Int, dm: Int, dd: Int, monthLength: (Int, Int) -> Int): (Int, Int) -> Span = { endYear, endMonth ->
            var years = dy
            var months = dm
            var days = dd
            if (days < 0) {
                val (py, pm) = if (endMonth == 1) endYear - 1 to 12 else endYear to endMonth - 1
                days += monthLength(py, pm)
                months -= 1
            }
            if (months < 0) {
                months += 12
                years -= 1
            }
            Span(years, months, days)
        }

        private fun hijriMonthLength(year: Int, month: Int, offset: Int): Int? {
            val start = HijriDates.toLocalDate(year, month, 1, offset) ?: return null
            val (ny, nm) = if (month == 12) year + 1 to 1 else year to month + 1
            val next = HijriDates.toLocalDate(ny, nm, 1, offset) ?: return null
            return ChronoUnit.DAYS.between(start, next).toInt()
        }
    }
}

/** Reading and writing dates as (year, month, day) in any [CalendarSystem]. */
object CalendarDates {
    fun parts(date: LocalDate, calendar: CalendarSystem, hijriOffset: Int = 0): Triple<Int, Int, Int>? = when (calendar) {
        CalendarSystem.JALALI -> date.toJalali().let { Triple(it.year, it.month, it.day) }
        CalendarSystem.GREGORIAN -> Triple(date.year, date.monthValue, date.dayOfMonth)
        CalendarSystem.HIJRI -> HijriDates.from(date, hijriOffset)?.let { Triple(it.year, it.month, it.day) }
    }

    /** Null when the date does not exist (e.g. 31 Mehr, 30 Esfand of a common year). */
    fun of(calendar: CalendarSystem, year: Int, month: Int, day: Int, hijriOffset: Int = 0): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        return runCatching {
            when (calendar) {
                CalendarSystem.JALALI -> if (day <= JalaliDate.monthLength(year, month)) JalaliDate.of(year, month, day).toLocalDate() else null
                CalendarSystem.GREGORIAN -> LocalDate.of(year, month, day)
                CalendarSystem.HIJRI -> HijriDates.toLocalDate(year, month, day, hijriOffset)
                    ?.takeIf { HijriDates.from(it, hijriOffset)?.day == day }
            }
        }.getOrNull()
    }
}
