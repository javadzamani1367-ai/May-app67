package ir.roozban.core.domain

import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.model.Habit
import ir.roozban.core.model.HabitSchedule
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one alarm per habit (its next reminder) and one per review kind. Each alarm re-arms the
 * next one when it fires, so only the nearest reminder is ever registered.
 */
@Singleton
class RoutineReminders @Inject constructor(
    private val habits: HabitRepository,
    private val settings: SettingsRepository,
    private val alarms: RoutineAlarms,
    private val clock: Clock,
) {
    suspend fun syncAll() {
        habits.all().forEach { syncHabit(it) }
        syncReviews()
    }

    suspend fun syncHabit(habit: Habit, after: LocalDateTime = LocalDateTime.now(clock)) {
        val next = nextHabitReminder(habit, after)
        if (next == null) alarms.cancelHabit(habit.id) else alarms.scheduleHabit(habit.id, millis(next))
    }

    fun cancelHabit(habitId: String) = alarms.cancelHabit(habitId)

    suspend fun syncReviews(after: LocalDateTime = LocalDateTime.now(clock)) {
        val s = settings.current()
        ReviewKind.entries.forEach { kind ->
            val next = nextReview(kind, s, after)
            if (next == null) alarms.cancelReview(kind) else alarms.scheduleReview(kind, millis(next))
        }
    }

    /**
     * A habit alarm fired. Returns the habit when the user should be reminded (today is a
     * scheduled day and it is not done yet), and arms the next reminder either way.
     */
    suspend fun onHabitAlarm(habitId: String): Habit? {
        val habit = habits.get(habitId) ?: return null.also { alarms.cancelHabit(habitId) }
        val now = LocalDateTime.now(clock)
        val remind = isDue(habit, now.toLocalDate())
        syncHabit(habit, after = now.plusMinutes(1))
        return habit.takeIf { remind }
    }

    /** A review alarm fired; arms the next one. Returns whether to notify. */
    suspend fun onReviewAlarm(kind: ReviewKind): Boolean {
        val s = settings.current()
        val enabled = nextReview(kind, s, LocalDateTime.now(clock)) != null
        syncReviews(after = LocalDateTime.now(clock).plusMinutes(1))
        return enabled
    }

    internal suspend fun nextHabitReminder(habit: Habit, after: LocalDateTime): LocalDateTime? {
        val time = habit.reminderTime ?: return null
        if (habit.archived) return null
        for (i in 0L..8L) {
            val date = after.toLocalDate().plusDays(i)
            val at = date.atTime(time)
            if (!at.isAfter(after) || date.isBefore(habit.startDate)) continue
            if (isDue(habit, date)) return at
        }
        return null
    }

    /** Whether the habit still needs doing on [date]. */
    private suspend fun isDue(habit: Habit, date: LocalDate): Boolean {
        if (habit.archived || date.isBefore(habit.startDate)) return false
        if (!habit.schedule.isScheduledOn(date)) return false
        val today = LocalDate.now(clock)
        // Only today's (and past) progress is known; future days are always due.
        if (date.isAfter(today)) {
            val schedule = habit.schedule
            if (schedule is HabitSchedule.TimesPerWeek && PersianWeek.startOfWeek(date) == PersianWeek.startOfWeek(today)) {
                return weekDone(habit, today) < schedule.times
            }
            return true
        }
        val count = habits.log(habit.id, date)?.count ?: 0
        if (count >= habit.targetPerDay) return false
        val schedule = habit.schedule
        return schedule !is HabitSchedule.TimesPerWeek || weekDone(habit, date) < schedule.times
    }

    private suspend fun weekDone(habit: Habit, date: LocalDate): Int {
        val start = PersianWeek.startOfWeek(date)
        return habits.observeLogs(start, start.plusDays(6)).first()
            .count { it.habitId == habit.id && it.count >= habit.targetPerDay }
    }

    private fun nextReview(kind: ReviewKind, s: UserSettings, after: LocalDateTime): LocalDateTime? = when (kind) {
        ReviewKind.DAILY -> s.dailyReviewTime?.let { t ->
            val today = after.toLocalDate().atTime(t)
            if (today.isAfter(after)) today else today.plusDays(1)
        }
        ReviewKind.WEEKLY -> s.weeklyReviewTime?.let { t ->
            (0L..7L).map { after.toLocalDate().plusDays(it) }
                .filter { it.dayOfWeek == s.weeklyReviewDay }
                .map { it.atTime(t) }
                .first { it.isAfter(after) }
        }
    }

    private fun millis(at: LocalDateTime): Long = at.atZone(clock.zone).toInstant().toEpochMilli()
}
