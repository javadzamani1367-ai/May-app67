package ir.roozban.feature.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.domain.LearningRunner
import ir.roozban.core.domain.LearningSnapshot
import ir.roozban.core.domain.LearningStore
import ir.roozban.core.domain.MemoryRepository
import ir.roozban.core.domain.MemoryUseCases
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.domain.Undo
import ir.roozban.core.model.MemoryFact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryUiState(
    val facts: List<MemoryFact> = emptyList(),
    val learningEnabled: Boolean = true,
    val snapshot: LearningSnapshot? = null,
    val running: Boolean = false,
)

/** A change the user can take back from the snackbar. */
data class MemoryUndo(val message: Int, val undo: Undo)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    memory: MemoryRepository,
    private val useCases: MemoryUseCases,
    private val settings: SettingsRepository,
    private val store: LearningStore,
    private val runner: LearningRunner,
) : ViewModel() {
    private val snapshot = MutableStateFlow<LearningSnapshot?>(null)
    private val running = MutableStateFlow(false)
    val undo = MutableStateFlow<MemoryUndo?>(null)

    val state: StateFlow<MemoryUiState> = combine(memory.observeFacts(), settings.settings, snapshot, running) { facts, s, snap, busy ->
        MemoryUiState(facts, s.planning.learningEnabled, snap, busy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryUiState())

    init {
        viewModelScope.launch { snapshot.value = store.load() }
    }

    fun add(text: String) {
        viewModelScope.launch { useCases.remember(text) }
    }

    fun edit(fact: MemoryFact, text: String) {
        viewModelScope.launch { useCases.edit(fact, text) }
    }

    fun togglePin(fact: MemoryFact) {
        viewModelScope.launch { useCases.setPinned(fact, !fact.pinned) }
    }

    fun delete(fact: MemoryFact) {
        viewModelScope.launch { undo.value = MemoryUndo(R.string.memory_deleted, useCases.delete(fact)) }
    }

    fun clearAll() {
        viewModelScope.launch {
            val facts = useCases.clearAll()
            val before = store.load()
            store.clear()
            snapshot.value = null
            undo.value = MemoryUndo(R.string.memory_cleared) {
                facts()
                before?.let { store.save(it) }
                snapshot.value = before
            }
        }
    }

    fun undoLast() {
        val u = undo.value ?: return
        undo.value = null
        viewModelScope.launch { u.undo() }
    }

    fun undoShown() {
        undo.value = null
    }

    fun setLearning(on: Boolean) {
        viewModelScope.launch { settings.update { it.copy(planning = it.planning.copy(learningEnabled = on)) } }
    }

    fun runNow() {
        if (running.value) return
        viewModelScope.launch {
            running.value = true
            try {
                runner.run()?.let { snapshot.value = it }
            } finally {
                running.value = false
            }
        }
    }
}
