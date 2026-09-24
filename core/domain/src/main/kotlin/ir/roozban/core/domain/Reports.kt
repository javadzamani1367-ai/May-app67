package ir.roozban.core.domain

import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.model.FocusSession
import ir.roozban.core.model.Habit
import ir.roozban.core.model.HabitLog
import ir.roozban.core.model.HabitSchedule
import ir.roozban.core.model.Task
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** An inclusive range of days. */
data class Period(val start: LocalDate, val end: LocalDate) {
    val days: List<LocalDate> get() = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
    val length: Int get() = (end.toEpochDay() - start.toEpochDay() + 1).toInt()

    fun startInstant(zone: ZoneId): Instant = start.atStartOfDay(zone).toInstant()
    fun endInstant(zone: ZoneId): Instant = end.plusDays(1).atStartOfDay(zone).toInstant()

    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

    companion object {
        /** The Saturday-to-Friday week containing [date]. */
        fun week(date: LocalDate): Period = PersianWeek.startOfWeek(date).let { Period(it, it.plusDays(6)) }

        /** The Jalali month containing [date]. */
        fun month(date: LocalDate): Period {
            val j = date.toJalali()
            val first = JalaliDate.of(j.year, j.month, 1)
            return Period(first.toLocalDate(), first.lastDayOfMonth().toLocalDate())
        }
    }
}

enum class ReportRange { WEEK, MONTH;

    fun periodOf(date: LocalDate): Period = if (this == WEEK) Period.week(date) else Period.month(date)

    fun previous(p: Period): Period = periodOf(p.start.minusDays(1))

    fun next(p: Period): Period = periodOf(p.end.plusDays(1))
}

data class DayStat(val date: LocalDate, val completed: Int, val trackedMinutes: Int)

/** Time and completions for one project; [projectId] null = tasks without a project. */
data class ProjectSlice(val projectId: String?, val minutes: Int, val completed: Int)

data class TaskTime(val taskId: String?, val title: String?, val minutes: Int)

data class Report(
    val period: Period,
    val days: List<DayStat>,
    val completed: Int,
    /** Time recorded on tasks (focus and manual). */
    val trackedMinutes: Int,
    val focusMinutes: Int,
    val focusSessions: Int,
    /** Focus periods that ran to the end. */
    val completedSessions: Int,
    val byProject: List<ProjectSlice>,
    val topTasks: List<TaskTime>,
    /** Tracked minutes by hour of day (0..23). */
    val hourly: List<Int>,
    /** Share of habit check-ins done, null without habits. */
    val habitRate: Float?,
) {
    /** Most productive hour, if there is enough data. */
    val peakHour: Int? get() = hourly.withIndex().maxByOrNull { it.value }?.takeIf { it.value >= 15 }?.index
    val activeDays: Int get() = days.count { it.completed > 0 || it.trackedMinutes > 0 }
}

object ReportBuilder {
    fun build(
        period: Period,
        completions: List<CompletionEvent>,
        sessions: List<FocusSession>,
        tracked: List<TrackedTime>,
        habits: List<Habit>,
        habitLogs: List<HabitLog>,
        zone: ZoneId,
        today: LocalDate,
    ): Report {
        val from = period.startInstant(zone)
        val until = period.endInstant(zone)
        val inRange = completions.filter { !it.at.isBefore(from) && it.at.isBefore(until) }
        val completedByDay = inRange.groupingBy { it.at.atZone(zone).toLocalDate() }.eachCount()

        // Tracked time, clipped to the period and split per day and per hour.
        val minutesByDay = HashMap<LocalDate, Long>()
        val hourly = LongArray(24)
        val secondsByTask = LinkedHashMap<String?, Long>()
        val titles = HashMap<String?, String?>()
        val secondsByProject = HashMap<String?, Long>()
        for (t in tracked) {
            val start = maxOf(t.entry.start, from)
            val end = minOf(t.entry.end, until)
            if (!end.isAfter(start)) continue
            var cursor = LocalDateTime.ofInstant(start, zone)
            val stop = LocalDateTime.ofInstant(end, zone)
            while (cursor.isBefore(stop)) {
                val nextHour = cursor.withMinute(0).withSecond(0).withNano(0).plusHours(1)
                val sliceEnd = minOf(nextHour, stop)
                val secs = Duration.between(cursor, sliceEnd).seconds
                hourly[cursor.hour] += secs
                minutesByDay.merge(cursor.toLocalDate(), secs, Long::plus)
                cursor = sliceEnd
            }
            val secs = Duration.between(start, end).seconds
            secondsByTask.merge(t.entry.taskId, secs, Long::plus)
            titles[t.entry.taskId] = t.taskTitle
            secondsByProject.merge(t.projectId, secs, Long::plus)
        }

        val completedByProject = inRange.groupingBy { it.projectId }.eachCount()
        val projectIds = secondsByProject.keys + completedByProject.keys
        val byProject = projectIds.map { id ->
            ProjectSlice(id, minutes(secondsByProject[id] ?: 0), completedByProject[id] ?: 0)
        }.sortedWith(compareByDescending<ProjectSlice> { it.minutes }.thenByDescending { it.completed })

        val focus = sessions.filter { !it.endedAt.isBefore(from) && it.endedAt.isBefore(until) }

        return Report(
            period = period,
            days = period.days.map { d -> DayStat(d, completedByDay[d] ?: 0, minutes(minutesByDay[d] ?: 0)) },
            completed = inRange.size,
            trackedMinutes = minutes(secondsByTask.values.sum()),
            focusMinutes = minutes(focus.sumOf { it.focusedSeconds }),
            focusSessions = focus.size,
            completedSessions = focus.count { it.completed },
            byProject = byProject,
            topTasks = secondsByTask.entries.sortedByDescending { it.value }.take(TOP_TASKS)
                .map { (id, secs) -> TaskTime(id, titles[id], minutes(secs)) }
                .filter { it.minutes > 0 },
            hourly = hourly.map { minutes(it) },
            habitRate = habitRate(period, habits, habitLogs, today),
        )
    }

    /** Done check-ins over expected check-ins, for the part of the period up to today. */
    fun habitRate(period: Period, habits: List<Habit>, logs: List<HabitLog>, today: LocalDate): Float? {
        val byHabit = logs.groupBy { it.habitId }
        var expected = 0f
        var done = 0f
        for (h in habits) {
            if (h.archived) continue
            val from = maxOf(period.start, h.startDate)
            val to = minOf(period.end, today)
            if (from.isAfter(to)) continue
            val doneDays = byHabit[h.id].orEmpty()
                .filter { it.count >= h.targetPerDay && it.date in Period(from, to) }
                .map { it.date }.toSet()
            val days = Period(from, to).days
            when (val s = h.schedule) {
                is HabitSchedule.TimesPerWeek -> {
                    val e = s.times * days.size / 7f
                    expected += e
                    done += minOf(doneDays.size.toFloat(), e)
                }
                else -> {
                    // Today only counts once it is done.
                    val scheduled = days.filter { s.isScheduledOn(it) && (it != today || it in doneDays) }
                    expected += scheduled.size
                    done += scheduled.count { it in doneDays }
                }
            }
        }
        return if (expected <= 0f) null else (done / expected).coerceIn(0f, 1f)
    }

    private fun minutes(seconds: Long): Int = ((seconds + 30) / 60).toInt()

    const val TOP_TASKS = 5
}

/** What the evening review looks at. */
data class DailyReview(
    val date: LocalDate,
    val completed: List<CompletionEvent>,
    /** Open tasks due today or earlier: to finish, move or drop. */
    val leftover: List<Task>,
    val tomorrow: List<Task>,
    val focusMinutes: Int,
    val habits: List<HabitDay>,
)

data class HabitDay(val habit: Habit, val count: Int, val due: Boolean)

data class WeeklyReview(
    val report: Report,
    val previous: Report,
    val overdue: List<Task>,
    /** Open tasks per day of the coming week. */
    val nextWeek: List<Pair<LocalDate, Int>>,
    val habits: List<HabitWeek>,
)

data class HabitWeek(val habit: Habit, val done: Int, val expected: Int)

object ReviewBuilder {
    fun daily(
        date: LocalDate,
        openTasks: List<Task>,
        completions: List<CompletionEvent>,
        sessions: List<FocusSession>,
        habits: List<Habit>,
        logs: List<HabitLog>,
        zone: ZoneId,
    ): DailyReview {
        val done = completions.filter { it.at.atZone(zone).toLocalDate() == date }.sortedBy { it.at }
        val leftover = openTasks.filter { t -> t.due?.date?.let { !it.isAfter(date) } == true }
            .sortedWith(compareBy({ it.due?.date }, { it.createdAt }))
        val tomorrow = openTasks.filter { it.due?.date == date.plusDays(1) }
        val focus = sessions.filter { it.endedAt.atZone(zone).toLocalDate() == date }.sumOf { it.focusedSeconds }
        val counts = logs.filter { it.date == date }.associate { it.habitId to it.count }
        val habitDays = habits.filter { !it.archived && !date.isBefore(it.startDate) && it.schedule.isScheduledOn(date) }
            .map { h ->
                val c = counts[h.id] ?: 0
                HabitDay(h, c, due = c < h.targetPerDay)
            }
        return DailyReview(date, done, leftover, tomorrow, ((focus + 30) / 60).toInt(), habitDays)
    }

    fun weekly(
        report: Report,
        previous: Report,
        openTasks: List<Task>,
        habits: List<Habit>,
        logs: List<HabitLog>,
        today: LocalDate,
    ): WeeklyReview {
        val next = Period.week(report.period.end.plusDays(1))
        val overdue = openTasks.filter { t -> t.due?.date?.isBefore(today) == true }.sortedBy { it.due?.date }
        val load = next.days.map { d -> d to openTasks.count { it.due?.date == d } }
        val week = report.period
        val habitWeeks = habits.filter { !it.archived && !it.startDate.isAfter(week.end) }.map { h ->
            val from = maxOf(week.start, h.startDate)
            val to = minOf(week.end, today)
            val days = if (from.isAfter(to)) emptyList() else Period(from, to).days
            val done = logs.count { it.habitId == h.id && it.count >= h.targetPerDay && it.date in week }
            val expected = when (val s = h.schedule) {
                is HabitSchedule.TimesPerWeek -> minOf(s.times, Period(from, week.end).length.coerceAtLeast(0))
                else -> days.count { s.isScheduledOn(it) }
            }
            HabitWeek(h, done, expected)
        }
        return WeeklyReview(report, previous, overdue, load, habitWeeks)
    }
}
