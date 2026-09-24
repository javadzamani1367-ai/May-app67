package ir.roozban.core.domain

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.HabitSchedule
import ir.roozban.core.testing.FakeHabitRepository
import ir.roozban.core.testing.FakeRoutineAlarms
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime

class HabitUseCasesTest {
    private class H {
        val h = Harness() // پنجشنبه ۲ مهر ۱۴۰۵، ۱۰:۰۰
        val habits = FakeHabitRepository()
        val alarms = FakeRoutineAlarms()
        val routines = RoutineReminders(habits, h.settings, alarms, h.clock)
        val use = HabitUseCases(habits, routines, h.clock)
        val today get() = h.clock.now.toLocalDate()
    }

    @Test
    fun `creating a habit with a reminder schedules the next one`() = runTest {
        val t = H()
        val later = t.use.create("پیاده‌روی", 0, HabitSchedule.Daily, 1, LocalTime.of(18, 0))!!
        assertThat(t.alarms.habits[later.id]).isEqualTo(t.today.atTime(18, 0))
        val early = t.use.create("مدیتیشن", 0, HabitSchedule.Daily, 1, LocalTime.of(7, 0))!!
        assertThat(t.alarms.habits[early.id]).isEqualTo(t.today.plusDays(1).atTime(7, 0))
        assertThat(t.use.create("  ", 0, HabitSchedule.Daily, 1, null)).isNull()
    }

    @Test
    fun `doing today's habit moves its reminder to the next day`() = runTest {
        val t = H()
        val habit = t.use.create("آب", 0, HabitSchedule.Daily, 1, LocalTime.of(20, 0))!!
        t.use.tap(habit, t.today)
        assertThat(t.alarms.habits[habit.id]).isEqualTo(t.today.plusDays(1).atTime(20, 0))
        t.use.tap(habit, t.today) // undo
        assertThat(t.alarms.habits[habit.id]).isEqualTo(t.today.atTime(20, 0))
    }

    @Test
    fun `reminders skip days off`() = runTest {
        val t = H()
        // Today is Thursday; next Saturday is two days away.
        val habit = t.use.create("باشگاه", 0, HabitSchedule.Weekdays(setOf(DayOfWeek.SATURDAY)), 1, LocalTime.of(8, 0))!!
        assertThat(t.alarms.habits[habit.id]).isEqualTo(t.today.plusDays(2).atTime(8, 0))
    }

    @Test
    fun `weekly habits stop reminding once the week's goal is met`() = runTest {
        val t = H()
        val habit = t.use.create("دویدن", 0, HabitSchedule.TimesPerWeek(1), 1, LocalTime.of(19, 0))!!
        t.use.tap(habit, t.today)
        // Thursday done: Friday is in the same week, so the next reminder is Saturday.
        assertThat(t.alarms.habits[habit.id]).isEqualTo(t.today.plusDays(2).atTime(19, 0))
    }

    @Test
    fun `tapping counts up to the target and then clears`() = runTest {
        val t = H()
        val habit = t.use.create("لیوان آب", 0, HabitSchedule.Daily, 3, null)!!
        assertThat((1..4).map { t.use.tap(habit, t.today) }).containsExactly(1, 2, 3, 0).inOrder()
        assertThat(t.habits.log(habit.id, t.today)).isNull()
    }

    @Test
    fun `future days cannot be logged, and logging before the start moves it back`() = runTest {
        val t = H()
        val habit = t.use.create("کتاب", 0, HabitSchedule.Daily, 1, null)!!
        assertThat(t.use.tap(habit, t.today.plusDays(1))).isEqualTo(0)
        t.use.tap(habit, t.today.minusDays(3))
        assertThat(t.habits.get(habit.id)!!.startDate).isEqualTo(t.today.minusDays(3))
    }

    @Test
    fun `an alarm reminds only when the habit is still due`() = runTest {
        val t = H()
        val habit = t.use.create("ورزش", 0, HabitSchedule.Daily, 1, LocalTime.of(10, 0))!!
        assertThat(t.routines.onHabitAlarm(habit.id)).isNotNull()
        assertThat(t.alarms.habits[habit.id]).isEqualTo(t.today.plusDays(1).atTime(10, 0))
        t.use.tap(habit, t.today)
        assertThat(t.routines.onHabitAlarm(habit.id)).isNull()
    }

    @Test
    fun `archiving or deleting cancels the reminder`() = runTest {
        val t = H()
        val a = t.use.create("الف", 0, HabitSchedule.Daily, 1, LocalTime.of(21, 0))!!
        val b = t.use.create("ب", 0, HabitSchedule.Daily, 1, LocalTime.of(21, 0))!!
        t.use.setArchived(a, true)
        t.use.delete(b)
        assertThat(t.alarms.habits).isEmpty()
        assertThat(t.habits.all().map { it.id }).containsExactly(a.id)
    }

    @Test
    fun `review reminders follow the settings`() = runTest {
        val t = H()
        t.h.settings.update { it.copy(dailyReviewTime = LocalTime.of(21, 30), weeklyReviewTime = LocalTime.of(18, 0), weeklyReviewDay = DayOfWeek.FRIDAY) }
        t.routines.syncReviews()
        assertThat(t.alarms.reviews[ReviewKind.DAILY]).isEqualTo(t.today.atTime(21, 30))
        assertThat(t.alarms.reviews[ReviewKind.WEEKLY]).isEqualTo(t.today.plusDays(1).atTime(18, 0))
        t.h.clock.now = t.today.atTime(21, 30)
        assertThat(t.routines.onReviewAlarm(ReviewKind.DAILY)).isTrue()
        assertThat(t.alarms.reviews[ReviewKind.DAILY]).isEqualTo(t.today.plusDays(1).atTime(21, 30))
        t.h.settings.update { it.copy(dailyReviewTime = null) }
        t.routines.syncReviews()
        assertThat(t.alarms.reviews).doesNotContainKey(ReviewKind.DAILY)
    }
}
