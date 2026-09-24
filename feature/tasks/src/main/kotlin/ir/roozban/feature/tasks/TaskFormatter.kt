package ir.roozban.feature.tasks

import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.TaskDue
import ir.roozban.core.recurrence.Frequency
import ir.roozban.core.recurrence.RecurrenceSpec
import java.time.Duration
import java.time.LocalDate

/** Persian labels for tasks. Pure functions, unit-tested. */
internal object TaskFormatter {

    fun due(due: TaskDue, today: LocalDate): String = when (due) {
        is TaskDue.AllDay -> PersianDateFormatter.relativeDay(due.date, today)
        is TaskDue.At -> PersianDateFormatter.relativeDateTime(due.dateTime, today)
    }

    /** Time only for a task shown under its own day's heading; the full label otherwise. */
    fun dueInSection(due: TaskDue, sectionDate: LocalDate?, today: LocalDate): String? = when {
        due.date == sectionDate && due is TaskDue.At -> PersianDateFormatter.time(due.time)
        due.date == sectionDate -> null
        else -> due(due, today)
    }

    fun recurrence(rrule: String): String? = RecurrenceSpec.parse(rrule)?.let(::recurrence)

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

    fun duration(d: Duration): String = duration(d.toMinutes().toInt())

    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "${PersianDigits.format(m)} دقیقه"
            m == 0 -> "${PersianDigits.format(h)} ساعت"
            m == 30 -> "${PersianDigits.format(h)} ساعت و نیم"
            else -> "${PersianDigits.format(h)} ساعت و ${PersianDigits.format(m)} دقیقه"
        }
    }

    fun reminder(setting: ReminderSetting?): String {
        if (setting == null) return "بدون یادآوری"
        val kind = if (setting.kind == ReminderKind.ALARM) "آلارم" else "اعلان"
        val offset = if (setting.offsetMinutes == 0) "سر وقت" else "${duration(setting.offsetMinutes)} قبل"
        return "$kind $offset"
    }

    /** Section title for a future day: «فردا · ۳ مهر», «یکشنبه · ۵ مهر». */
    fun dayHeading(date: LocalDate, today: LocalDate): String {
        val relative = PersianDateFormatter.relativeDay(date, today)
        val dayMonth = PersianDateFormatter.dayMonth(JalaliDate.from(date))
        return if (relative == dayMonth) relative else "$relative · $dayMonth"
    }
}
