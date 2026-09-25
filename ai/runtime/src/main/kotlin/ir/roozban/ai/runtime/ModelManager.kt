package ir.roozban.ai.runtime

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.ai.core.DeviceTier
import ir.roozban.ai.core.EngineConfig
import ir.roozban.ai.core.LlmException
import ir.roozban.ai.models.InstalledModel
import ir.roozban.ai.models.ModelCatalog
import ir.roozban.ai.models.ModelKind
import ir.roozban.ai.models.ModelSpec
import ir.roozban.ai.models.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DownloadState {
    data class Running(val downloaded: Long, val total: Long) : DownloadState {
        val fraction: Float get() = if (total <= 0) 0f else (downloaded.toFloat() / total).coerceIn(0f, 1f)
    }

    data class Failed(val message: String, val partialBytes: Long) : DownloadState
}

data class ModelsState(
    val tier: DeviceTier,
    /** Assistant (language) models on the phone. */
    val installed: List<InstalledModel> = emptyList(),
    val activeId: String? = null,
    /** Speech models on the phone. */
    val speechInstalled: List<InstalledModel> = emptyList(),
    val activeSpeechId: String? = null,
    /** Catalog id → an ongoing or failed download. */
    val downloads: Map<String, DownloadState> = emptyMap(),
    /** Off by default: mobile data is allowed, with a size confirmation in the model screen. */
    val wifiOnly: Boolean = false,
) {
    val active: InstalledModel? get() = installed.firstOrNull { it.id == activeId } ?: installed.firstOrNull()

    val available: List<ModelSpec> get() = ModelCatalog.availableFor(tier)

    /** The speech model voice input uses; models from before the Persian fine-tunes are never used. */
    val activeSpeech: InstalledModel?
        get() {
            val usable = speechInstalled.filter { !ModelCatalog.isLegacySpeech(it.id) || ModelCatalog.get(it.id) != null }
            return usable.firstOrNull { it.id == activeSpeechId } ?: usable.firstOrNull()
        }

    val speechAvailable: List<ModelSpec> get() = ModelCatalog.speechFor(tier)
}

/** Models on this device: catalog downloads, imports, and which one the assistant uses. */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("ai", Context.MODE_PRIVATE)
    val store = ModelStore(File(context.filesDir, "models"))
    val tier: DeviceTier = DeviceTier.of(totalRam())

    private val _state = MutableStateFlow(ModelsState(tier))
    val state: StateFlow<ModelsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val all = store.installed()
        val installed = all.filter { it.kind == ModelKind.LLM }
        val speech = all.filter { it.kind == ModelKind.SPEECH }
        val failed = (ModelCatalog.all + ModelCatalog.speech).mapNotNull { spec ->
            val part = store.partialBytes(spec)
            if (part > 0 && all.none { it.id == spec.id }) spec.id to DownloadState.Failed("", part) else null
        }.toMap()
        _state.update { s ->
            s.copy(
                installed = installed,
                activeId = prefs.getString(KEY_ACTIVE, null),
                speechInstalled = speech,
                activeSpeechId = prefs.getString(KEY_ACTIVE_SPEECH, null),
                wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, false),
                downloads = failed + s.downloads.filterValues { it is DownloadState.Running },
            )
        }
    }

    fun setActive(id: String) {
        prefs.edit { putString(KEY_ACTIVE, id) }
        refresh()
    }

    fun setActiveSpeech(id: String) {
        prefs.edit { putString(KEY_ACTIVE_SPEECH, id) }
        refresh()
    }

    fun setWifiOnly(value: Boolean) {
        prefs.edit { putBoolean(KEY_WIFI_ONLY, value) }
        refresh()
    }

    /** True when the current network may be used for a download under the Wi-Fi-only rule. */
    fun networkAllowed(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return !_state.value.wifiOnly || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** True on mobile data or another metered network. */
    fun isMetered(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun freeSpaceBytes(): Long = store.dir.apply { mkdirs() }.usableSpace

    fun startDownload(spec: ModelSpec) {
        _state.update { it.copy(downloads = it.downloads + (spec.id to DownloadState.Running(store.partialBytes(spec), spec.sizeBytes))) }
        ContextCompat.startForegroundService(context, ModelDownloadService.startIntent(context, spec.id))
    }

    fun cancelDownload(spec: ModelSpec) {
        context.startService(ModelDownloadService.cancelIntent(context))
        _state.update { s -> s.copy(downloads = s.downloads - spec.id) }
        refresh()
    }

    fun discardPartial(spec: ModelSpec) {
        store.deletePartial(spec)
        _state.update { s -> s.copy(downloads = s.downloads - spec.id) }
    }

    internal fun onProgress(spec: ModelSpec, downloaded: Long, total: Long) {
        _state.update { it.copy(downloads = it.downloads + (spec.id to DownloadState.Running(downloaded, total))) }
    }

    internal fun onFinished(spec: ModelSpec, error: String?) {
        if (error == null) {
            store.recordDownload(spec)
            val key = if (spec.kind == ModelKind.SPEECH) KEY_ACTIVE_SPEECH else KEY_ACTIVE
            if (prefs.getString(key, null) == null) prefs.edit { putString(key, spec.id) }
            _state.update { it.copy(downloads = it.downloads - spec.id) }
        } else {
            _state.update { it.copy(downloads = it.downloads + (spec.id to DownloadState.Failed(error, store.partialBytes(spec)))) }
        }
        val failed = _state.value.downloads
        refresh()
        if (error != null) _state.update { it.copy(downloads = failed) }
    }

    /** Copies a user-picked GGUF into the app. Throws with a message fit for the user. */
    suspend fun import(uri: Uri): InstalledModel = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) to c.getLong(1) else null
        }
        val size = name?.second ?: 0
        if (size > 0 && size > freeSpaceBytes() - SPACE_MARGIN) throw LlmException("فضای کافی روی گوشی نیست.")
        val model = try {
            store.import(name?.first.orEmpty()) { resolver.openInputStream(uri) ?: throw LlmException("فایل باز نشد.") }
        } catch (e: ir.roozban.ai.models.InvalidModelException) {
            throw LlmException("این فایل مدل GGUF معتبری نیست.")
        }
        if (prefs.getString(KEY_ACTIVE, null) == null) prefs.edit { putString(KEY_ACTIVE, model.id) }
        refresh()
        model
    }

    fun delete(id: String) {
        store.delete(id)
        if (prefs.getString(KEY_ACTIVE, null) == id) prefs.edit { remove(KEY_ACTIVE) }
        if (prefs.getString(KEY_ACTIVE_SPEECH, null) == id) prefs.edit { remove(KEY_ACTIVE_SPEECH) }
        refresh()
    }

    /** Engine settings for this device. */
    fun engineConfig(): EngineConfig = EngineConfig(
        contextTokens = tier.contextTokens.coerceAtLeast(2048),
        threads = DeviceTier.threadsFor(Runtime.getRuntime().availableProcessors()),
        batchTokens = 256,
    )

    private fun totalRam(): Long {
        val am = context.getSystemService(ActivityManager::class.java) ?: return 0
        return ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.totalMem
    }

    companion object {
        private const val KEY_ACTIVE = "active_model"
        private const val KEY_ACTIVE_SPEECH = "active_speech_model"
        private const val KEY_WIFI_ONLY = "wifi_only"

        /** Kept free beyond the model itself. */
        const val SPACE_MARGIN = 200L * 1024 * 1024
    }
}
