package ir.ilam.inspection.ui.approvals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.ServerApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApprovalsState(
    val busy: Boolean = false,
    val offline: Boolean = false,
    /** Waiting cases the server knows about, including ones not on this phone. */
    val remote: List<ServerApi.PendingApproval> = emptyList()
)

/**
 * The manager's queue of cases waiting for a decision.
 *
 * Two sources, because neither alone is complete. This phone's own database
 * holds the cases it has synced, and those can be opened and decided offline.
 * The server knows about every expert's submissions, including cases this
 * phone has never seen — those are listed so the manager at least knows they
 * are waiting, rather than believing the queue is empty.
 */
class ApprovalsViewModel(private val container: AppContainer) : ViewModel() {

    val local: StateFlow<List<ReportEntity>> = container.reportRepository.observePendingApproval()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(ApprovalsState())
    val state: StateFlow<ApprovalsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val api = ServerApi(container.settingsRepository.current().syncTarget)
            val token = container.vault.serverToken()
            if (!api.configured || token == null) {
                _state.update { it.copy(offline = true) }
                return@launch
            }
            _state.update { it.copy(busy = true) }
            when (val result = api.pendingApprovals(token)) {
                is ApiResult.Ok ->
                    _state.update { it.copy(busy = false, offline = false, remote = result.value) }
                is ApiResult.Refused, ApiResult.Unreachable ->
                    _state.update { it.copy(busy = false, offline = true) }
            }
        }
    }
}
