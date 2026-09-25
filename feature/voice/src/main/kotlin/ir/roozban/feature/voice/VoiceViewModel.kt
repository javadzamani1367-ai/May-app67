package ir.roozban.feature.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.ai.core.LlmException
import ir.roozban.ai.runtime.Recording
import ir.roozban.ai.runtime.VoiceInput
import ir.roozban.core.domain.LabelRepository
import ir.roozban.core.domain.ProjectRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VoiceState {
    data object Idle : VoiceState

    data class Listening(val level: Float, val speaking: Boolean) : VoiceState

    data object Transcribing : VoiceState
}

sealed interface VoiceEvent {
    data class Text(val text: String) : VoiceEvent

    data class Problem(val message: String) : VoiceEvent

    data object NeedsModel : VoiceEvent
}

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val voice: VoiceInput,
    private val projects: ProjectRepository,
    private val labels: LabelRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val _events = Channel<VoiceEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var job: Job? = null

    @Volatile
    private var stopRequested = false

    val hasModel: Boolean get() = voice.hasModel

    /** Tap: start listening; tap again while listening: stop and transcribe what was said. */
    fun toggle() {
        when (_state.value) {
            VoiceState.Idle -> start()
            is VoiceState.Listening -> stopRequested = true
            VoiceState.Transcribing -> Unit
        }
    }

    private fun start() {
        if (!voice.hasModel) {
            _events.trySend(VoiceEvent.NeedsModel)
            return
        }
        stopRequested = false
        _state.value = VoiceState.Listening(0f, false)
        job = viewModelScope.launch {
            try {
                var samples: ShortArray? = null
                voice.record { stopRequested }.collect { event ->
                    when (event) {
                        is Recording.Level -> _state.value = VoiceState.Listening(event.value, event.speaking)
                        is Recording.Done -> samples = event.samples
                    }
                }
                val audio = samples
                if (audio == null || audio.isEmpty()) {
                    _events.send(VoiceEvent.Problem("صدایی شنیده نشد."))
                    return@launch
                }
                _state.value = VoiceState.Transcribing
                val text = voice.transcribe(audio, vocabulary())
                if (text.isBlank()) _events.send(VoiceEvent.Problem("متوجه نشدم؛ دوباره بگو.")) else _events.send(VoiceEvent.Text(text))
            } catch (e: LlmException) {
                _events.send(VoiceEvent.Problem(e.message ?: "ورودی صوتی کار نکرد."))
            } finally {
                _state.value = VoiceState.Idle
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = VoiceState.Idle
    }

    /** Project and label names bias the recognizer toward the user's own words. */
    private suspend fun vocabulary(): String =
        (projects.all().filter { !it.archived }.map { it.name } + labels.all().map { it.name }).distinct().take(30).joinToString("، ")
}
