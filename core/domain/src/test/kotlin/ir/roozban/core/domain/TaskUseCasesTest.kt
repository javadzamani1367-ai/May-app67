package ir.roozban.core.domain

import ir.roozban.core.testing.FakeAlarmScheduler
import ir.roozban.core.testing.FakeReminderRepository
import ir.roozban.core.testing.FakeSettingsRepository
import ir.roozban.core.testing.FakeTaskRepository
import ir.roozban.core.testing.TestClock
import ir.roozban.core.testing.jalali
import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.TaskDue
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalTime

class TaskUseCasesTest {

    @Test
    fun `adding a timed task schedules its reminder`() = runTest {
        val h = Harness()
        val task = h.quickAdd("جلسه فردا ساعت ۱۰")
        assertThat(task.reminder).isEqualTo(ReminderSetting(ReminderKind.NOTIFICATION))
        assertThat(h.scheduler.scheduled[task.id]).isEqualTo(ReminderKind.NOTIFICATION to jalali("1405-07-03").atTime(10, 0))
    }

    @Test
    fun `task without date has no reminder`() = runTest {
        val h = Harness()
        val task = h.quickAdd("کتاب بخوانم")
        assertThat(task.due).isNull()
        assertThat(task.reminder).isNull()
        assertThat(h.scheduler.scheduled).isEmpty()
    }

    @Test
    fun `all-day task is reminded at the configured time`() = runTest {
        val h = Harness(UserSettings(allDayReminderTime = LocalTime.of(8, 30)))
        val task = h.quickAdd("پرداخت قبض آخر ماه")
        // Stored now; handed to AlarmManager once it is within the 7-day window.
        assertThat(h.reminders.reminders[task.id]?.triggerAt).isEqualTo(jalali("1405-07-30").atTime(8, 30))
        assertThat(h.scheduler.scheduled).isEmpty()
    }

    @Test
    fun `completing and undoing a normal task`() = runTest {
        val h = Harness()
        val task = h.quickAdd("جلسه فردا ساعت ۱۰")
        val undo = h.complete(task)
        assertThat(h.tasks.get(task.id)!!.isCompleted).isTrue()
        assertThat(h.scheduler.scheduled).isEmpty()
        undo()
        assertThat(h.tasks.get(task.id)!!.isCompleted).isFalse()
        assertThat(h.scheduler.scheduled).containsKey(task.id)
    }

    @Test
    fun `completing a recurring task advances it and records the completion`() = runTest {
        val h = Harness()
        val task = h.quickAdd("ورزش هر شنبه و دوشنبه ساعت ۷ صبح")
        assertThat(task.due).isEqualTo(TaskDue.At(jalali("1405-07-04"), LocalTime.of(7, 0)))
        val undo = h.complete(task)
        val advanced = h.tasks.get(task.id)!!
        assertThat(advanced.isCompleted).isFalse()
        assertThat(advanced.due).isEqualTo(TaskDue.At(jalali("1405-07-06"), LocalTime.of(7, 0)))
        assertThat(h.tasks.completions).containsExactly(task.id to jalali("1405-07-04"))
        assertThat(h.scheduler.scheduled[task.id]?.second).isEqualTo(jalali("1405-07-06").atTime(7, 0))
        undo()
        assertThat(h.tasks.get(task.id)!!.due).isEqualTo(task.due)
        assertThat(h.tasks.completions).isEmpty()
    }

    @Test
    fun `an overdue recurring task skips missed occurrences`() = runTest {
        val h = Harness()
        val task = h.quickAdd("قرص هر روز ساعت ۹ شب")
        // Five days pass without completing it.
        h.clock.now = jalali("1405-07-07").atTime(12, 0)
        h.complete(task)
        // Completed the 07-02 occurrence late → next is today's (07-07), not 07-03.
        assertThat(h.tasks.get(task.id)!!.due?.date).isEqualTo(jalali("1405-07-07"))
    }

    @Test
    fun `last day of each Jalali month`() = runTest {
        val h = Harness()
        val task = h.quickAdd("اجاره آخر هر ماه")
        assertThat(task.due?.date).isEqualTo(jalali("1405-07-30"))
        h.clock.now = jalali("1405-07-30").atTime(9, 0)
        h.complete(task)
        assertThat(h.tasks.get(task.id)!!.due?.date).isEqualTo(jalali("1405-08-30"))
    }

    @Test
    fun `delete and undo`() = runTest {
        val h = Harness()
        val task = h.quickAdd("جلسه فردا ساعت ۱۰")
        val undo = h.delete(task)
        assertThat(h.tasks.deleted).contains(task.id)
        assertThat(h.scheduler.scheduled).isEmpty()
        undo()
        assertThat(h.tasks.deleted).isEmpty()
        assertThat(h.scheduler.scheduled).containsKey(task.id)
    }

    @Test
    fun `updating the time reschedules and clearing the date cancels`() = runTest {
        val h = Harness()
        val task = h.quickAdd("جلسه فردا ساعت ۱۰")
        h.update(task.copy(due = TaskDue.At(jalali("1405-07-05"), LocalTime.of(16, 30))))
        assertThat(h.scheduler.scheduled[task.id]?.second).isEqualTo(jalali("1405-07-05").atTime(16, 30))
        h.update(task.copy(due = null))
        assertThat(h.scheduler.scheduled).isEmpty()
        assertThat(h.reminders.reminders).isEmpty()
    }
}
