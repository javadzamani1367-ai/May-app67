@file:OptIn(ExperimentalCoroutinesApi::class)

package ir.roozban.feature.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.domain.CompleteTaskUseCase
import ir.roozban.core.domain.DailyReview
import ir.roozban.core.domain.DeleteTaskUseCase
import ir.roozban.core.domain.FocusRepository
import ir.roozban.core.domain.HabitRepository
import ir.roozban.core.domain.HabitUseCases
import ir.roozban.core.domain.Period
import ir.roozban.core.domain.ReportRange
import ir.roozban.core.domain.ReviewBuilder
import ir.roozban.core.domain.ReviewKind
import ir.roozban.core.domain.RoutineReminders
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.domain.UpdateTaskUseCase
import ir.roozban.core.domain.WeeklyReview
import ir.roozban.core.model.Habit
import ir.roozban.core.model.Task
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class ReviewUiState(
    val kind: ReviewKind = ReviewKind.DAILY,
    val today: LocalDate = LocalDate.MIN,
    val daily: DailyReview? = null,
    val weekly: WeeklyReview? = null,
    val settings: UserSettings = UserSettings(),
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tasks: TaskRepository,
    focus: FocusRepository,
    private val habits: HabitRepository,
    private val habitUseCases: HabitUseCases,
    source: ReportSource,
    private val settings: SettingsRepository,
    private val routines: RoutineReminders,
    private val update: UpdateTaskUseCase,
    private val complete: CompleteTaskUseCase,
    private val delete: DeleteTaskUseCase,
    private val clock: Clock,
) : ViewModel() {
    val kind: ReviewKind = if (savedStateHandle.get<Boolean>("weekly") == true) ReviewKind.WEEKLY else ReviewKind.DAILY
    private val today = LocalDate.now(clock)
    private val zone = clock.zone

    private val daily: Flow<DailyReview?> = if (kind != ReviewKind.DAILY) {
        flowOf(null)
    } else {
        val from = today.atStartOfDay(zone).toInstant()
        val until = today.plusDays(1).atStartOfDay(zone).toInstant()
        combine(
            tasks.observeOpenTasks(),
            tasks.observeCompletionEvents(from, until),
            focus.observeSessions(from, until),
            habits.observeHabits(),
            habits.observeLogs(today, today),
        ) { open, done, sessions, habitList, logs ->
            ReviewBuilder.daily(today, open, done, sessions, habitList, logs, zone)
        }
    }

    private val weekly: Flow<WeeklyReview?> = if (kind != ReviewKind.WEEKLY) {
        flowOf(null)
    } else {
        val week = Period.week(today)
        combine(
            source.report(week),
            source.report(ReportRange.WEEK.previous(week)),
            tasks.observeOpenTasks(),
            habits.observeHabits(),
            habits.observeLogs(week.start, week.end),
        ) { report, previous, open, habitList, logs ->
            ReviewBuilder.weekly(report, previous, open, habitList, logs, today)
        }
    }

    val state: StateFlow<ReviewUiState> = combine(daily, weekly, settings.settings) { d, w, s ->
        ReviewUiState(kind, today, d, w, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState(kind = kind, today = today))

    /** Moves a task to [date], keeping its time of day. */
    fun moveTo(task: Task, date: LocalDate) {
        val due = task.due ?: return
        viewModelScope.launch { update(task.copy(due = due.withDate(date))) }
    }

    fun moveAllToTomorrow() {
        val leftover = state.value.daily?.leftover.orEmpty() + state.value.weekly?.overdue.orEmpty()
        val tomorrow = today.plusDays(1)
        viewModelScope.launch { leftover.forEach { t -> t.due?.let { update(t.copy(due = it.withDate(tomorrow))) } } }
    }

    fun complete(task: Task) {
        viewModelScope.launch { complete.invoke(task) }
    }

    fun delete(task: Task) {
        viewModelScope.launch { delete.invoke(task) }
    }

    fun tapHabit(habit: Habit) {
        viewModelScope.launch { habitUseCases.tap(habit, today) }
    }

    fun setDailyReminder(time: LocalTime?) = updateSettings { it.copy(dailyReviewTime = time) }

    fun setWeeklyReminder(day: DayOfWeek, time: LocalTime?) = updateSettings { it.copy(weeklyReviewDay = day, weeklyReviewTime = time) }

    private fun updateSettings(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch {
            settings.update(transform)
            routines.syncReviews()
        }
    }
}
