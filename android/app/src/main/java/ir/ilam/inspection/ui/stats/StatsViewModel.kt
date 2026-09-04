package ir.ilam.inspection.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.data.model.ReportType
import ir.ilam.inspection.export.ShareUtil
import ir.ilam.inspection.util.PersianDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StatsFilter(
    val status: ReportStatus? = null,
    val county: String? = null,
    val type: ReportType? = null,
    val expert: String? = null,
    val fromDate: Long? = null,
    val toDate: Long? = null
)

/** Counters for the stats screen and the filtered Excel export behind it. */
class StatsViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.reportRepository

    val total: StateFlow<Int> = repository.countAll().asState(0)
    val pending: StateFlow<Int> = repository.countPending().asState(0)
    val visited: StateFlow<Int> = repository.countVisited().asState(0)
    val archived: StateFlow<Int> = repository.countArchived().asState(0)
    val byType: StateFlow<Map<Int, Int>> = repository.countByType().asState(emptyMap())
    val byCounty: StateFlow<Map<String, Int>> = repository.countByCounty().asState(emptyMap())
    val totalPower: StateFlow<Double> = repository.totalPower().asState(0.0)

    private val _filter = MutableStateFlow(StatsFilter())
    val filter: StateFlow<StatsFilter> = _filter.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    val counties: List<String> = container.counties.defaults.map { it.name }

    fun setStatus(status: ReportStatus?) = update { it.copy(status = status) }
    fun setCounty(county: String?) = update { it.copy(county = county) }
    fun setType(type: ReportType?) = update { it.copy(type = type) }
    fun setFromDate(millis: Long?) = update { it.copy(fromDate = millis?.let(PersianDate::startOfDay)) }
    fun setToDate(millis: Long?) = update { it.copy(toDate = millis?.let(PersianDate::endOfDay)) }
    fun clearFilter() {
        _filter.value = StatsFilter()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun exportExcel(context: Context) {
        viewModelScope.launch {
            _busy.value = true
            val current = _filter.value
            val settings = container.settingsRepository.settings.first()
            val reports = repository.filter(
                status = current.status,
                county = current.county,
                type = current.type,
                expert = current.expert,
                fromDate = current.fromDate,
                toDate = current.toDate
            )
            val file = withContext(Dispatchers.IO) {
                val details = reports.mapNotNull { repository.detail(it.id) }
                runCatching {
                    container.excelExporter.export(
                        details = details,
                        fileName = "reports-" + PersianDate.trackingStamp(System.currentTimeMillis()),
                        expertName = settings.expertName
                    )
                }.getOrNull()
            }
            _busy.value = false
            if (file == null) {
                _message.value = R.string.export_failed
            } else {
                ShareUtil.share(context, file)
            }
        }
    }

    private fun update(mutate: (StatsFilter) -> StatsFilter) {
        _filter.value = mutate(_filter.value)
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
