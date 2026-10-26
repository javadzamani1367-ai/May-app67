package ir.ilam.inspection.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.data.model.ReportType
import ir.ilam.inspection.export.FileOutbox
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
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

    /**
     * One entry per **stored** county name, not one per area.
     *
     * Ilam is two areas, 401 and 402, and the intake picker shows them apart.
     * But what a case stores is the county name, so both areas are "ایلام" in
     * the database. Listing the catalog as-is put ایلام in this page twice,
     * each showing the same total, and gave the Excel filter two entries that
     * filtered identically.
     */
    val counties: List<String> = container.counties.defaults.map { it.name }.distinct()

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

    /** The filtered cases as Excel, or as Word for letters; then saved on the phone or sent. */
    fun export(context: Context, word: Boolean = false) {
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
            val name = "reports-" + PersianDate.trackingStamp(System.currentTimeMillis())
            val file = withContext(Dispatchers.IO) {
                val details = reports.mapNotNull { repository.detail(it.id) }
                runCatching {
                    if (word) container.excelExporter.word(details, name, settings.expertName, summary(context, details))
                    else container.excelExporter.export(details = details, fileName = name, expertName = settings.expertName)
                }.getOrNull()
            }
            _busy.value = false
            if (file == null) {
                _message.value = R.string.export_failed
            } else {
                FileOutbox.offer(file)
            }
        }
    }

    private fun summary(context: Context, details: List<ir.ilam.inspection.data.model.ReportDetail>): List<Pair<String, String>> {
        val count = { status: ReportStatus -> details.count { it.report.status == status.code } }
        return listOf(
            context.getString(R.string.stats_total) to PersianNumbers.toPersian(details.size),
            context.getString(R.string.stats_pending) to PersianNumbers.toPersian(count(ReportStatus.PENDING)),
            context.getString(R.string.stats_visited) to PersianNumbers.toPersian(count(ReportStatus.VISITED)),
            context.getString(R.string.stats_archived) to PersianNumbers.toPersian(count(ReportStatus.ARCHIVED)),
            context.getString(R.string.stats_total_power) to PersianNumbers.grouped(details.sumOf { it.totalPower }.toDouble())
        )
    }

    private fun update(mutate: (StatsFilter) -> StatsFilter) {
        _filter.value = mutate(_filter.value)
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.asState(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
