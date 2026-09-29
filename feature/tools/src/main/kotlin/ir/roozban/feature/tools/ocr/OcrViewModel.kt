package ir.roozban.feature.tools.ocr

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.ai.models.ModelCatalog
import ir.roozban.ai.runtime.DownloadState
import ir.roozban.ai.runtime.ModelManager
import ir.roozban.ai.tts.SpeakState
import ir.roozban.ai.tts.SpeechOutput
import ir.roozban.core.domain.NoteUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OcrUiState(
    val text: String = "",
    val working: Boolean = false,
    val error: String? = null,
    val savedNoteId: String? = null,
)

/** Language data for the tool: missing, downloading (0..1) or ready. */
sealed interface OcrData {
    data object Missing : OcrData
    data class Downloading(val fraction: Float) : OcrData
    data class Failed(val message: String) : OcrData
    data object Ready : OcrData
}

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val engine: OcrEngine,
    private val models: ModelManager,
    private val notes: NoteUseCases,
    private val speech: SpeechOutput,
) : ViewModel() {
    private val _state = MutableStateFlow(OcrUiState())
    val state: StateFlow<OcrUiState> = _state.asStateFlow()

    val data: StateFlow<OcrData> = models.state.map { s ->
        val specs = ModelCatalog.ocr
        val downloads = specs.mapNotNull { s.downloads[it.id] }
        when {
            specs.all { ModelCatalog.ocrLanguage(it) in s.ocrInstalled } -> OcrData.Ready
            downloads.any { it is DownloadState.Running } -> {
                val r = downloads.filterIsInstance<DownloadState.Running>()
                OcrData.Downloading(r.sumOf { it.downloaded }.toFloat() / r.sumOf { it.total }.coerceAtLeast(1))
            }
            downloads.any { it is DownloadState.Failed } -> OcrData.Failed((downloads.first { it is DownloadState.Failed } as DownloadState.Failed).message)
            else -> OcrData.Missing
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OcrData.Missing)

    val reading: StateFlow<Boolean> = speech.state.map { it is SpeakState.Speaking && it.id == SPEECH_ID || it is SpeakState.Preparing && it.id == SPEECH_ID }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val downloadSizeBytes: Long get() = ModelCatalog.ocr.sumOf { it.sizeBytes }

    fun download() {
        ModelCatalog.ocr.filter { ModelCatalog.ocrLanguage(it) !in models.ocrInstalled() }.forEach(models::startDownload)
    }

    fun read(uri: Uri) {
        _state.update { it.copy(working = true, error = null, savedNoteId = null) }
        viewModelScope.launch {
            val result = runCatching { engine.readImage(uri) }
            _state.update { s ->
                result.fold(
                    onSuccess = { text ->
                        val joined = if (s.text.isBlank()) text else s.text.trimEnd() + "\n\n" + text
                        s.copy(text = joined, working = false, error = if (text.isBlank()) "متنی در تصویر پیدا نشد." else null)
                    },
                    onFailure = { e -> s.copy(working = false, error = e.message ?: "خواندن تصویر نشد.") },
                )
            }
        }
    }

    fun setText(text: String) = _state.update { it.copy(text = text) }

    fun clear() = _state.update { OcrUiState() }

    fun saveAsNote() {
        val text = _state.value.text.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val note = notes.create()
            notes.save(note.id, "", text)
            _state.update { it.copy(savedNoteId = note.id) }
        }
    }

    fun toggleReading() {
        if (reading.value) speech.stop() else speech.speak(_state.value.text, id = SPEECH_ID)
    }

    override fun onCleared() {
        if (reading.value) speech.stop()
    }

    private companion object {
        const val SPEECH_ID = "ocr"
    }
}
