package ir.roozban.feature.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.ai.core.EngineState
import ir.roozban.ai.core.LlmException
import ir.roozban.ai.runtime.AssistantHost
import ir.roozban.ai.runtime.ModelManager
import ir.roozban.ai.tools.ActionResult
import ir.roozban.ai.tools.Assistant
import ir.roozban.ai.tools.AssistantContext
import ir.roozban.ai.tools.AssistantEvent
import ir.roozban.ai.tools.Plan
import ir.roozban.ai.tools.PlannedAction
import ir.roozban.ai.tools.ToolExecutor
import ir.roozban.ai.tools.Turn
import ir.roozban.core.domain.Access
import ir.roozban.core.domain.Entitlements
import ir.roozban.core.domain.HabitRepository
import ir.roozban.core.domain.ProFeature
import ir.roozban.core.domain.SettingsRepository
import ir.roozban.core.domain.TaskRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

enum class ChatRole { USER, ASSISTANT }

data class ChatItem(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val streaming: Boolean = false,
    /** Actions waiting for the user's approval. */
    val pending: Plan? = null,
    val results: List<ActionResult> = emptyList(),
    /** Actions the assistant could not do, with the reason. */
    val problems: List<PlannedAction> = emptyList(),
    val undone: Boolean = false,
    val error: Boolean = false,
) {
    val canUndo: Boolean get() = !undone && results.any { it.undo != null }
}

enum class EngineStatus { UNSUPPORTED, NO_MODEL, IDLE, LOADING, READY, FAILED }

data class AssistantUiState(
    val items: List<ChatItem> = emptyList(),
    val status: EngineStatus = EngineStatus.IDLE,
    val modelName: String? = null,
    val busy: Boolean = false,
    val access: Access = Access.FULL,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val host: AssistantHost,
    private val models: ModelManager,
    private val executor: ToolExecutor,
    private val tasks: TaskRepository,
    private val habits: HabitRepository,
    private val settings: SettingsRepository,
    entitlements: Entitlements,
    private val clock: Clock,
) : ViewModel() {
    private val items = MutableStateFlow<List<ChatItem>>(emptyList())
    private val busy = MutableStateFlow(false)
    private val history = mutableListOf<Turn>()
    private var nextId = 0L
    private var job: Job? = null
    private val access = entitlements.access(ProFeature.ASSISTANT)

    val state: StateFlow<AssistantUiState> = combine(items, busy, host.engine.state, models.state) { items, busy, engine, m ->
        val status = when {
            !m.tier.supported -> EngineStatus.UNSUPPORTED
            m.active == null -> EngineStatus.NO_MODEL
            engine is EngineState.Loading -> EngineStatus.LOADING
            engine is EngineState.Ready -> EngineStatus.READY
            engine is EngineState.Failed -> EngineStatus.FAILED
            else -> EngineStatus.IDLE
        }
        AssistantUiState(items, status, m.active?.name, busy, access)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState(access = access))

    init {
        // Warm the model up while the user types.
        if (models.state.value.active != null && models.tier.supported) {
            viewModelScope.launch { runCatching { host.ensureLoaded() } }
        }
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || busy.value || access != Access.FULL) return
        val answerId = nextId + 1
        items.update { it + ChatItem(nextId, ChatRole.USER, message) + ChatItem(answerId, ChatRole.ASSISTANT, "", streaming = true) }
        nextId += 2
        busy.value = true
        job = viewModelScope.launch {
            try {
                val loaded = host.ensureLoaded()
                val context = AssistantContext(
                    now = LocalDateTime.now(clock),
                    tasks = tasks.observeOpenTasks().first().filter { it.parentId == null },
                    habits = habits.all(),
                    settings = settings.current(),
                )
                Assistant(host.engine, loaded.model.template, loaded.config.contextTokens)
                    .ask(context, history.toList(), message)
                    .collect { event ->
                        when (event) {
                            is AssistantEvent.Partial -> edit(answerId) { it.copy(text = event.reply) }
                            is AssistantEvent.Complete -> {
                                history += Turn(message, event.raw)
                                while (history.size > MAX_HISTORY) history.removeAt(0)
                                finish(answerId, event.plan)
                            }
                        }
                    }
            } catch (e: CancellationException) {
                edit(answerId) { it.copy(streaming = false, text = it.text.ifEmpty { "متوقف شد." }) }
                throw e
            } catch (e: LlmException) {
                edit(answerId) { it.copy(streaming = false, error = true, text = e.message ?: "خطا در دستیار.") }
            } catch (e: Exception) {
                edit(answerId) { it.copy(streaming = false, error = true, text = "مشکلی پیش آمد؛ دوباره امتحان کن.") }
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun finish(id: Long, plan: Plan) {
        val problems = plan.actions.filter { !it.runnable }
        val reply = plan.reply.ifBlank { if (plan.actions.isEmpty()) "متوجه نشدم؛ کمی دقیق‌تر بگو." else "" }
        if (plan.needsConfirmation) {
            edit(id) { it.copy(text = reply, streaming = false, pending = plan, problems = problems) }
            return
        }
        val results = executor.execute(Plan(plan.actions.filter { it.runnable }, plan.reply))
        edit(id) { it.copy(text = reply, streaming = false, results = results, problems = problems) }
    }

    fun confirm(id: Long) {
        val plan = items.value.firstOrNull { it.id == id }?.pending ?: return
        edit(id) { it.copy(pending = null) }
        viewModelScope.launch {
            val results = executor.execute(Plan(plan.actions.filter { it.runnable }, plan.reply))
            edit(id) { it.copy(results = results) }
        }
    }

    fun reject(id: Long) = edit(id) { it.copy(pending = null, text = "باشه، کاری انجام ندادم.") }

    fun undo(id: Long) {
        val item = items.value.firstOrNull { it.id == id } ?: return
        if (!item.canUndo) return
        edit(id) { it.copy(undone = true) }
        viewModelScope.launch {
            item.results.reversed().forEach { r -> runCatching { r.undo?.invoke() } }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun clear() {
        stop()
        history.clear()
        items.value = emptyList()
    }

    private fun edit(id: Long, change: (ChatItem) -> ChatItem) {
        items.update { list -> list.map { if (it.id == id) change(it) else it } }
    }

    override fun onCleared() {
        host.release()
    }

    private companion object {
        const val MAX_HISTORY = 4
    }
}
