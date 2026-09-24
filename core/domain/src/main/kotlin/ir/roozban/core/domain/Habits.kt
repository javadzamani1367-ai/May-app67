package ir.roozban.core.domain

import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.model.Habit
import ir.roozban.core.model.HabitLog
import ir.roozban.core.model.HabitSchedule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

enum class HabitDayStatus {
    DONE,

    /** Some progress but below the day's target (past days). */
    PARTIAL,

    /** Missed, but a freeze kept the streak alive. */
    FROZEN,
    MISSED,

    /** Today, not done yet: does not break the streak. */
    PENDING,

    /** Not a scheduled day. */
    OFF,
}

enum class StreakUnit { DAY, WEEK }

data class HabitStats(
    val current: Int,
    val best: Int,
    val unit: StreakUnit,
    /** Freezes in reserve; one is earned for every [HabitStreaks.DAYS_PER_FREEZE] days (or weeks). */
    val freezes: Int,
    /** Status of every day from the habit's start through today. */
    val days: Map<LocalDate, HabitDayStatus>,
    /** Share of scheduled days done in the last 30 days, 0..1. */
    val rate30: Float,
    val todayCount: Int,
    /** Days done in the current week (for «N بار در هفته»). */
    val doneThisWeek: Int,
)

/**
 * Streaks with automatic freezes, computed from the logs alone (so backups and restores can never
 * disagree with stored counters).
 *
 * - Daily / specific days: the streak counts consecutive scheduled days done. A missed day uses a
 *   freeze if one is in reserve, otherwise the streak restarts. Today not being done yet is fine.
 * - N times a week: the streak counts successful weeks; a failed week uses a freeze.
 */
object HabitStreaks {
    const val MAX_FREEZES = 2
    const val DAYS_PER_FREEZE = 7
    const val WEEKS_PER_FREEZE = 4

    fun compute(habit: Habit, logs: Map<LocalDate, Int>, today: LocalDate): HabitStats {
        val target = habit.targetPerDay.coerceAtLeast(1)
        fun count(d: LocalDate) = logs[d] ?: 0
        fun done(d: LocalDate) = count(d) >= target
        val weekStart = PersianWeek.startOfWeek(today)
        val doneThisWeek = generateSequence(weekStart) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.count { done(it) }
        val stats = when (val schedule = habit.schedule) {
            is HabitSchedule.TimesPerWeek -> weekly(habit.startDate, schedule.times, ::done, today)
            else -> daily(habit.startDate, schedule, ::count, target, today)
        }
        return stats.copy(todayCount = count(today), doneThisWeek = doneThisWeek, rate30 = rate30(habit, ::done, today))
    }

    private fun daily(
        start: LocalDate,
        schedule: HabitSchedule,
        count: (LocalDate) -> Int,
        target: Int,
        today: LocalDate,
    ): HabitStats {
        val days = LinkedHashMap<LocalDate, HabitDayStatus>()
        var streak = 0
        var best = 0
        var freezes = 0
        var run = 0 // done days since the last freeze was earned or used
        var d = start
        while (!d.isAfter(today)) {
            val c = count(d)
            days[d] = when {
                !schedule.isScheduledOn(d) -> HabitDayStatus.OFF
                c >= target -> {
                    streak++
                    run++
                    if (run % DAYS_PER_FREEZE == 0) freezes = minOf(freezes + 1, MAX_FREEZES)
                    HabitDayStatus.DONE
                }
                d == today -> HabitDayStatus.PENDING
                freezes > 0 -> {
                    freezes--
                    run = 0
                    HabitDayStatus.FROZEN
                }
                else -> {
                    streak = 0
                    run = 0
                    if (c > 0) HabitDayStatus.PARTIAL else HabitDayStatus.MISSED
                }
            }
            best = maxOf(best, streak)
            d = d.plusDays(1)
        }
        return HabitStats(streak, best, StreakUnit.DAY, freezes, days, 0f, 0, 0)
    }

    private fun weekly(start: LocalDate, times: Int, done: (LocalDate) -> Boolean, today: LocalDate): HabitStats {
        val days = LinkedHashMap<LocalDate, HabitDayStatus>()
        var d = start
        while (!d.isAfter(today)) {
            days[d] = when {
                done(d) -> HabitDayStatus.DONE
                d == today -> HabitDayStatus.PENDING
                else -> HabitDayStatus.OFF
            }
            d = d.plusDays(1)
        }
        var streak = 0
        var best = 0
        var freezes = 0
        var run = 0
        var week = PersianWeek.startOfWeek(start)
        val currentWeek = PersianWeek.startOfWeek(today)
        while (!week.isAfter(currentWeek)) {
            val first = maxOf(week, start)
            val last = week.plusDays(6)
            // The first week may be short: it only asks for as many days as it has.
            val required = minOf(times, (last.toEpochDay() - first.toEpochDay() + 1).toInt())
            val doneDays = generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(minOf(last, today)) }.count(done)
            when {
                doneDays >= required -> {
                    streak++
                    run++
                    if (run % WEEKS_PER_FREEZE == 0) freezes = minOf(freezes + 1, MAX_FREEZES)
                }
                week == currentWeek -> Unit // still in progress
                freezes > 0 -> {
                    freezes--
                    run = 0
                }
                else -> {
                    streak = 0
                    run = 0
                }
            }
            best = maxOf(best, streak)
            week = week.plusWeeks(1)
        }
        return HabitStats(streak, best, StreakUnit.WEEK, freezes, days, 0f, 0, 0)
    }

    private fun rate30(habit: Habit, done: (LocalDate) -> Boolean, today: LocalDate): Float {
        val from = maxOf(habit.startDate, today.minusDays(29))
        if (from.isAfter(today)) return 0f
        val window = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
        return when (val s = habit.schedule) {
            is HabitSchedule.TimesPerWeek -> {
                val expected = s.times * window.size / 7f
                if (expected <= 0f) 0f else (window.count(done) / expected).coerceAtMost(1f)
            }
            else -> {
                // Today only counts once it is done.
                val scheduled = window.filter { s.isScheduledOn(it) && (it != today || done(it)) }
                if (scheduled.isEmpty()) 0f else scheduled.count(done).toFloat() / scheduled.size
            }
        }
    }
}

class HabitUseCases @Inject constructor(
    private val habits: HabitRepository,
    private val routines: RoutineReminders,
    private val clock: Clock,
) {
    suspend fun create(
        name: String,
        color: Int,
        schedule: HabitSchedule,
        targetPerDay: Int,
        reminderTime: LocalTime?,
    ): Habit? {
        if (name.isBlank()) return null
        val now = Instant.now(clock)
        val sortOrder = (habits.all().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val habit = Habit(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            color = color,
            schedule = schedule,
            targetPerDay = targetPerDay.coerceIn(1, MAX_TARGET),
            reminderTime = reminderTime,
            startDate = LocalDate.now(clock),
            sortOrder = sortOrder,
            createdAt = now,
            updatedAt = now,
        )
        habits.upsert(habit)
        routines.syncHabit(habit)
        return habit
    }

    suspend fun update(habit: Habit) {
        val updated = habit.copy(
            name = habit.name.trim(),
            targetPerDay = habit.targetPerDay.coerceIn(1, MAX_TARGET),
            updatedAt = Instant.now(clock),
        )
        habits.upsert(updated)
        routines.syncHabit(updated)
    }

    suspend fun setArchived(habit: Habit, archived: Boolean) = update(habit.copy(archived = archived))

    suspend fun delete(habit: Habit) {
        habits.delete(habit.id)
        routines.cancelHabit(habit.id)
    }

    /** A tap on a day adds one, until the target; one more tap clears the day. Returns the new count. */
    suspend fun tap(habit: Habit, date: LocalDate): Int {
        val current = habits.log(habit.id, date)?.count ?: 0
        val next = if (current >= habit.targetPerDay) 0 else current + 1
        return setCount(habit, date, next)
    }

    /**
     * Future days cannot be logged. Logging a day before the habit's start moves the start back
     * (up to [MAX_BACKFILL_DAYS]), for habits added a few days late. Returns the stored count.
     */
    suspend fun setCount(habit: Habit, date: LocalDate, count: Int): Int {
        val today = LocalDate.now(clock)
        if (date.isAfter(today) || date.isBefore(today.minusDays(MAX_BACKFILL_DAYS))) {
            return habits.log(habit.id, date)?.count ?: 0
        }
        val value = count.coerceIn(0, habit.targetPerDay)
        if (value > 0 && date.isBefore(habit.startDate)) {
            habits.upsert(habit.copy(startDate = date, updatedAt = Instant.now(clock)))
        }
        habits.setLog(HabitLog(habit.id, date, value, Instant.now(clock)))
        // Completing today silences today's reminder.
        routines.syncHabit(habits.get(habit.id) ?: habit)
        return value
    }

    companion object {
        const val MAX_TARGET = 20

        const val MAX_BACKFILL_DAYS = 60L
    }
}
