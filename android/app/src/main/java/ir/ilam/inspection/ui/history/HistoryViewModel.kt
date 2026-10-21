package ir.ilam.inspection.ui.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.export.ShareUtil
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.Flow
import ir.ilam.inspection.sync.Readings
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.ServerFiles
import ir.ilam.inspection.sync.flowPeople
import ir.ilam.inspection.sync.historyExportRoute
import ir.ilam.inspection.sync.historyImageRoute
import ir.ilam.inspection.sync.historyRefer
import ir.ilam.inspection.sync.historySearch
import ir.ilam.inspection.sync.historySeries
import ir.ilam.inspection.ui.fielditems.FieldProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class HistoryState(
    val filter: Readings.Filter = Readings.Filter(),
    val search: Readings.Search? = null,
    val key: Readings.Key? = null,
    val label: String = "",
    val series: List<Readings.Row> = emptyList(),
    /** Crypto experts of the plate's area, for a referral. */
    val experts: List<Flow.Person> = emptyList(),
    val busy: Boolean = false,
    val problem: FieldProblem? = null,
    val message: Int? = null
)

/**
 * The inspection history, live from the server: search by plate and area, one
 * plate's readings with a chart, Excel and Word (manager and headquarters), and a
 * referral for a crypto inspection (manager and the area's crypto executor).
 */
class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    fun setFilter(filter: Readings.Filter) {
        _state.update { it.copy(filter = filter) }
        load()
    }

    fun load() = viewModelScope.launch {
        val filter = _state.value.filter
        call({ api, token -> api.historySearch(token, filter) }) { copy(search = it) }
    }

    fun open(summary: Readings.Summary) = viewModelScope.launch {
        _state.update { it.copy(key = summary.key, label = summary.label, series = emptyList(), experts = emptyList(), message = null) }
        call({ api, token -> api.historySeries(token, summary.key) }) { copy(series = it) }
        if (_state.value.search?.canRefer == true && summary.key.area != null) {
            call({ api, token -> api.flowPeople(token, summary.key.area) }) { people -> copy(experts = people.experts) }
        }
    }

    fun refer(expertId: String, note: String) = viewModelScope.launch {
        val key = _state.value.key ?: return@launch
        call({ api, token -> api.historyRefer(token, key, expertId, note) }) { copy(message = R.string.hist_referred) }
    }

    /** Excel or Word of the current search, shared from the phone. */
    fun export(context: Context, format: String) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        val target = File(container.cacheDir, "history/history-${System.currentTimeMillis()}.$format")
        val ok = download(historyExportRoute(_state.value.filter, format), target)
        _state.update { it.copy(busy = false, message = if (ok) null else R.string.export_failed) }
        if (ok) ShareUtil.share(context, target)
    }

    /** A thermal picture of an earlier inspection, cached; null when it cannot be had. */
    suspend fun image(fileId: String): File? {
        val target = File(container.cacheDir, "history/$fileId.jpg")
        return target.takeIf { it.exists() && it.length() > 0 || download(historyImageRoute(fileId), it) }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    private suspend fun download(route: String, target: File): Boolean {
        val server = container.settingsRepository.current().syncTarget
        val token = container.vault.serverToken() ?: return false
        return server.isNotBlank() && ServerFiles(server, token).downloadRoute(route, target)
    }

    private suspend fun <T> call(block: suspend (ServerApi, String) -> ApiResult<T>, change: HistoryState.(T) -> HistoryState) {
        _state.update { it.copy(busy = true) }
        val api = ServerApi(container.settingsRepository.current().syncTarget)
        val token = container.vault.serverToken()
        val result = if (!api.configured || token == null) ApiResult.Unreachable else block(api, token)
        _state.update {
            when (result) {
                is ApiResult.Ok -> it.change(result.value).copy(busy = false, problem = null)
                is ApiResult.Refused -> it.copy(busy = false, problem = FieldProblem.Refused(result.message))
                ApiResult.Unreachable -> it.copy(busy = false, problem = FieldProblem.Offline)
            }
        }
    }
}
