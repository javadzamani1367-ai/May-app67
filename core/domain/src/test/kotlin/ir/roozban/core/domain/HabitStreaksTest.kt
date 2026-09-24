package ir.roozban.core.domain

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.Habit
import ir.roozban.core.model.HabitSchedule
import ir.roozban.core.testing.jalali
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

class HabitStreaksTest {
    // ۱ مهر ۱۴۰۵ is a Wednesday; ۴ مهر is Saturday (start of a week).
    private val start = jalali("1405-07-04")

    private fun habit(schedule: HabitSchedule = HabitSchedule.Daily, target: Int = 1, from: LocalDate = start) =
        Habit("h", "ورزش", schedule = schedule, targetPerDay = target, startDate = from, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    /** Logs from a pattern string starting at [start]: x = done, . = not done, p = partial (1). */
    private fun logs(pattern: String, full: Int = 1): Map<LocalDate, Int> =
        pattern.withIndex().mapNotNull { (i, c) ->
            when (c) {
                'x' -> start.plusDays(i.toLong()) to full
                'p' -> start.plusDays(i.toLong()) to 1
                else -> null
            }
        }.toMap()

    private fun day(i: Int) = start.plusDays(i.toLong())

    @Test
    fun `consecutive days build the streak and today pending does not break it`() {
        val s = HabitStreaks.compute(habit(), logs("xxx"), today = day(3))
        assertThat(s.current).isEqualTo(3)
        assertThat(s.best).isEqualTo(3)
        assertThat(s.days[day(3)]).isEqualTo(HabitDayStatus.PENDING)
        assertThat(s.unit).isEqualTo(StreakUnit.DAY)
    }

    @Test
    fun `a missed day without freezes restarts the streak`() {
        val s = HabitStreaks.compute(habit(), logs("xxx.xx"), today = day(5))
        assertThat(s.current).isEqualTo(2)
        assertThat(s.best).isEqualTo(3)
        assertThat(s.days[day(3)]).isEqualTo(HabitDayStatus.MISSED)
    }

    @Test
    fun `seven days earn a freeze that covers one missed day`() {
        val s = HabitStreaks.compute(habit(), logs("xxxxxxx.xx"), today = day(10))
        assertThat(s.days[day(7)]).isEqualTo(HabitDayStatus.FROZEN)
        assertThat(s.current).isEqualTo(9)
        assertThat(s.freezes).isEqualTo(0)
    }

    @Test
    fun `freezes are capped`() {
        val s = HabitStreaks.compute(habit(), logs("x".repeat(35)), today = day(35))
        assertThat(s.freezes).isEqualTo(HabitStreaks.MAX_FREEZES)
    }

    @Test
    fun `two misses with one freeze break the streak`() {
        val s = HabitStreaks.compute(habit(), logs("xxxxxxx..x"), today = day(10))
        assertThat(s.days[day(7)]).isEqualTo(HabitDayStatus.FROZEN)
        assertThat(s.days[day(8)]).isEqualTo(HabitDayStatus.MISSED)
        assertThat(s.current).isEqualTo(1)
        assertThat(s.best).isEqualTo(7)
    }

    @Test
    fun `days off do not break a specific-days habit`() {
        // Saturday, Monday, Wednesday.
        val h = habit(HabitSchedule.Weekdays(setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        val s = HabitStreaks.compute(h, logs("x.x.x..x"), today = day(8))
        assertThat(s.current).isEqualTo(4)
        assertThat(s.days[day(1)]).isEqualTo(HabitDayStatus.OFF)
    }

    @Test
    fun `a count below target is partial and does not count`() {
        val h = habit(target = 3)
        val s = HabitStreaks.compute(h, logs("xxp", full = 3), today = day(3))
        assertThat(s.days[day(2)]).isEqualTo(HabitDayStatus.PARTIAL)
        assertThat(s.current).isEqualTo(0)
    }

    @Test
    fun `weekly habits count successful weeks`() {
        val h = habit(HabitSchedule.TimesPerWeek(3))
        // Week 1: 3 days, week 2: 3 days, week 3 (current, day 16): 1 so far.
        val s = HabitStreaks.compute(h, logs("x.x.x.." + "xx..x.." + ".x"), today = day(15))
        assertThat(s.unit).isEqualTo(StreakUnit.WEEK)
        assertThat(s.current).isEqualTo(2)
        assertThat(s.doneThisWeek).isEqualTo(1)
    }

    @Test
    fun `a failed week breaks a weekly streak, and the first short week asks less`() {
        // Start on Thursday (day 5 of the week): only 2 days left, so 2 are enough.
        val thursday = start.plusDays(5)
        val h = habit(HabitSchedule.TimesPerWeek(3), from = thursday)
        val l = mapOf(thursday to 1, thursday.plusDays(1) to 1, thursday.plusDays(2) to 1)
        val s = HabitStreaks.compute(h, l, today = thursday.plusDays(10))
        assertThat(s.best).isEqualTo(1)
        assertThat(s.current).isEqualTo(0)
    }

    @Test
    fun `rate over the last 30 days ignores an unfinished today`() {
        val s = HabitStreaks.compute(habit(), logs("xx.x"), today = day(4))
        assertThat(s.rate30).isWithin(0.001f).of(0.75f)
    }

    @Test
    fun `schedule encoding round-trips`() {
        val all = listOf(
            HabitSchedule.Daily,
            HabitSchedule.Weekdays(setOf(DayOfWeek.SATURDAY, DayOfWeek.TUESDAY)),
            HabitSchedule.TimesPerWeek(4),
        )
        all.forEach { assertThat(HabitSchedule.decode(it.encode())).isEqualTo(it) }
        assertThat(HabitSchedule.decode("W:")).isEqualTo(HabitSchedule.Daily)
        assertThat(HabitSchedule.decode("N:99")).isEqualTo(HabitSchedule.TimesPerWeek(7))
    }
}
