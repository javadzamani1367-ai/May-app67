package ir.roozban.core.alarm

import ir.roozban.core.domain.EventReminders
import ir.roozban.core.domain.FocusService
import ir.roozban.core.domain.ReminderRepository
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.RoutineReminders
import ir.roozban.core.domain.TaskRepository
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Brings AlarmManager in line with the database. Runs at app start, boot, clock/time-zone change,
 * app update, exact-alarm permission change and once a day.
 */
@Singleton
class ReminderReconciler @Inject constructor(
    private val sync: ReminderSync,
    private val tasks: TaskRepository,
    private val reminders: ReminderRepository,
    private val notifier: ReminderNotifier,
    private val scheduler: AndroidAlarmScheduler,
    private val focus: FocusService,
    private val routines: RoutineReminders,
    private val events: EventReminders,
    private val dateNotifier: DateNotifier,
    private val clock: Clock,
) {
    suspend fun reconcile() {
        // A focus period that ended while the phone was off is finished now; otherwise re-armed.
        focus.onAlarm()
        routines.syncAll()
        events.syncAll()
        dateNotifier.refresh()
        for (missed in sync.reconcile()) {
            tasks.get(missed.taskId)?.takeIf { !it.isCompleted }?.let { notifier.show(it, missed.kind, missed = true) }
            reminders.markFired(missed.taskId)
        }
        val tomorrowAt3 = LocalDate.now(clock).plusDays(1).atTime(LocalTime.of(3, 0))
        scheduler.scheduleDailyReconcile(tomorrowAt3.atZone(clock.zone).toInstant().toEpochMilli())
    }
}
