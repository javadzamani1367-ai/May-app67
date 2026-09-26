package ir.ilam.inspection.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.db.DashboardCounts
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.repo.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Everything the dashboard shows, as one value so the screen draws once per change. */
data class DashboardState(
    val counts: DashboardCounts = DashboardCounts(),
    val byType: Map<Int, Int> = emptyMap(),
    val recent: List<ReportEntity> = emptyList(),
    /** The cases that have waited longest, for "needs attention". */
    val oldestPending: List<ReportEntity> = emptyList(),
    val deviceCounts: Map<String, Int> = emptyMap(),
    val serverPending: Int = 0,
    val settings: AppSettings = AppSettings()
)

/**
 * The dashboard's numbers, all computed on the phone from its own database.
 * Nothing here needs a network: a dashboard that went blank in a village with
 * no signal would be a dashboard nobody trusted in the office either.
 */
class DashboardViewModel(container: AppContainer) : ViewModel() {

    private val repository = container.reportRepository

    val state: StateFlow<DashboardState> = combine(
        repository.dashboard(),
        repository.countByType(),
        repository.recentVisits(RECENT),
        repository.observePending().map { it.take(ATTENTION) },
        combine(
            repository.deviceCounts(),
            container.database.serverSyncDao().pendingCount(),
            container.settingsRepository.settings
        ) { devices, serverPending, settings -> Triple(devices, serverPending, settings) }
    ) { counts, byType, recent, oldest, (devices, serverPending, settings) ->
        DashboardState(
            counts = counts,
            byType = byType,
            recent = recent,
            oldestPending = oldest,
            deviceCounts = devices,
            serverPending = serverPending,
            settings = settings
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    fun daysWaiting(report: ReportEntity): Int = repository.daysWaiting(report)

    private companion object {
        const val RECENT = 5
        const val ATTENTION = 3
    }
}
