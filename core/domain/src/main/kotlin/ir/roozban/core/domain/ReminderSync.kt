package ir.roozban.core.domain

import ir.roozban.core.model.Reminder
import ir.roozban.core.model.ReminderState
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.model.UserSettings
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

/** When a task's reminder should fire, or null for none. Pure. */
object ReminderPlanner {
    fun triggerFor(task: Task, settings: UserSettings): LocalDateTime? {
        if (task.isCompleted) return null
        val setting = task.reminder ?: return null
        return when (val due = task.due) {
            null -> null
            is TaskDue.At -> due.dateTime.minusMinutes(setting.offsetMinutes.toLong())
            is TaskDue.AllDay -> settings.allDayReminderTime?.let { due.date.atTime(it) }
        }
    }
}

/**
 * Keeps the reminder table and the platform alarms in line with tasks.
 *
 * Only reminders within [WINDOW] are handed to the platform (AlarmManager has a per-app limit);
 * [reconcile] runs at boot, on time changes, at app start and daily, and schedules the rest as
 * they come into the window.
 */
class ReminderSync @Inject constructor(
    private val reminders: ReminderRepository,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val scheduler: AlarmScheduler,
    private val clock: Clock,
) {
    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    private fun millis(at: LocalDateTime): Long = at.atZone(clock.zone).toInstant().toEpochMilli()

    suspend fun sync(task: Task) {
        val trigger = ReminderPlanner.triggerFor(task, settings.current())
        val setting = task.reminder
        if (trigger == null || setting == null || !trigger.isAfter(now())) {
            reminders.clear(task.id)
            scheduler.cancel(task.id)
            return
        }
        val reminder = Reminder(task.id, trigger, setting.kind, ReminderState.PENDING)
        reminders.set(reminder)
        schedule(reminder)
    }

    /** Re-plans every open task, e.g. after the all-day reminder time changed. */
    suspend fun syncAll() {
        tasks.openTasksWithDue().forEach { sync(it) }
    }

    fun schedule(reminder: Reminder) {
        if (reminder.triggerAt.isBefore(now().plus(WINDOW))) {
            scheduler.schedule(reminder.taskId, reminder.kind, millis(reminder.triggerAt))
        } else {
            scheduler.cancel(reminder.taskId)
        }
    }

    /**
     * Schedules every pending reminder in the window and returns the ones that were missed while
     * the device was off (up to [MISSED_GRACE] ago) so the caller can show them. Older missed
     * reminders are dropped silently.
     */
    suspend fun reconcile(): List<Reminder> {
        val now = now()
        val missed = ArrayList<Reminder>()
        for (r in reminders.pendingUntil(now.plus(WINDOW))) {
            when {
                r.triggerAt.isAfter(now) -> schedule(r)
                r.triggerAt.isAfter(now.minus(MISSED_GRACE)) -> missed += r
                else -> reminders.markFired(r.taskId)
            }
        }
        return missed
    }

    suspend fun snooze(taskId: String, minutes: Long) {
        val task = tasks.get(taskId) ?: return
        val kind = reminders.get(taskId)?.kind ?: task.reminder?.kind ?: return
        val reminder = Reminder(taskId, now().plusMinutes(minutes).withSecond(0).withNano(0), kind, ReminderState.PENDING)
        reminders.set(reminder)
        scheduler.schedule(taskId, kind, millis(reminder.triggerAt))
    }

    suspend fun cancel(taskId: String) {
        reminders.clear(taskId)
        scheduler.cancel(taskId)
    }

    companion object {
        val WINDOW: Duration = Duration.ofDays(7)
        val MISSED_GRACE: Duration = Duration.ofHours(24)
    }
}
