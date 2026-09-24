package ir.roozban.core.domain

import com.google.common.truth.Truth.assertThat
import ir.roozban.core.model.FocusSession
import ir.roozban.core.model.Habit
import ir.roozban.core.model.HabitLog
import ir.roozban.core.model.HabitSchedule
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.model.TimeEntry
import ir.roozban.core.model.TimeSource
import ir.roozban.core.testing.TEHRAN
import ir.roozban.core.testing.jalali
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant

class ReportsTest {
    private val sat = jalali("1405-07-04") // a Saturday
    private fun at(day: Int, h: Int, m: Int = 0): Instant = sat.plusDays(day.toLong()).atTime(h, m).atZone(TEHRAN).toInstant()
    private fun done(id: String, day: Int, h: Int, project: String? = null) = CompletionEvent(id, id, project, at(day, h), null)
    private fun tracked(task: String?, project: String?, from: Instant, to: Instant) =
        TrackedTime(TimeEntry("e$task$from", task, from, to, TimeSource.FOCUS), task, project)

    @Test
    fun `periods are Saturday weeks and Jalali months`() {
        assertThat(Period.week(sat.plusDays(3))).isEqualTo(Period(sat, sat.plusDays(6)))
        val mehr = Period.month(sat)
        assertThat(mehr.start).isEqualTo(jalali("1405-07-01"))
        assertThat(mehr.end).isEqualTo(jalali("1405-07-30"))
        assertThat(ReportRange.MONTH.previous(mehr).end).isEqualTo(jalali("1405-06-31"))
        assertThat(ReportRange.WEEK.next(Period.week(sat)).start).isEqualTo(sat.plusDays(7))
    }

    @Test
    fun `completions, tracked time and projects are totalled per day`() {
        val period = Period.week(sat)
        val report = ReportBuilder.build(
            period = period,
            completions = listOf(done("a", 0, 9, "p1"), done("b", 0, 20), done("c", 2, 11, "p1"), done("old", -1, 10)),
            sessions = emptyList(),
            tracked = listOf(
                tracked("a", "p1", at(0, 9), at(0, 9, 50)),
                tracked("x", "p2", at(2, 14), at(2, 16)),
            ),
            habits = emptyList(),
            habitLogs = emptyList(),
            zone = TEHRAN,
            today = sat.plusDays(6),
        )
        assertThat(report.completed).isEqualTo(3)
        assertThat(report.days.map { it.completed }).containsExactly(2, 0, 1, 0, 0, 0, 0).inOrder()
        assertThat(report.days[2].trackedMinutes).isEqualTo(120)
        assertThat(report.trackedMinutes).isEqualTo(170)
        assertThat(report.byProject.first()).isEqualTo(ProjectSlice("p2", 120, 0))
        assertThat(report.byProject).contains(ProjectSlice("p1", 50, 2))
        assertThat(report.byProject).contains(ProjectSlice(null, 0, 1))
        assertThat(report.topTasks.map { it.taskId }).containsExactly("x", "a").inOrder()
        assertThat(report.hourly[14]).isEqualTo(60)
        assertThat(report.hourly[15]).isEqualTo(60)
        assertThat(report.hourly[9]).isEqualTo(50)
        assertThat(report.peakHour).isEqualTo(14)
        assertThat(report.activeDays).isEqualTo(2)
    }

    @Test
    fun `time crossing midnight and the period edge is split and clipped`() {
        val period = Period(sat, sat)
        val r = ReportBuilder.build(
            period, emptyList(), emptyList(),
            listOf(tracked("t", null, at(-1, 23, 30), at(0, 0, 30)), tracked("u", null, at(0, 23, 30), at(1, 1, 0))),
            emptyList(), emptyList(), TEHRAN, sat,
        )
        assertThat(r.trackedMinutes).isEqualTo(60)
        assertThat(r.hourly[0]).isEqualTo(30)
        assertThat(r.hourly[23]).isEqualTo(30)
    }

    @Test
    fun `focus sessions are counted by their end`() {
        val s = listOf(
            FocusSession("1", null, at(0, 9), at(0, 9, 25), 25, 25 * 60, true),
            FocusSession("2", null, at(1, 9), at(1, 9, 10), 25, 10 * 60, false),
            FocusSession("3", null, at(7, 9), at(7, 9, 25), 25, 25 * 60, true),
        )
        val r = ReportBuilder.build(Period.week(sat), emptyList(), s, emptyList(), emptyList(), emptyList(), TEHRAN, sat)
        assertThat(r.focusSessions).isEqualTo(2)
        assertThat(r.completedSessions).isEqualTo(1)
        assertThat(r.focusMinutes).isEqualTo(35)
    }

    @Test
    fun `habit rate counts scheduled days up to today`() {
        val daily = Habit("d", "d", startDate = sat, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val weekly = Habit("w", "w", schedule = HabitSchedule.TimesPerWeek(7), startDate = sat, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val logs = listOf(HabitLog("d", sat, 1, Instant.EPOCH), HabitLog("d", sat.plusDays(1), 1, Instant.EPOCH), HabitLog("w", sat, 1, Instant.EPOCH))
        // Today is Monday, not yet done: 2 of 2 daily check-ins, 1 of 3 weekly ones.
        val rate = ReportBuilder.habitRate(Period.week(sat), listOf(daily, weekly), logs, sat.plusDays(2))
        assertThat(rate).isWithin(0.001f).of(3f / 5f)
        assertThat(ReportBuilder.habitRate(Period.week(sat), emptyList(), emptyList(), sat)).isNull()
    }

    @Test
    fun `daily review gathers leftovers, tomorrow and habits`() = runTest {
        val today = sat.plusDays(2)
        fun task(id: String, day: Int?) = Task(id, id, due = day?.let { TaskDue.AllDay(sat.plusDays(it.toLong())) }, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val habit = Habit("h", "h", startDate = sat, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val review = ReviewBuilder.daily(
            date = today,
            openTasks = listOf(task("late", 0), task("now", 2), task("tomorrow", 3), task("none", null)),
            completions = listOf(done("x", 2, 10), done("y", 1, 10)),
            sessions = listOf(FocusSession("1", null, at(2, 9), at(2, 9, 25), 25, 25 * 60, true)),
            habits = listOf(habit),
            logs = emptyList(),
            zone = TEHRAN,
        )
        assertThat(review.leftover.map { it.id }).containsExactly("late", "now").inOrder()
        assertThat(review.tomorrow.map { it.id }).containsExactly("tomorrow")
        assertThat(review.completed.map { it.taskId }).containsExactly("x")
        assertThat(review.focusMinutes).isEqualTo(25)
        assertThat(review.habits.single().due).isTrue()
    }

    @Test
    fun `weekly review compares weeks and looks ahead`() {
        fun task(id: String, day: Int) = Task(id, id, due = TaskDue.At(sat.plusDays(day.toLong()), java.time.LocalTime.NOON), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val week = Period.week(sat)
        val empty = ReportBuilder.build(week, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), TEHRAN, sat)
        val habit = Habit("h", "h", schedule = HabitSchedule.TimesPerWeek(3), startDate = sat, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val review = ReviewBuilder.weekly(
            report = empty,
            previous = empty.copy(period = ReportRange.WEEK.previous(week)),
            openTasks = listOf(task("late", 1), task("next", 8), task("next2", 8)),
            habits = listOf(habit),
            logs = listOf(HabitLog("h", sat, 1, Instant.EPOCH)),
            today = sat.plusDays(6),
        )
        assertThat(review.overdue.map { it.id }).containsExactly("late")
        assertThat(review.nextWeek[1]).isEqualTo(sat.plusDays(8) to 2)
        assertThat(review.habits.single()).isEqualTo(HabitWeek(habit, 1, 3))
    }
}
