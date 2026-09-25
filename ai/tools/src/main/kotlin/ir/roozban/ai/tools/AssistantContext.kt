package ir.roozban.ai.tools

import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.model.Habit
import ir.roozban.core.model.MemoryFact
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.model.UserSettings
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * What the assistant knows when answering: the clock and a snapshot of open tasks and habits.
 * Tasks are numbered from 1 in [tasks] order; the model refers to them as `#n`.
 */
data class AssistantContext(
    val now: LocalDateTime,
    val tasks: List<Task>,
    val habits: List<Habit>,
    val settings: UserSettings = UserSettings(),
    /** What Roozban knows about the user (pinned and user-stated first). */
    val facts: List<MemoryFact> = emptyList(),
) {
    val today: LocalDate get() = now.toLocalDate()

    fun numberOf(task: Task): Int = tasks.indexOfFirst { it.id == task.id } + 1
}

object Describe {
    /** «امروز ۱۷:۰۰», «فردا», «شنبه ۵ مهر ۰۹:۰۰». */
    fun due(due: TaskDue, today: LocalDate): String {
        val day = day(due.date, today)
        return if (due is TaskDue.At) "$day ${PersianDateFormatter.time(due.time)}" else day
    }

    fun day(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "امروز"
        today.plusDays(1) -> "فردا"
        today.minusDays(1) -> "دیروز"
        else -> {
            val j = date.toJalali()
            val base = PersianDateFormatter.fullDate(j)
            if (j.year == today.toJalali().year) base.substringBeforeLast(' ') else base
        }
    }
}
