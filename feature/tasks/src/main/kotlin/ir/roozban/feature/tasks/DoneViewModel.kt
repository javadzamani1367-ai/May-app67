package ir.roozban.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.domain.DoneHistory
import ir.roozban.core.domain.DoneItem
import ir.roozban.core.domain.ProjectRepository
import ir.roozban.core.domain.ReminderSync
import ir.roozban.core.domain.ReopenTaskUseCase
import ir.roozban.core.domain.TaskRepository
import ir.roozban.core.domain.Undo
import ir.roozban.core.model.Project
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class DoneEntry(val item: DoneItem, val project: Project?) {
    /** One-off tasks can go back to the open list; a done occurrence of a recurring task cannot. */
    val canReopen: Boolean get() = item.occurrence == null
}

data class DoneDay(val label: String, val entries: List<DoneEntry>)

data class DoneUiState(
    val query: String = "",
    val days: List<DoneDay> = emptyList(),
    /** Done items in the list at all (before searching). */
    val total: Int = 0,
    val loaded: Boolean = false,
)

data class DoneMessage(val text: String, val undo: Undo?)

@HiltViewModel
class DoneViewModel @Inject constructor(
    private val history: DoneHistory,
    projects: ProjectRepository,
    private val tasks: TaskRepository,
    private val reopenTask: ReopenTaskUseCase,
    private val reminders: ReminderSync,
    private val clock: Clock,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _messages = Channel<DoneMessage>(Channel.BUFFERED)
    val messages: Flow<DoneMessage> = _messages.receiveAsFlow()

    val state: StateFlow<DoneUiState> = combine(query, history.observeDone(), projects.observeProjects()) { q, items, all ->
        val byId = all.associateBy { it.id }
        val entries = items.map { DoneEntry(it, it.projectId?.let(byId::get)) }
        DoneUiState(
            query = q,
            days = DoneList.group(DoneList.search(entries, q), LocalDate.now(clock), clock),
            total = entries.size,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DoneUiState())

    fun search(q: String) {
        query.value = q
    }

    /** Takes it off this list only; reports and statistics still count it. */
    fun remove(entry: DoneEntry) {
        viewModelScope.launch {
            history.hide(entry.item)
            _messages.send(DoneMessage("از فهرست برداشته شد؛ در گزارش‌ها و آمار می‌ماند", Undo { history.unhide(entry.item) }))
        }
    }

    fun reopen(entry: DoneEntry) {
        if (!entry.canReopen) return
        viewModelScope.launch {
            val task = tasks.get(entry.item.taskId) ?: return@launch
            reopenTask(task)
            _messages.send(
                DoneMessage("به کارهای باز برگشت") {
                    tasks.upsert(task)
                    reminders.sync(task)
                },
            )
        }
    }

    fun undo(undo: Undo) {
        viewModelScope.launch { undo() }
    }
}

internal object DoneList {
    /** Every word must appear in the title or the project name (Arabic ی/ک and half-spaces are ignored). */
    fun search(entries: List<DoneEntry>, query: String): List<DoneEntry> {
        val words = normalize(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return entries
        return entries.filter { e ->
            val text = normalize(e.item.title + " " + (e.project?.name ?: ""))
            words.all { text.contains(it, ignoreCase = true) }
        }
    }

    /** Groups by the day each was done, newest first: «امروز»، «دیروز»، then the date. */
    fun group(entries: List<DoneEntry>, today: LocalDate, clock: Clock): List<DoneDay> =
        entries.groupBy { it.item.at.atZone(clock.zone).toLocalDate() }
            .toSortedMap(compareByDescending { it })
            .map { (day, list) -> DoneDay(label(day, today), list.sortedByDescending { it.item.at }) }

    private fun label(day: LocalDate, today: LocalDate): String =
        // relativeDay also names days ahead; done days are never in the future.
        if (day.isAfter(today)) PersianDateFormatter.relativeDay(today, today) else PersianDateFormatter.relativeDay(day, today)

    private fun normalize(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').replace('\u200c', ' ')
}
