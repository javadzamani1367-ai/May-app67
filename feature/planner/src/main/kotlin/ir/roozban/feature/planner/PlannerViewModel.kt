@file:OptIn(FlowPreview::class)

package ir.roozban.feature.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.domain.LearningStore
import ir.roozban.core.domain.PlanDayUseCase
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.domain.Undo
import ir.roozban.core.model.PlanningSettings
import ir.roozban.learning.DayPlan
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class PlannerUiState(
    val date: LocalDate = LocalDate.MIN,
    val tomorrow: Boolean = false,
    val plan: DayPlan? = null,
    /** Task ids of the placements that will be applied. */
    val selected: Set<String> = emptySet(),
    val settings: PlanningSettings = PlanningSettings(),
    /** Learned estimate factor, when it differs from 1. */
    val estimateFactor: Double? = null,
    /** Set right after applying, for the snackbar. */
    val applied: Applied? = null,
)

data class Applied(val count: Int, val undo: Undo)

@HiltViewModel
class PlannerViewModel @Inject constructor(
    private val planDay: PlanDayUseCase,
    private val settings: SettingsRepository,
    private val store: LearningStore,
    tasks: TaskRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(PlannerUiState(date = LocalDate.now(clock)))
    val state: StateFlow<PlannerUiState> = _state.asStateFlow()
    private var proposing: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.map { it.planning }.distinctUntilChanged().collect { p ->
                _state.update { it.copy(settings = p) }
                refresh()
            }
        }
        viewModelScope.launch {
            // Re-plan when tasks change elsewhere (added, finished, moved).
            tasks.observeOpenTasks().debounce(300).collectLatest { refresh() }
        }
    }

    fun showTomorrow(tomorrow: Boolean) {
        val today = LocalDate.now(clock)
        _state.update { it.copy(tomorrow = tomorrow, date = if (tomorrow) today.plusDays(1) else today) }
        refresh()
    }

    fun toggle(taskId: String) = _state.update {
        it.copy(selected = if (taskId in it.selected) it.selected - taskId else it.selected + taskId)
    }

    fun apply() {
        val s = _state.value
        val plan = s.plan ?: return
        viewModelScope.launch {
            val result = planDay.apply(plan, s.selected)
            _state.update { it.copy(applied = Applied(result.applied, result.undo)) }
            refresh()
        }
    }

    fun undoApplied() {
        val applied = _state.value.applied ?: return
        _state.update { it.copy(applied = null) }
        viewModelScope.launch {
            applied.undo()
            refresh()
        }
    }

    fun appliedShown() = _state.update { it.copy(applied = null) }

    fun setDayStart(t: LocalTime) = updatePlanning { if (t < it.dayEnd) it.copy(dayStart = t) else it }

    fun setDayEnd(t: LocalTime) = updatePlanning { if (t > it.dayStart) it.copy(dayEnd = t) else it }

    fun setMorning(t: LocalTime?) = updatePlanning { it.copy(morningTime = t) }

    fun setAuto(on: Boolean) = updatePlanning { it.copy(autoPlan = on) }

    private fun updatePlanning(transform: (PlanningSettings) -> PlanningSettings) {
        viewModelScope.launch { settings.update { it.copy(planning = transform(it.planning)) } }
    }

    private fun refresh() {
        proposing?.cancel()
        proposing = viewModelScope.launch {
            val date = _state.value.date
            val plan = planDay.propose(date)
            val factor = store.load()?.factors?.global?.takeIf { it.samples > 0 }?.value
            _state.update {
                it.copy(
                    plan = plan,
                    selected = plan.placements.mapTo(HashSet()) { p -> p.candidate.taskId },
                    estimateFactor = factor?.takeIf { f -> f < 0.9 || f > 1.1 },
                )
            }
        }
    }
}
