package ir.roozban.feature.tasks

import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.model.Quadrant
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Turns tasks into the sections of each list. Pure; unit-tested. */
internal object TaskListBuilder {

    private const val UPCOMING_DAYS = 7L

    fun build(mode: ListMode, open: List<Task>, completedToday: List<Task>, now: LocalDateTime): TaskListUiState {
        val today = now.toLocalDate()
        fun item(task: Task, sectionDate: LocalDate?) = task.toItem(sectionDate, today, now)

        val sections = when (mode) {
            ListMode.TODAY -> {
                val overdue = open.filter { it.due != null && it.due!!.date.isBefore(today) }
                val dueToday = open.filter { it.due?.date == today }
                listOf(
                    TaskSection(SectionKind.OVERDUE, "عقب‌افتاده", overdue.map { item(it, null) }),
                    TaskSection(SectionKind.TODAY, "امروز", dueToday.map { item(it, today) }),
                ).filter { it.tasks.isNotEmpty() }
            }
            ListMode.UPCOMING -> {
                val future = open.filter { it.due?.date?.isAfter(today) == true }
                val (soon, later) = future.partition { !it.due!!.date.isAfter(today.plusDays(UPCOMING_DAYS)) }
                val days = soon.groupBy { it.due!!.date }.toSortedMap().map { (date, tasks) ->
                    TaskSection(SectionKind.DAY, TaskFormatter.dayHeading(date, today), tasks.map { item(it, date) })
                }
                val months = later.groupBy { it.due!!.date.toJalali().let { j -> j.year * 100 + j.month } }.toSortedMap()
                    .map { (key, tasks) ->
                        val title = "${PersianNames.jalaliMonth(key % 100)} ${PersianDigits.format(key / 100)}"
                        TaskSection(SectionKind.MONTH, title, tasks.map { item(it, null) })
                    }
                days + months
            }
            ListMode.INBOX -> {
                val undated = open.filter { it.due == null }.sortedWith(compareBy({ it.quadrant.sortOrder }, { it.createdAt }))
                listOf(TaskSection(SectionKind.NO_DATE, null, undated.map { item(it, null) })).filter { it.tasks.isNotEmpty() }
            }
        }
        return TaskListUiState(
            mode = mode,
            loading = false,
            header = if (mode == ListMode.TODAY) header(today) else null,
            sections = sections,
            completed = if (mode == ListMode.TODAY) completedToday.map { item(it, today) } else emptyList(),
        )
    }

    private val Quadrant.sortOrder: Int
        get() = when (this) {
            Quadrant.DO_FIRST -> 0
            Quadrant.SCHEDULE -> 1
            Quadrant.DELEGATE -> 2
            Quadrant.ELIMINATE, Quadrant.NONE -> 3
        }

    private fun Task.toItem(sectionDate: LocalDate?, today: LocalDate, now: LocalDateTime): TaskItem {
        val due = due
        val overdue = !isCompleted && due != null && when (due) {
            is TaskDue.AllDay -> due.date.isBefore(today)
            is TaskDue.At -> due.dateTime.isBefore(now)
        }
        return TaskItem(
            id = id,
            title = title,
            dueLabel = due?.let { TaskFormatter.dueInSection(it, sectionDate, today) },
            overdue = overdue,
            recurrenceLabel = recurrence?.let(TaskFormatter::recurrence),
            estimateLabel = estimateMinutes?.let(TaskFormatter::duration),
            quadrant = quadrant,
            reminder = if (due != null) reminder?.kind else null,
            completed = isCompleted,
        )
    }

    fun header(today: LocalDate): TodayHeader {
        val secondary = listOfNotNull(PersianDateFormatter.gregorian(today), PersianDateFormatter.hijri(today)).joinToString(" · ")
        val start = PersianWeek.startOfWeek(today)
        val week = (0L until 7L).map { offset ->
            val day = start.plusDays(offset)
            WeekDay(
                label = PersianNames.WEEKDAYS_SHORT[offset.toInt()],
                dayOfMonth = PersianDigits.format(day.toJalali().day),
                isToday = day == today,
                isWeekend = day.dayOfWeek == DayOfWeek.FRIDAY,
            )
        }
        return TodayHeader(
            weekday = PersianNames.weekday(today.dayOfWeek),
            date = PersianDateFormatter.dayMonthYear(today.toJalali()),
            secondaryDates = secondary,
            week = week,
        )
    }
}
