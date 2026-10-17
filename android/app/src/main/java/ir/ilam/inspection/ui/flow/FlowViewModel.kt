package ir.ilam.inspection.ui.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.Flow
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.ServerSyncWorker
import ir.ilam.inspection.sync.flowBoard
import ir.ilam.inspection.sync.flowFollowUp
import ir.ilam.inspection.sync.flowIntake
import ir.ilam.inspection.sync.flowPeople
import ir.ilam.inspection.sync.flowSummary
import ir.ilam.inspection.sync.flowToExecutor
import ir.ilam.inspection.sync.flowToExpert
import ir.ilam.inspection.ui.fielditems.FieldProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BoardState(
    val board: Flow.Board? = null,
    val stage: String? = null,
    val busy: Boolean = false,
    val problem: FieldProblem? = null
) {
    val shown: List<Flow.BoardCase>
        get() = board?.cases.orEmpty().filter { stage == null || it.stage == stage || (stage == LATE && it.late) }

    companion object {
        const val LATE = "late"
    }
}

data class CaseFlowState(
    val summary: Flow.Summary? = null,
    val people: Flow.People? = null,
    val busy: Boolean = false,
    val problem: FieldProblem? = null,
    val saved: Boolean = false
)

data class IntakeState(
    val people: Flow.People? = null,
    val busy: Boolean = false,
    val problem: FieldProblem? = null,
    /** The new case's id once the server took it; the screen moves on to its flow. */
    val created: String? = null,
    val warning: String? = null
)

/**
 * The follow-up board, one case's journey and registering a report — all
 * live from the server, which decides by the account's role and areas what
 * comes back. Nothing here is stored on the phone; a case registered here
 * reaches the phone's archive with the next sync, which is started at once.
 */
class FlowViewModel(private val container: AppContainer) : ViewModel() {

    private val _board = MutableStateFlow(BoardState())
    val board: StateFlow<BoardState> = _board.asStateFlow()

    private val _case = MutableStateFlow(CaseFlowState())
    val case: StateFlow<CaseFlowState> = _case.asStateFlow()

    private val _intake = MutableStateFlow(IntakeState())
    val intake: StateFlow<IntakeState> = _intake.asStateFlow()

    fun showStage(stage: String?) = _board.update { it.copy(stage = stage) }

    fun loadBoard() = viewModelScope.launch {
        _board.update { it.copy(busy = true) }
        val result = call { api, token -> api.flowBoard(token) }
        _board.update { state ->
            when (result) {
                is ApiResult.Ok -> state.copy(board = result.value, busy = false, problem = null)
                else -> state.copy(busy = false, problem = problemOf(result))
            }
        }
    }

    fun openCase(reportId: String) = viewModelScope.launch {
        if (_case.value.summary?.let { it.code } == null) _case.value = CaseFlowState()
        _case.update { it.copy(busy = true, saved = false) }
        val result = call { api, token -> api.flowSummary(token, reportId) }
        if (result !is ApiResult.Ok) return@launch _case.update { it.copy(busy = false, problem = problemOf(result)) }
        _case.update { it.copy(summary = result.value, busy = false, problem = null) }
        val people = call { api, token -> api.flowPeople(token, result.value.areaCode) }
        if (people is ApiResult.Ok) _case.update { it.copy(people = people.value) }
    }

    fun toExecutor(reportId: String, executorId: String?, note: String, onDone: () -> Unit) =
        act(onDone) { api, token -> api.flowToExecutor(token, reportId, executorId, note) }

    fun toExpert(reportId: String, expertId: String, dueDays: Int?, note: String, onDone: () -> Unit) =
        act(onDone) { api, token -> api.flowToExpert(token, reportId, expertId, dueDays, note) }

    fun followUp(reportId: String, note: String, onDone: () -> Unit) =
        act(onDone) { api, token -> api.flowFollowUp(token, reportId, note) }

    fun openIntake() = viewModelScope.launch {
        _intake.value = IntakeState(busy = true)
        val result = call { api, token -> api.flowPeople(token) }
        _intake.update {
            if (result is ApiResult.Ok) it.copy(people = result.value, busy = false) else it.copy(busy = false, problem = problemOf(result))
        }
    }

    fun register(fields: Map<String, String>, context: android.content.Context) = viewModelScope.launch {
        _intake.update { it.copy(busy = true, problem = null) }
        val result = call { api, token -> api.flowIntake(token, fields) }
        when (result) {
            is ApiResult.Ok -> {
                _intake.update { it.copy(busy = false, created = result.value.first, warning = result.value.second) }
                // Brings the new case into this phone's archive without waiting for the next scheduled run.
                ServerSyncWorker.runNow(context)
            }
            else -> _intake.update { it.copy(busy = false, problem = problemOf(result)) }
        }
    }

    fun intakeShown() = _intake.update { it.copy(created = null) }

    private fun act(onDone: () -> Unit, block: suspend (ServerApi, String) -> ApiResult<Flow.Summary>) = viewModelScope.launch {
        _case.update { it.copy(busy = true, saved = false) }
        when (val result = call(block)) {
            is ApiResult.Ok -> {
                _case.update { it.copy(summary = result.value, busy = false, problem = null, saved = true) }
                onDone()
            }
            else -> _case.update { it.copy(busy = false, problem = problemOf(result)) }
        }
    }

    private suspend fun <T> call(block: suspend (ServerApi, String) -> ApiResult<T>): ApiResult<T> {
        val api = ServerApi(container.settingsRepository.current().syncTarget)
        val token = container.vault.serverToken()
        if (!api.configured || token == null) return ApiResult.Unreachable
        return block(api, token)
    }

    private fun problemOf(result: ApiResult<*>): FieldProblem = when (result) {
        is ApiResult.Refused -> FieldProblem.Refused(result.message)
        else -> FieldProblem.Offline
    }
}
