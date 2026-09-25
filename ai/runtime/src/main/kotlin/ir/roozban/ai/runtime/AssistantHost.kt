package ir.roozban.ai.runtime

import ir.roozban.ai.core.EngineConfig
import ir.roozban.ai.core.EngineState
import ir.roozban.ai.core.LlmException
import ir.roozban.ai.models.InstalledModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class LoadedModel(val model: InstalledModel, val config: EngineConfig)

/**
 * Keeps the active model loaded while the assistant is in use and unloads it shortly after the
 * last screen leaves, so the memory goes back to the phone.
 */
@Singleton
class AssistantHost @Inject constructor(
    val engine: RemoteLlmEngine,
    private val models: ModelManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleJob: Job? = null

    /** Loads the active model if needed. Throws [LlmException] with a Persian message. */
    suspend fun ensureLoaded(): LoadedModel {
        idleJob?.cancel()
        if (!models.tier.supported) throw LlmException("حافظهٔ این گوشی برای دستیار کافی نیست.")
        val model = models.state.value.active ?: throw LlmException("هنوز مدلی نصب نشده است.")
        if (!model.file.exists()) {
            models.refresh()
            throw LlmException("فایل مدل پیدا نشد.")
        }
        if (!engine.isAvailable()) throw LlmException("موتور دستیار روی این گوشی اجرا نمی‌شود.")
        val config = models.engineConfig()
        try {
            engine.load(model.file.path, config)
        } catch (e: LlmException) {
            throw LlmException("بارگذاری مدل انجام نشد: ${e.message}")
        }
        return LoadedModel(model, config)
    }

    private var warmedKey: String? = null

    private val _warmProgress = MutableStateFlow<Int?>(null)

    /** 0..100 while the fixed prompt is being prepared; null otherwise. */
    val warmProgress: StateFlow<Int?> = _warmProgress.asStateFlow()

    /**
     * Prepares the fixed prompt [prefix] ahead of the first message (while the user types), so
     * each answer only processes the new part. The result is kept in a file next to the model,
     * so later sessions restore it in about a second instead of recomputing it.
     */
    suspend fun warmUp(loaded: LoadedModel, prefix: String) {
        val key = sha256(prefix + "|" + loaded.config).take(16)
        if (warmedKey == key && engine.state.value is EngineState.Ready) return
        val model = loaded.model.file
        val cache = File(model.path + ".prefix-" + key + ".kv")
        // Older caches of this model (from an earlier prompt version) are dead weight.
        model.parentFile?.listFiles { f -> f.name.startsWith(model.name + ".prefix-") && f != cache }?.forEach { it.delete() }
        _warmProgress.value = 0
        try {
            engine.warmUp(prefix, cache.path) { _warmProgress.value = it }
            warmedKey = key
        } finally {
            _warmProgress.value = null
        }
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        .joinToString("") { "%02x".format(it) }

    /** Call when a screen using the model goes away. */
    fun release() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_MILLIS)
            engine.unload()
            warmedKey = null
        }
    }

    private companion object {
        /** Five minutes: coming back to the assistant soon should not reload the model. */
        const val IDLE_MILLIS = 5 * 60_000L
    }
}
