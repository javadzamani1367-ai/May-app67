package ir.roozban.feature.assistant

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.ai.models.ModelSpec
import ir.roozban.ai.runtime.AssistantHost
import ir.roozban.ai.runtime.ModelManager
import ir.roozban.ai.runtime.ModelsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val manager: ModelManager,
    private val host: AssistantHost,
) : ViewModel() {
    val state: StateFlow<ModelsState> = manager.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), manager.state.value)

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        manager.refresh()
    }

    /** Checks space and network first; returns a problem to show, or null when it started. */
    fun download(spec: ModelSpec) {
        val needed = spec.sizeBytes - manager.store.partialBytes(spec) + ModelManager.SPACE_MARGIN
        _message.value = when {
            manager.freeSpaceBytes() < needed -> "فضای خالی کافی نیست؛ دست‌کم ${formatSize(needed)} لازم است."
            !manager.networkAllowed() -> if (state.value.wifiOnly) "به وای‌فای وصل شو یا گزینهٔ «فقط با وای‌فای» را خاموش کن." else "اینترنت در دسترس نیست."
            else -> {
                manager.startDownload(spec)
                null
            }
        }
    }

    fun cancel(spec: ModelSpec) = manager.cancelDownload(spec)

    fun discard(spec: ModelSpec) = manager.discardPartial(spec)

    fun activate(id: String) {
        manager.setActive(id)
        viewModelScope.launch { host.engine.unload() }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            if (state.value.active?.id == id) host.engine.unload()
            manager.delete(id)
        }
    }

    fun setWifiOnly(value: Boolean) = manager.setWifiOnly(value)

    fun import(uri: Uri) {
        _importing.value = true
        viewModelScope.launch {
            _message.value = try {
                "«${manager.import(uri).name}» اضافه شد."
            } catch (e: Exception) {
                e.message ?: "وارد کردن فایل انجام نشد."
            } finally {
                _importing.value = false
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }
}
