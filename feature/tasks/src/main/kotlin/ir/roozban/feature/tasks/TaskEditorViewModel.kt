package ir.roozban.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.domain.AddSubtaskUseCase
import ir.roozban.core.domain.CompleteTaskUseCase
import ir.roozban.core.domain.DeleteTaskUseCase
import ir.roozban.core.domain.FocusRepository
import ir.roozban.core.domain.FocusService
import ir.roozban.core.domain.LabelRepository
import ir.roozban.core.domain.ProjectRepository
import ir.roozban.core.domain.ReopenTaskUseCase
import ir.roozban.core.domain.TagResolver
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.domain.Undo
import ir.roozban.core.domain.UpdateTaskUseCase
import ir.roozban.core.model.Label
import ir.roozban.core.model.Project
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.ReminderSetting
import ir.roozban.core.model.Task
import ir.roozban.core.model.TaskDue
import ir.roozban.core.recurrence.RecurrenceSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class EditorState(val original: Task, val draft: Task) {
    val canSave: Boolean get() = draft.title.isNotBlank()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val updateTask: UpdateTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val addSubtaskUseCase: AddSubtaskUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val reopenTask: ReopenTaskUseCase,
    private val tags: TagResolver,
    private val focus: FocusService,
    focusRepository: FocusRepository,
    projects: ProjectRepository,
    labels: LabelRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow<EditorState?>(null)
    val state: StateFlow<EditorState?> = _state.asStateFlow()

    val projects: StateFlow<List<Project>> = projects.observeProjects()
        .map { list -> list.filter { !it.archived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val labels: StateFlow<List<Label>> = labels.observeLabels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Subtasks are saved immediately (not part of the draft). */
    val subtasks: StateFlow<List<Task>> = _state.map { it?.original?.id }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else tasks.observeSubtasks(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Time recorded on this task (focus and manual), in minutes. */
    val trackedMinutes: StateFlow<Int> = _state.map { it?.original?.id }.distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(0L) else focusRepository.observeTaskSeconds(id) }
        .map { ((it + 30) / 60).toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val today: LocalDate get() = LocalDate.now(clock)

    /** Saves the task, then starts a focus period on it. */
    fun startFocus(onStarted: () -> Unit) {
        val s = _state.value ?: return
        viewModelScope.launch {
            if (s.canSave && s.draft != s.original) updateTask(s.draft.copy(title = s.draft.title.trim()))
            focus.start(s.original.id)
            _state.value = null
            onStarted()
        }
    }

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

    /** A recurring task needs a date: an undated task starts today. */
    fun setRecurrence(spec: RecurrenceSpec?) = edit { t ->
        if (spec == null) {
            t.copy(recurrence = null, recurrenceStart = null)
        } else {
            val due = t.due ?: TaskDue.AllDay(today)
            t.copy(due = due, recurrence = spec.toRRule(), recurrenceStart = due.date)
        }
    }

    fun setProject(projectId: String?) = edit { it.copy(projectId = projectId) }

    fun toggleLabel(labelId: String) = edit { t ->
        t.copy(labelIds = if (labelId in t.labelIds) t.labelIds - labelId else t.labelIds + labelId)
    }

    fun addLabel(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = tags.labelId(name)
            edit { it.copy(labelIds = it.labelIds + id) }
        }
    }

    fun addSubtask(title: String) {
        val parent = _state.value?.original ?: return
        viewModelScope.launch { addSubtaskUseCase(parent, title) }
    }

    fun toggleSubtask(subtask: Task) {
        viewModelScope.launch { if (subtask.isCompleted) reopenTask(subtask) else completeTask(subtask) }
    }

    fun deleteSubtask(subtask: Task) {
        viewModelScope.launch { deleteTask(subtask) }
    }

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
