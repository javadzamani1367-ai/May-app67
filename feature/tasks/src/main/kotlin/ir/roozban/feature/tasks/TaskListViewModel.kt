package ir.roozban.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.domain.AddTaskUseCase
import ir.roozban.core.domain.CompleteTaskUseCase
import ir.roozban.core.domain.LabelRepository
import ir.roozban.core.domain.ProjectRepository
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
    private val projects: ProjectRepository,
    private val labels: LabelRepository,
    private val settings: SettingsRepository,
    private val parser: QuickAddParser,
    private val addTask: AddTaskUseCase,
    private val completeTask: CompleteTaskUseCase,
    private val reopenTask: ReopenTaskUseCase,
    private val clock: Clock,
) : ViewModel() {

    private data class Target(val mode: ListMode, val projectId: String?)

    /** Set once by the screen; each list (tab or project) has its own ViewModel instance. */
    private val target = MutableStateFlow<Target?>(null)

    private val context = combine(
        projects.observeProjects(),
        labels.observeLabels(),
        tasks.observeSubtaskProgress(),
    ) { p, l, progress -> TaskListBuilder.Context(p.associateBy { it.id }, l.associateBy { it.id }, progress) }

    /** Re-emitted by [refresh] (e.g. on resume) so «today» follows the calendar. */
    private val today = MutableStateFlow(LocalDate.now(clock))

    val state: StateFlow<TaskListUiState> = target.filterNotNull().flatMapLatest { t ->
        combine(
            tasks.observeOpenTasks(),
            today.flatMapLatest { day -> tasks.observeCompletedSince(day.atStartOfDay(clock.zone).toInstant()) },
            today,
            context,
        ) { open, completed, _, ctx ->
            TaskListBuilder.build(t.mode, open, completed, LocalDateTime.now(clock), ctx, t.projectId)
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

    fun setMode(listMode: ListMode, projectId: String? = null) {
        target.value = Target(listMode, projectId)
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

    /** Returns true when the task was accepted (the sheet should then close); saving continues in the background. */
    fun submitQuickAdd(): Boolean {
        val input = takeQuickAdd() ?: return false
        viewModelScope.launch { save(input) }
        return true
    }

    /** Like [submitQuickAdd] but returns only after the task is stored, for screens that close right away. */
    suspend fun submitQuickAddAndWait(): Boolean {
        val input = takeQuickAdd() ?: return false
        save(input)
        return true
    }

    private fun takeQuickAdd(): QuickAddResult? {
        val input = lastParse ?: return null
        if (input.title.isBlank()) return null
        lastParse = null
        _quickAdd.value = QuickAddState()
        return input
    }

    private suspend fun save(input: QuickAddResult) {
        val task = addTask(input, defaultProjectId = target.value?.projectId) ?: return
        val whenText = task.due?.let { TaskFormatter.due(it, LocalDate.now(clock)) }
        _events.send(TaskListEvent.Message(if (whenText != null) "ثبت شد: $whenText" else "ثبت شد"))
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
            projectName?.let { add(PreviewChip(ChipKind.PROJECT, it)) }
            labelNames.forEach { add(PreviewChip(ChipKind.LABEL, it)) }
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
