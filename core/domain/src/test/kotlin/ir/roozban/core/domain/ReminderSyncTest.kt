package ir.roozban.core.domain

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.ReminderState
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.model.UserSettings
import ir.roozban.core.testing.FakeAlarmScheduler
import ir.roozban.core.testing.FakeReminderRepository
import ir.roozban.core.testing.FakeSettingsRepository
import ir.roozban.core.testing.FakeTaskRepository
import ir.roozban.core.testing.TestClock
import ir.roozban.core.testing.jalali
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalTime

class ReminderSyncTest {

    private fun task(id: String, due: TaskDue?, reminder: ReminderSetting? = ReminderSetting(ReminderKind.NOTIFICATION)) =
        Task(id = id, title = id, due = due, reminder = reminder, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test
    fun `planner applies offset and all-day time`() {
        val settings = UserSettings(allDayReminderTime = LocalTime.of(9, 0))
        val at = task("a", TaskDue.At(jalali("1405-07-03"), LocalTime.of(10, 0)), ReminderSetting(ReminderKind.ALARM, 15))
        assertThat(ReminderPlanner.triggerFor(at, settings)).isEqualTo(jalali("1405-07-03").atTime(9, 45))
        val allDay = task("b", TaskDue.AllDay(jalali("1405-07-03")))
        assertThat(ReminderPlanner.triggerFor(allDay, settings)).isEqualTo(jalali("1405-07-03").atTime(9, 0))
        assertThat(ReminderPlanner.triggerFor(allDay, settings.copy(allDayReminderTime = null))).isNull()
        assertThat(ReminderPlanner.triggerFor(task("c", null), settings)).isNull()
        assertThat(ReminderPlanner.triggerFor(at.copy(completedAt = Instant.EPOCH), settings)).isNull()
    }

    @Test
    fun `reminders beyond the window are stored but not scheduled until reconcile`() = runTest {
        val h = Harness()
        val far = task("far", TaskDue.At(jalali("1405-07-20"), LocalTime.of(10, 0)))
        h.sync.sync(far)
        assertThat(h.reminders.reminders).containsKey("far")
        assertThat(h.scheduler.scheduled).isEmpty()
        h.clock.now = jalali("1405-07-15").atTime(10, 0)
        h.sync.reconcile()
        assertThat(h.scheduler.scheduled).containsKey("far")
    }

    @Test
    fun `a reminder in the past is not created`() = runTest {
        val h = Harness()
        h.sync.sync(task("past", TaskDue.At(jalali("1405-07-02"), LocalTime.of(9, 0))))
        assertThat(h.reminders.reminders).isEmpty()
        assertThat(h.scheduler.scheduled).isEmpty()
    }

    @Test
    fun `reconcile after the phone was off reports recent missed reminders`() = runTest {
        val h = Harness()
        h.sync.sync(task("soon", TaskDue.At(jalali("1405-07-02"), LocalTime.of(11, 0))))
        h.sync.sync(task("old", TaskDue.At(jalali("1405-07-02"), LocalTime.of(10, 30))))
        h.clock.now = jalali("1405-07-03").atTime(10, 15) // 23h45m after «old», 23h15m after «soon»
        val missed = h.sync.reconcile()
        assertThat(missed.map { it.taskId }).containsExactly("old", "soon").inOrder()
        h.clock.now = jalali("1405-07-04").atTime(12, 0)
        h.reminders.reminders.values.forEach { h.reminders.markFired(it.taskId) }
        assertThat(h.sync.reconcile()).isEmpty()
    }

    @Test
    fun `stale missed reminders are dropped`() = runTest {
        val h = Harness()
        h.sync.sync(task("t", TaskDue.At(jalali("1405-07-02"), LocalTime.of(11, 0))))
        h.clock.now = jalali("1405-07-05").atTime(10, 0)
        assertThat(h.sync.reconcile()).isEmpty()
        assertThat(h.reminders.reminders["t"]?.state).isEqualTo(ReminderState.FIRED)
    }

    @Test
    fun snooze() = runTest {
        val h = Harness()
        val t = task("t", TaskDue.At(jalali("1405-07-02"), LocalTime.of(10, 30)), ReminderSetting(ReminderKind.ALARM))
        h.tasks.upsert(t)
        h.sync.sync(t)
        h.clock.now = jalali("1405-07-02").atTime(10, 30, 12)
        h.sync.snooze("t", 10)
        assertThat(h.scheduler.scheduled["t"]).isEqualTo(ReminderKind.ALARM to jalali("1405-07-02").atTime(10, 40))
        assertThat(h.reminders.reminders["t"]?.state).isEqualTo(ReminderState.PENDING)
    }

    @Test
    fun `changing the all-day time and resyncing`() = runTest {
        val h = Harness()
        val t = task("t", TaskDue.AllDay(jalali("1405-07-04")))
        h.tasks.upsert(t)
        h.sync.sync(t)
        assertThat(h.scheduler.scheduled["t"]?.second).isEqualTo(jalali("1405-07-04").atTime(9, 0))
        h.settings.update { it.copy(allDayReminderTime = LocalTime.of(7, 0)) }
        h.sync.syncAll()
        assertThat(h.scheduler.scheduled["t"]?.second).isEqualTo(jalali("1405-07-04").atTime(7, 0))
    }
}
