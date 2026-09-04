package ir.ilam.inspection.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.ReportStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Backs the three tabs. Search applies to whichever tab is showing. */
@OptIn(ExperimentalCoroutinesApi::class)
class CaseListViewModel(container: AppContainer) : ViewModel() {

    private val repository = container.reportRepository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tab = MutableStateFlow(ReportStatus.PENDING)
    val tab: StateFlow<ReportStatus> = _tab.asStateFlow()

    val cases: StateFlow<List<ReportEntity>> = _tab
        .flatMapLatest { status ->
            _query.flatMapLatest { text ->
                if (text.isBlank()) {
                    when (status) {
                        ReportStatus.PENDING -> repository.observePending()
                        ReportStatus.VISITED -> repository.observeVisited()
                        ReportStatus.ARCHIVED -> repository.observeArchived()
                    }
                } else {
                    repository.search(status, text)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> = repository.countPending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun selectTab(status: ReportStatus) {
        _tab.value = status
    }

    fun search(text: String) {
        _query.value = text
    }

    fun daysWaiting(report: ReportEntity): Int = repository.daysWaiting(report)
}
