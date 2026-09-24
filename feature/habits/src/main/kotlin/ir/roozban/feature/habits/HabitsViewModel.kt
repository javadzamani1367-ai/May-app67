package ir.roozban.feature.habits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.calendar.JalaliDate
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.domain.Access
import ir.roozban.core.domain.Entitlements
import ir.roozban.core.domain.HabitDayStatus
import ir.roozban.core.domain.HabitRepository
import ir.roozban.core.domain.HabitStats
import ir.roozban.core.domain.HabitStreaks
import ir.roozban.core.domain.HabitUseCases
import ir.roozban.core.domain.ProFeature
import ir.roozban.core.model.Habit
import ir.roozban.core.model.HabitLog
import ir.roozban.core.model.HabitSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** One day in a habit's strip or calendar. [status] null = outside the habit's life (before start or future). */
data class HabitDayCell(
    val date: LocalDate,
    val status: HabitDayStatus?,
    val count: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
)

data class HabitCard(val habit: Habit, val stats: HabitStats, val week: List<HabitDayCell>)

data class HabitsUiState(
    val loading: Boolean = true,
    val active: List<HabitCard> = emptyList(),
    val archived: List<HabitCard> = emptyList(),
    val access: Access = Access.FULL,
    val today: LocalDate = LocalDate.MIN,
) {
    val doneToday: Int get() = active.count { it.stats.days[today] == HabitDayStatus.DONE }
    val dueToday: Int get() = active.count { it.stats.days[today] == HabitDayStatus.DONE || it.stats.days[today] == HabitDayStatus.PENDING }
}

/** What the editor produces; [id] null = new habit. */
data class HabitDraft(
    val id: String? = null,
    val name: String = "",
    val color: Int = 0,
    val schedule: HabitSchedule = HabitSchedule.Daily,
    val target: Int = 1,
    val reminder: LocalTime? = null,
) {
    companion object {
        fun of(h: Habit) = HabitDraft(h.id, h.name, h.color, h.schedule, h.targetPerDay, h.reminderTime)
    }
}

internal fun cells(habit: Habit, stats: HabitStats, logs: Map<LocalDate, Int>, days: List<LocalDate>, today: LocalDate) = days.map { d ->
    HabitDayCell(
        date = d,
        status = when {
            d.isAfter(today) -> null
            else -> stats.days[d] ?: if (d.isBefore(habit.startDate) && (logs[d] ?: 0) == 0) null else HabitDayStatus.OFF
        },
        count = logs[d] ?: 0,
        isToday = d == today,
        isFuture = d.isAfter(today),
    )
}

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val habits: HabitRepository,
    private val useCases: HabitUseCases,
    private val entitlements: Entitlements,
    private val clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    val state: StateFlow<HabitsUiState> = combine(
        habits.observeHabits(),
        habits.observeLogs(today.minusDays(LOOKBACK_DAYS), today),
    ) { list, logs -> build(list, logs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState(today = today))

    private fun build(list: List<Habit>, logs: List<HabitLog>): HabitsUiState {
        val byHabit = logs.groupBy { it.habitId }.mapValues { (_, l) -> l.associate { it.date to it.count } }
        val week = PersianWeek.startOfWeek(today).let { start -> (0L..6L).map { start.plusDays(it) } }
        val cards = list.map { h ->
            val l = byHabit[h.id].orEmpty()
            val stats = HabitStreaks.compute(h, l, today)
            HabitCard(h, stats, cells(h, stats, l, week, today))
        }
        return HabitsUiState(
            loading = false,
            active = cards.filter { !it.habit.archived },
            archived = cards.filter { it.habit.archived },
            access = entitlements.access(ProFeature.HABITS),
            today = today,
        )
    }

    fun tap(habit: Habit, date: LocalDate) {
        if (state.value.access != Access.FULL) return
        viewModelScope.launch { useCases.tap(habit, date) }
    }

    fun save(draft: HabitDraft) {
        if (state.value.access != Access.FULL || draft.name.isBlank()) return
        viewModelScope.launch {
            val existing = draft.id?.let { habits.get(it) }
            if (existing == null) {
                useCases.create(draft.name, draft.color, draft.schedule, draft.target, draft.reminder)
            } else {
                useCases.update(
                    existing.copy(
                        name = draft.name,
                        color = draft.color,
                        schedule = draft.schedule,
                        targetPerDay = draft.target,
                        reminderTime = draft.reminder,
                    ),
                )
            }
        }
    }

    fun setArchived(habit: Habit, archived: Boolean) {
        viewModelScope.launch { useCases.setArchived(habit, archived) }
    }

    fun delete(habit: Habit) {
        viewModelScope.launch { useCases.delete(habit) }
    }

    companion object {
        /** Enough history for streaks of about two years. */
        const val LOOKBACK_DAYS = 730L
    }
}

data class HabitDetailUiState(
    val habit: Habit? = null,
    val stats: HabitStats? = null,
    val month: JalaliDate = JalaliDate.of(1400, 1, 1),
    /** Month grid, Saturday first; null cells pad the first week. */
    val cells: List<HabitDayCell?> = emptyList(),
    val access: Access = Access.FULL,
    val deleted: Boolean = false,
)

@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val habits: HabitRepository,
    private val useCases: HabitUseCases,
    private val entitlements: Entitlements,
    private val clock: Clock,
) : ViewModel() {
    private val habitId: String = checkNotNull(savedStateHandle["habitId"])
    private val today = LocalDate.now(clock)
    private val month = MutableStateFlow(today.toJalali().firstDayOfMonth())

    val state: StateFlow<HabitDetailUiState> = combine(
        habits.observeHabit(habitId),
        habits.observeHabitLogs(habitId),
        month,
    ) { habit, logs, m ->
        if (habit == null) return@combine HabitDetailUiState(month = m, deleted = true)
        val counts = logs.associate { it.date to it.count }
        val stats = HabitStreaks.compute(habit, counts, today)
        val first = m.toLocalDate()
        val days = (0 until m.lengthOfMonth).map { first.plusDays(it.toLong()) }
        val leading = List(PersianWeek.indexOf(first.dayOfWeek)) { null }
        HabitDetailUiState(habit, stats, m, leading + cells(habit, stats, counts, days, today), entitlements.access(ProFeature.HABITS))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDetailUiState(month = month.value))

    fun previousMonth() {
        month.value = month.value.plusMonths(-1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun tap(date: LocalDate) {
        val habit = state.value.habit ?: return
        if (state.value.access != Access.FULL) return
        viewModelScope.launch { useCases.tap(habit, date) }
    }

    fun save(draft: HabitDraft) {
        val habit = state.value.habit ?: return
        if (draft.name.isBlank()) return
        viewModelScope.launch {
            useCases.update(habit.copy(name = draft.name, color = draft.color, schedule = draft.schedule, targetPerDay = draft.target, reminderTime = draft.reminder))
        }
    }

    fun setArchived(archived: Boolean) {
        val habit = state.value.habit ?: return
        viewModelScope.launch { useCases.setArchived(habit, archived) }
    }

    fun delete() {
        val habit = state.value.habit ?: return
        viewModelScope.launch { useCases.delete(habit) }
    }
}
