package ir.roozban.feature.tools

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.ai.runtime.Dictation
import ir.roozban.ai.runtime.DictationEvent
import ir.roozban.ai.tts.SpeakState
import ir.roozban.ai.tts.SpeechOutput
import ir.roozban.core.domain.NoteRepository
import ir.roozban.core.domain.NoteUseCases
import ir.roozban.core.model.Note
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    notes: NoteRepository,
    private val useCases: NoteUseCases,
) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<Pair<String, List<Note>>> = combine(query, notes.observeNotes()) { q, list ->
        val words = q.trim().split(' ').filter { it.isNotBlank() }
        q to if (words.isEmpty()) list else list.filter { n -> words.all { w -> n.title.contains(w) || n.body.contains(w) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "" to emptyList())

    fun search(q: String) {
        query.value = q
    }

    fun delete(note: Note) {
        viewModelScope.launch { useCases.delete(note) }
    }
}

sealed interface DictationUi {
    data object Idle : DictationUi

    data class Listening(val level: Float, val speaking: Boolean, val pending: Int) : DictationUi

    /** Recording stopped; the last pieces are still being turned into text. */
    data class Finishing(val pending: Int) : DictationUi
}

data class NoteUiState(
    val id: String? = null,
    val title: String = "",
    val body: String = "",
    val dictation: DictationUi = DictationUi.Idle,
    val reading: Boolean = false,
    val loaded: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notes: NoteRepository,
    private val useCases: NoteUseCases,
    private val dictation: Dictation,
    private val speech: SpeechOutput,
) : ViewModel() {
    private val _state = MutableStateFlow(NoteUiState())
    val state: StateFlow<NoteUiState> = _state.asStateFlow()

    /** Speech model missing: the screen offers to download it. */
    val needModel = MutableStateFlow(false)

    /** No voice for reading aloud. */
    val noVoice = MutableStateFlow(false)

    private var stopRequested = false
    private var dictating: Job? = null

    init {
        viewModelScope.launch {
            val id = savedStateHandle.get<String>("id")
            val note = id?.let { notes.get(it) } ?: useCases.create()
            _state.update { it.copy(id = note.id, title = note.title, body = note.body, loaded = true) }
            // Everything is saved as you type (and dictate).
            _state.drop(1).debounce(SAVE_DELAY_MS).collect { s -> s.id?.let { useCases.save(it, s.title, s.body) } }
        }
        viewModelScope.launch {
            speech.state.collect { s ->
                val mine = s is SpeakState.Speaking && s.id == speechId() || s is SpeakState.Preparing && s.id == speechId()
                _state.update { it.copy(reading = mine) }
                if (s is SpeakState.NoVoice) {
                    noVoice.value = true
                    speech.clearNoVoice()
                }
            }
        }
    }

    fun setTitle(t: String) = _state.update { it.copy(title = t) }

    fun setBody(b: String) = _state.update { it.copy(body = b) }

    fun toggleDictation() {
        when (_state.value.dictation) {
            is DictationUi.Listening -> stopRequested = true
            is DictationUi.Finishing -> Unit
            DictationUi.Idle -> start()
        }
    }

    private fun start() {
        if (!dictation.hasModel) {
            needModel.value = true
            return
        }
        speech.stop()
        stopRequested = false
        _state.update { it.copy(dictation = DictationUi.Listening(0f, false, 0)) }
        dictating = viewModelScope.launch {
            try {
                dictation.run(stop = { stopRequested }, context = { _state.value.body }).collect { e ->
                    when (e) {
                        is DictationEvent.Level -> _state.update { s ->
                            val d = s.dictation
                            s.copy(dictation = if (stopRequested) DictationUi.Finishing(pendingOf(d)) else DictationUi.Listening(e.value, e.speaking, pendingOf(d)))
                        }
                        is DictationEvent.Pending -> _state.update { s ->
                            s.copy(dictation = when (val d = s.dictation) {
                                is DictationUi.Listening -> d.copy(pending = e.count)
                                is DictationUi.Finishing -> DictationUi.Finishing(e.count)
                                DictationUi.Idle -> DictationUi.Idle
                            })
                        }
                        is DictationEvent.Text -> _state.update { it.copy(body = append(it.body, e.text)) }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                _state.update { it.copy(dictation = DictationUi.Idle) }
            }
        }
    }

    fun toggleReading() {
        if (_state.value.reading) {
            speech.stop()
        } else {
            val s = _state.value
            speech.speak(listOf(s.title, s.body).filter { it.isNotBlank() }.joinToString("\n"), id = speechId())
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = _state.value.id ?: return
        viewModelScope.launch {
            speech.stop()
            notes.get(id)?.let { useCases.delete(it) }
            _state.update { it.copy(id = null) }
            onDone()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCleared() {
        stopRequested = true
        if (_state.value.reading) speech.stop()
        val s = _state.value
        val id = s.id ?: return
        // Save the last edits even as the screen goes away.
        GlobalScope.launch(NonCancellable) {
            useCases.save(id, s.title, s.body)
            useCases.discardIfEmpty(id)
        }
    }

    private fun speechId() = "note:${_state.value.id}"

    private fun pendingOf(d: DictationUi) = when (d) {
        is DictationUi.Listening -> d.pending
        is DictationUi.Finishing -> d.pending
        DictationUi.Idle -> 0
    }

    private companion object {
        const val SAVE_DELAY_MS = 600L

        /** Dictated text goes after what is there, with a space or on a new line. */
        fun append(body: String, text: String): String = when {
            body.isBlank() -> text.trim()
            body.endsWith("\n") || body.endsWith(" ") -> body + text.trim()
            else -> body + " " + text.trim()
        }
    }
}
