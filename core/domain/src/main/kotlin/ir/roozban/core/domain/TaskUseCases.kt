package ir.roozban.core.domain

import ir.roozban.core.model.Task
import ir.roozban.core.recurrence.RecurrenceSpec
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class AddTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val reminders: ReminderSync,
    private val clock: Clock,
) {
    /** Creates a task from quick-add input. Returns null when there is no title. */
    suspend operator fun invoke(input: QuickAddResult): Task? {
        if (input.title.isBlank()) return null
        val now = Instant.now(clock)
        val task = Task(
            id = UUID.randomUUID().toString(),
            title = input.title,
            due = input.due,
            important = input.important,
            urgent = input.urgent,
            estimateMinutes = input.estimate?.toMinutes()?.toInt(),
            recurrence = input.recurrence?.toRRule(),
            recurrenceStart = input.recurrence?.let { input.due?.date },
            reminder = if (input.due != null) settings.current().defaultReminder else null,
            createdAt = now,
            updatedAt = now,
        )
        tasks.upsert(task)
        reminders.sync(task)
        return task
    }
}

class UpdateTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
    private val clock: Clock,
) {
    suspend operator fun invoke(task: Task) {
        // A recurring task keeps its series anchored to its (possibly new) first date.
        val anchored = if (task.recurrence != null && task.recurrenceStart == null) task.copy(recurrenceStart = task.due?.date) else task
        val updated = anchored.copy(updatedAt = Instant.now(clock))
        tasks.upsert(updated)
        reminders.sync(updated)
    }
}

class CompleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
    private val clock: Clock,
) {
    /**
     * Completes a task. A recurring task instead advances to its next occurrence after today
     * (missed occurrences are skipped) and the completion is recorded.
     */
    suspend operator fun invoke(task: Task): Undo {
        val now = Instant.now(clock)
        val due = task.due
        val spec = task.recurrence?.let(RecurrenceSpec::parse)
        if (spec != null && due != null) {
            val today = LocalDate.now(clock)
            val after = maxOf(due.date, today.minusDays(1))
            val next = spec.nextAfter(after, task.recurrenceStart ?: due.date)
            val advanced = task.copy(due = due.withDate(next), updatedAt = now)
            tasks.upsert(advanced)
            tasks.recordCompletion(task.id, due.date, now)
            reminders.sync(advanced)
            return Undo {
                tasks.upsert(task)
                tasks.removeCompletion(task.id, due.date)
                reminders.sync(task)
            }
        }
        val done = task.copy(completedAt = now, updatedAt = now)
        tasks.upsert(done)
        reminders.sync(done)
        return Undo {
            tasks.upsert(task)
            reminders.sync(task)
        }
    }
}

class ReopenTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
    private val clock: Clock,
) {
    suspend operator fun invoke(task: Task) {
        val reopened = task.copy(completedAt = null, updatedAt = Instant.now(clock))
        tasks.upsert(reopened)
        reminders.sync(reopened)
    }
}

class DeleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val reminders: ReminderSync,
) {
    suspend operator fun invoke(task: Task): Undo {
        tasks.delete(task.id)
        reminders.cancel(task.id)
        return Undo {
            tasks.restore(task.id)
            reminders.sync(task)
        }
    }
}
