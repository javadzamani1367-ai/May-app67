package ir.roozban.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.domain.AddTaskUseCase
import ir.roozban.core.domain.CompleteTaskUseCase
import ir.roozban.core.domain.QuickAddParser
import ir.roozban.core.domain.QuickAddResult
import ir.roozban.core.domain.ReopenTaskUseCase
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.domain.Undo
import ir.roozban.core.model.UserSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

sealed interface TaskListEvent {
    /** Show a snackbar; [undo] adds a «بازگردانی» action. */
    data class Message(val text: String, val undo: Undo? = null) : TaskListEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val parser: QuickAddParser,
    private val addTask: AddTaskUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val reopenTask: ReopenTaskUseCase,
    private val clock: Clock,
) : ViewModel() {

    /** Set once by the screen; each list (tab) has its own ViewModel instance. */
    private val mode = MutableStateFlow<ListMode?>(null)

    /** Re-emitted by [refresh] (e.g. on resume) so «today» follows the calendar. */
    private val today = MutableStateFlow(LocalDate.now(clock))

    val state: StateFlow<TaskListUiState> = mode.filterNotNull().flatMapLatest { listMode ->
        combine(
            tasks.observeOpenTasks(),
            today.flatMapLatest { day -> tasks.observeCompletedSince(day.atStartOfDay(clock.zone).toInstant()) },
            today,
        ) { open, completed, _ ->
            TaskListBuilder.build(listMode, open, completed, LocalDateTime.now(clock))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState(ListMode.TODAY))

    private val _quickAdd = MutableStateFlow(QuickAddState())
    val quickAdd: StateFlow<QuickAddState> = _quickAdd.asStateFlow()

    private var lastParse: QuickAddResult? = null
    private var currentSettings = UserSettings()

    private val _events = Channel<TaskListEvent>(Channel.BUFFERED)
    val events: Flow<TaskListEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { settings.settings.collect { currentSettings = it } }
    }

    fun setMode(listMode: ListMode) {
        mode.value = listMode
    }

    fun refresh() {
        today.value = LocalDate.now(clock)
    }

    fun onQuickAddTextChange(text: String) {
        if (text.isBlank()) {
            lastParse = null
            _quickAdd.value = QuickAddState(text)
            return
        }
        val now = LocalDateTime.now(clock)
        val result = parser.parse(text, now, currentSettings)
        lastParse = result
        _quickAdd.value = QuickAddState(text, result.toPreview(now.toLocalDate()))
    }

    /** Returns true when the task was added (the sheet should then close). */
    fun submitQuickAdd(): Boolean {
        val input = lastParse ?: return false
        if (input.title.isBlank()) return false
        lastParse = null
        _quickAdd.value = QuickAddState()
        viewModelScope.launch {
            val task = addTask(input) ?: return@launch
            val whenText = task.due?.let { TaskFormatter.due(it, LocalDate.now(clock)) }
            _events.send(TaskListEvent.Message(if (whenText != null) "ثبت شد: $whenText" else "ثبت شد"))
        }
        return true
    }

    fun dismissQuickAdd() {
        lastParse = null
        _quickAdd.value = QuickAddState()
    }

    fun onToggleComplete(id: String) {
        viewModelScope.launch {
            val task = tasks.get(id) ?: return@launch
            if (task.isCompleted) {
                reopenTask(task)
            } else {
                val undo = completeTask(task)
                val message = if (task.recurrence != null) "انجام شد؛ نوبت بعدی ثبت شد" else "انجام شد"
                _events.send(TaskListEvent.Message(message, undo))
            }
        }
    }

    /** Offers undo for an action taken elsewhere, e.g. deleting from the editor. */
    fun offerUndo(text: String, undo: Undo) {
        viewModelScope.launch { _events.send(TaskListEvent.Message(text, undo)) }
    }

    fun undo(undo: Undo) {
        viewModelScope.launch { undo() }
    }

    private fun QuickAddResult.toPreview(today: LocalDate): QuickAddPreview {
        val chips = buildList {
            due?.let { add(PreviewChip(ChipKind.TIME, TaskFormatter.due(it, today))) }
            recurrence?.let { add(PreviewChip(ChipKind.RECURRENCE, TaskFormatter.recurrence(it))) }
            estimate?.let { add(PreviewChip(ChipKind.DURATION, TaskFormatter.duration(it))) }
            val priority = when {
                important && urgent -> "مهم و فوری"
                important -> "مهم"
                urgent -> "فوری"
                else -> null
            }
            priority?.let { add(PreviewChip(ChipKind.PRIORITY, it)) }
        }
        return QuickAddPreview(
            title = title,
            chips = chips,
            highlights = highlights,
            needsReview = due != null && confidence < REVIEW_THRESHOLD,
        )
    }

    companion object {
        private const val REVIEW_THRESHOLD = 0.6f
    }
}
