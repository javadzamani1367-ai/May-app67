package ir.roozban.ai.runtime

import ir.roozban.ai.core.EngineConfig
import ir.roozban.ai.core.LlmException
import ir.roozban.ai.models.InstalledModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    /** Call when a screen using the model goes away. */
    fun release() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_MILLIS)
            engine.unload()
        }
    }

    private companion object {
        const val IDLE_MILLIS = 90_000L
    }
}
