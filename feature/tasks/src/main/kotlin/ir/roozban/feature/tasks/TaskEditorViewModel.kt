package ir.roozban.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.domain.DeleteTaskUseCase
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.domain.Undo
import ir.roozban.core.domain.UpdateTaskUseCase
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class EditorState(val original: Task, val draft: Task) {
    val canSave: Boolean get() = draft.title.isNotBlank()
}

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val updateTask: UpdateTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow<EditorState?>(null)
    val state: StateFlow<EditorState?> = _state.asStateFlow()

    val today: LocalDate get() = LocalDate.now(clock)

    fun load(id: String) {
        if (_state.value?.original?.id == id) return
        viewModelScope.launch {
            tasks.get(id)?.let { _state.value = EditorState(it, it) }
        }
    }

    private fun edit(transform: (Task) -> Task) = _state.update { it?.copy(draft = transform(it.draft)) }

    fun setTitle(title: String) = edit { it.copy(title = title) }

    fun setNotes(notes: String) = edit { it.copy(notes = notes) }

    /** Keeps the time when moving to another day. A recurring series restarts from the new date. */
    fun setDate(date: LocalDate?) = edit { t ->
        val due = when {
            date == null -> null
            t.due is TaskDue.At -> TaskDue.At(date, (t.due as TaskDue.At).time)
            else -> TaskDue.AllDay(date)
        }
        t.copy(
            due = due,
            recurrenceStart = if (t.recurrence != null) date else t.recurrenceStart,
            reminder = t.reminder.takeIf { due != null },
        )
    }

    /** Setting a time on an undated task puts it on today. */
    fun setTime(time: LocalTime?) = edit { t ->
        val date = t.due?.date ?: today
        t.copy(due = if (time == null) TaskDue.AllDay(date) else TaskDue.At(date, time))
    }

    fun setReminderKind(kind: ReminderKind?) = edit { t ->
        t.copy(reminder = kind?.let { ReminderSetting(it, t.reminder?.offsetMinutes ?: 0) })
    }

    fun setReminderOffset(minutes: Int) = edit { t -> t.copy(reminder = t.reminder?.copy(offsetMinutes = minutes)) }

    fun setImportant(value: Boolean) = edit { it.copy(important = value) }

    fun setUrgent(value: Boolean) = edit { it.copy(urgent = value) }

    fun setEstimate(minutes: Int?) = edit { it.copy(estimateMinutes = minutes) }

    fun removeRecurrence() = edit { it.copy(recurrence = null, recurrenceStart = null) }

    fun save(onSaved: () -> Unit) {
        val s = _state.value ?: return
        if (!s.canSave) return
        viewModelScope.launch {
            if (s.draft != s.original) updateTask(s.draft.copy(title = s.draft.title.trim()))
            _state.value = null
            onSaved()
        }
    }

    fun delete(onDeleted: (Undo) -> Unit) {
        val s = _state.value ?: return
        viewModelScope.launch {
            val undo = deleteTask(s.original)
            _state.value = null
            onDeleted(undo)
        }
    }

    fun discard() {
        _state.value = null
    }
}
