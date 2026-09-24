package ir.roozban.feature.tasks.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.domain.UpdateTaskUseCase
import ir.roozban.core.model.TaskDue
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

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val tasks: TaskRepository,
    settings: SettingsRepository,
    private val updateTask: UpdateTaskUseCase,
    private val clock: Clock,
) : ViewModel() {

    private data class Position(val mode: CalendarMode, val anchor: LocalDate, val selected: LocalDate)

    private val position = MutableStateFlow(LocalDate.now(clock).let { Position(CalendarMode.WEEK, it, it) })

    val state: StateFlow<CalendarUiState> = combine(position, tasks.observeOpenTasks(), settings.settings) { pos, open, s ->
        CalendarUiState(
            mode = pos.mode,
            anchor = pos.anchor,
            title = CalendarBuilder.title(pos.mode, pos.anchor),
            days = CalendarBuilder.build(pos.mode, pos.anchor, LocalDate.now(clock), open, s.showGregorian, s.showHijri, s.hijriOffset),
            selected = pos.selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState(anchor = LocalDate.now(clock)))

    fun setMode(mode: CalendarMode) {
        position.value = position.value.let { it.copy(mode = mode, anchor = it.selected) }
    }

    fun next() = shift(1)

    fun previous() = shift(-1)

    private fun shift(steps: Long) {
        position.value = position.value.let { p ->
            val anchor = CalendarBuilder.shift(p.mode, p.anchor, steps)
            p.copy(anchor = anchor, selected = anchor)
        }
    }

    fun goToToday() {
        val today = LocalDate.now(clock)
        position.value = position.value.copy(anchor = today, selected = today)
    }

    fun select(date: LocalDate) {
        position.value = position.value.copy(selected = date)
    }

    /** Opens a day from the month grid. */
    fun openDay(date: LocalDate) {
        position.value = Position(CalendarMode.DAY, date, date)
    }

    /** Drag-and-drop result: puts the task on [date] at [time] (a time slot on the grid). */
    fun moveTask(taskId: String, date: LocalDate, time: LocalTime) {
        viewModelScope.launch {
            val task = tasks.get(taskId) ?: return@launch
            val due = TaskDue.At(date, time)
            if (task.due == due) return@launch
            // A recurring series keeps its phase; only this occurrence moves.
            updateTask(task.copy(due = due))
        }
    }
}
