package ir.roozban.feature.today

import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.timeparser.Frequency
import ir.roozban.core.timeparser.RecurrenceSpec
import ir.roozban.core.timeparser.ResolvedTime
import java.time.Duration
import java.time.LocalDate

/** Persian labels for parse results. Pure functions, unit-tested. */
internal object PreviewFormatter {

    fun time(time: ResolvedTime, today: LocalDate): String = when (time) {
        is ResolvedTime.AllDay -> PersianDateFormatter.relativeDay(time.date, today)
        is ResolvedTime.At -> PersianDateFormatter.relativeDateTime(time.dateTime, today)
    }

    fun recurrence(r: RecurrenceSpec): String {
        val every = if (r.interval > 1) "هر ${PersianDigits.format(r.interval)}" else "هر"
        return when (r.frequency) {
            Frequency.DAILY -> if (r.interval == 2) "یک روز در میان" else "$every روز"
            Frequency.WEEKLY -> if (r.byWeekdays.isEmpty()) {
                "$every هفته"
            } else {
                "$every " + PersianWeek.DAYS.filter { it in r.byWeekdays }.joinToString("، ") { PersianNames.weekday(it) }
            }
            Frequency.MONTHLY -> when (val d = r.jalaliMonthDay) {
                null -> "$every ماه"
                -1 -> "آخر $every ماه"
                else -> "روز ${PersianDigits.format(d)} $every ماه"
            }
            Frequency.YEARLY -> "$every سال"
        }
    }

    fun duration(d: Duration): String {
        val h = d.toHours()
        val m = (d.toMinutes() % 60).toInt() // toMinutesPart() needs API 31+
        return when {
            h == 0L -> "${PersianDigits.format(m)} دقیقه"
            m == 0 -> "${PersianDigits.format(h)} ساعت"
            m == 30 -> "${PersianDigits.format(h)} ساعت و نیم"
            else -> "${PersianDigits.format(h)} ساعت و ${PersianDigits.format(m)} دقیقه"
        }
    }
}
