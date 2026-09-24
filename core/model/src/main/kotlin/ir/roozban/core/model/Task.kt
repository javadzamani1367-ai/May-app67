package ir.roozban.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * When a task is due. Times are *floating* wall-clock times: «ساعت ۹ صبح» stays 09:00 local time
 * even if the device time zone changes.
 */
sealed interface TaskDue {
    val date: LocalDate

    data class AllDay(override val date: LocalDate) : TaskDue

    data class At(override val date: LocalDate, val time: LocalTime) : TaskDue {
        val dateTime: LocalDateTime get() = date.atTime(time)
    }

    fun withDate(newDate: LocalDate): TaskDue = when (this) {
        is AllDay -> AllDay(newDate)
        is At -> At(newDate, time)
    }
}

enum class ReminderKind { NOTIFICATION, ALARM }

/** How the user wants to be reminded: [offsetMinutes] before the due time. */
data class ReminderSetting(val kind: ReminderKind, val offsetMinutes: Int = 0)

/** The Eisenhower matrix. */
enum class Quadrant { DO_FIRST, SCHEDULE, DELEGATE, ELIMINATE, NONE }

data class Task(
    val id: String,
    val title: String,
    val notes: String = "",
    val due: TaskDue? = null,
    val important: Boolean = false,
    val urgent: Boolean = false,
    val estimateMinutes: Int? = null,
    /** RRULE (see `RecurrenceSpec`); the series' first occurrence is [recurrenceStart]. */
    val recurrence: String? = null,
    val recurrenceStart: LocalDate? = null,
    val reminder: ReminderSetting? = null,
    val projectId: String? = null,
    /** Set for subtasks; subtasks are shown inside their parent, not in the lists. */
    val parentId: String? = null,
    val labelIds: Set<String> = emptySet(),
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isCompleted: Boolean get() = completedAt != null

    val quadrant: Quadrant
        get() = when {
            important && urgent -> Quadrant.DO_FIRST
            important -> Quadrant.SCHEDULE
            urgent -> Quadrant.DELEGATE
            else -> Quadrant.NONE
        }
}

/** A materialized reminder for one task (at most one per task). */
data class Reminder(
    val taskId: String,
    val triggerAt: LocalDateTime,
    val kind: ReminderKind,
    val state: ReminderState,
)

enum class ReminderState { PENDING, FIRED }
