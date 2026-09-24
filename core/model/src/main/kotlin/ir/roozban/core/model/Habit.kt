package ir.roozban.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

sealed interface HabitSchedule {
    data object Daily : HabitSchedule

    /** Only on these days of the week. */
    data class Weekdays(val days: Set<DayOfWeek>) : HabitSchedule

    /** Any N days in each (Saturday-based) week. */
    data class TimesPerWeek(val times: Int) : HabitSchedule

    fun isScheduledOn(date: LocalDate): Boolean = when (this) {
        Daily -> true
        is Weekdays -> date.dayOfWeek in days
        is TimesPerWeek -> true
    }

    /** Compact storage form: `D`, `W:1,3,5` (ISO day numbers) or `N:3`. */
    fun encode(): String = when (this) {
        Daily -> "D"
        is Weekdays -> "W:" + days.map { it.value }.sorted().joinToString(",")
        is TimesPerWeek -> "N:$times"
    }

    companion object {
        fun decode(value: String): HabitSchedule = when {
            value.startsWith("W:") -> {
                val days = value.substring(2).split(',').mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..7 }.map(DayOfWeek::of).toSet()
                if (days.isEmpty()) Daily else Weekdays(days)
            }
            value.startsWith("N:") -> TimesPerWeek(value.substring(2).toIntOrNull()?.coerceIn(1, 7) ?: 1)
            else -> Daily
        }
    }
}

data class Habit(
    val id: String,
    val name: String,
    val color: Int = 0,
    val schedule: HabitSchedule = HabitSchedule.Daily,
    /** How many times a day counts as done, e.g. «۸ لیوان آب». */
    val targetPerDay: Int = 1,
    val reminderTime: LocalTime? = null,
    val startDate: LocalDate,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Progress of a habit on one day; absent means zero. */
data class HabitLog(
    val habitId: String,
    val date: LocalDate,
    val count: Int,
    val updatedAt: Instant,
)
