package ir.roozban.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.ai.models.ModelSpec
import ir.roozban.ai.runtime.ModelManager
import ir.roozban.ai.runtime.ModelsState
import ir.roozban.ai.tts.SpeakState
import ir.roozban.ai.tts.SpeechOutput
import ir.roozban.ai.tts.VoiceOption
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.model.SpeechSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoicesUiState(
    val models: ModelsState,
    val speech: SpeechSettings = SpeechSettings(),
    /** Offline Persian voices of the phone's own engine; null while looking. */
    val systemVoices: List<VoiceOption>? = null,
    val speaking: SpeakState = SpeakState.Idle,
)

@HiltViewModel
class VoicesViewModel @Inject constructor(
    private val manager: ModelManager,
    private val settings: SettingsRepository,
    private val speech: SpeechOutput,
) : ViewModel() {
    private val systemVoices = MutableStateFlow<List<VoiceOption>?>(null)

    val state: StateFlow<VoicesUiState> = combine(manager.state, settings.settings.map { it.speech }, systemVoices, speech.state) { m, s, sys, speaking ->
        VoicesUiState(m, s, sys, speaking)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VoicesUiState(manager.state.value))

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _confirmMobile = MutableStateFlow<ModelSpec?>(null)
    val confirmMobile: StateFlow<ModelSpec?> = _confirmMobile.asStateFlow()

    init {
        manager.refresh()
        viewModelScope.launch { systemVoices.value = speech.systemVoices() }
    }

    fun select(key: String?) = update { it.copy(voice = key) }

    fun setRate(rate: Float) = update { it.copy(rate = rate.coerceIn(SpeechSettings.MIN_RATE, SpeechSettings.MAX_RATE)) }

    fun setReadReplies(on: Boolean) = update { it.copy(readReplies = on) }

    fun preview(key: String) {
        val id = PREVIEW + key
        if ((speech.state.value as? SpeakState.Speaking)?.id == id || (speech.state.value as? SpeakState.Preparing)?.id == id) {
            speech.stop()
        } else {
            speech.speak(SAMPLE, id = id, voiceKey = key)
        }
    }

    fun download(spec: ModelSpec, confirmedMobile: Boolean = false) {
        _confirmMobile.value = null
        val needed = spec.sizeBytes * 2 - manager.store.partialBytes(spec) + ModelManager.SPACE_MARGIN
        _message.value = when {
            manager.freeSpaceBytes() < needed -> "فضای خالی کافی نیست؛ دست‌کم ${formatSize(needed)} لازم است."
            !manager.networkAllowed() -> if (manager.state.value.wifiOnly) "به وای‌فای وصل شو، یا در تنظیمات دانلود با اینترنت همراه را هم مجاز کن." else "اینترنت در دسترس نیست."
            manager.isMetered() && !confirmedMobile -> {
                _confirmMobile.value = spec
                null
            }
            else -> {
                manager.startDownload(spec)
                null
            }
        }
    }

    fun dismissMobile() {
        _confirmMobile.value = null
    }

    fun cancel(spec: ModelSpec) = manager.cancelDownload(spec)

    fun discard(spec: ModelSpec) = manager.discardPartial(spec)

    fun delete(id: String) {
        speech.stop()
        manager.delete(id)
        if (state.value.speech.voice == "piper:$id") select(null)
    }

    fun messageShown() {
        _message.value = null
    }

    override fun onCleared() {
        if (speech.state.value.let { it is SpeakState.Speaking && it.id.startsWith(PREVIEW) || it is SpeakState.Preparing && it.id.startsWith(PREVIEW) }) speech.stop()
    }

    private fun update(transform: (SpeechSettings) -> SpeechSettings) {
        viewModelScope.launch { settings.update { it.copy(speech = transform(it.speech)) } }
    }

    companion object {
        const val PREVIEW = "preview:"
        const val SAMPLE = "سلام! من روزبان هستم. فردا ساعت نه صبح جلسه داری و سه کار برای امروز مانده است."
    }
}
