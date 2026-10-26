package ir.ilam.inspection.ui.centers

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.export.ShareUtil
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.Centers
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.ServerFiles
import ir.ilam.inspection.sync.centerApply
import ir.ilam.inspection.sync.centerCopy
import ir.ilam.inspection.sync.centerDelete
import ir.ilam.inspection.sync.centerExportRoute
import ir.ilam.inspection.sync.centerImport
import ir.ilam.inspection.sync.centerReplan
import ir.ilam.inspection.sync.centerRewards
import ir.ilam.inspection.sync.centerRewardsRoute
import ir.ilam.inspection.sync.centerRow
import ir.ilam.inspection.sync.centerSave
import ir.ilam.inspection.sync.centers
import ir.ilam.inspection.ui.fielditems.FieldProblem
import ir.ilam.inspection.util.PersianDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CentersState(
    val year: Int = PersianDate.today().year,
    val search: String = "",
    val page: Centers.Page? = null,
    val detail: Centers.Detail? = null,
    /** What the user typed into a cell, by column key, until saved. */
    val edits: Map<String, String> = emptyMap(),
    /** «system», «file» or «own» for a conflicting cell. */
    val choices: Map<String, String> = emptyMap(),
    val plan: Centers.Plan? = null,
    val sheets: Set<String> = emptySet(),
    val applied: Centers.Applied? = null,
    val rewards: Centers.Rewards? = null,
    val rewardKind: String = "staff",
    val busy: Boolean = false,
    val problem: FieldProblem? = null,
    val message: Int? = null
)

/**
 * Comprehensive centre statistics in the manager app (and the executor's, when
 * the manager allows it): a year's rows with charts, one row edited section by
 * section with the conflicts chosen, an Excel import from the phone, the
 * Tavanir-form Excel and Word shared from the phone, and the reward reports.
 */
class CentersViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CentersState())
    val state: StateFlow<CentersState> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        val s = _state.value
        call({ api, token -> api.centers(token, s.year, s.search, charts = true) }) { copy(page = it) }
    }

    fun setYear(year: Int) {
        _state.update { it.copy(year = year) }
        load()
    }

    fun setSearch(text: String) {
        _state.update { it.copy(search = text) }
        load()
    }

    fun open(id: String) = viewModelScope.launch {
        _state.update { it.copy(detail = null, edits = emptyMap(), choices = emptyMap(), message = null) }
        call({ api, token -> api.centerRow(token, id) }) { copy(detail = it) }
    }

    fun edit(key: String, text: String) = _state.update { it.copy(edits = it.edits + (key to text)) }

    fun choose(key: String, choice: String) = _state.update { it.copy(choices = it.choices + (key to choice)) }

    /** Typed cells, and the conflicts settled; «own» sends what is typed in the cell. */
    fun save() = viewModelScope.launch {
        val s = _state.value
        val row = s.detail?.row ?: return@launch
        val values = s.edits.filterKeys { s.choices[it] != "system" && s.choices[it] != "file" }
        val choices = s.choices.filterValues { it == "system" || it == "file" }
        call({ api, token -> api.centerSave(token, row.id, values, choices) }) {
            copy(detail = it, edits = emptyMap(), choices = emptyMap(), message = R.string.ct_saved)
        }
        if (_state.value.problem == null) load()
    }

    fun copyRow(onCreated: (String) -> Unit) = viewModelScope.launch {
        val row = _state.value.detail?.row ?: return@launch
        call({ api, token -> api.centerCopy(token, row.id) }) { this }.also { result -> (result as? ApiResult.Ok)?.let { onCreated(it.value) } }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        val row = _state.value.detail?.row ?: return@launch
        if (call({ api, token -> api.centerDelete(token, row.id) }) { this } is ApiResult.Ok) {
            load()
            onDone()
        }
    }

    /** The picked file's bytes go up; the preview comes back with every sheet it found. */
    fun import(context: Context, uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(plan = null, applied = null, message = null) }
        val bytes = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() }
        if (bytes == null || bytes.isEmpty()) {
            _state.update { it.copy(message = R.string.ct_file_unread) }
            return@launch
        }
        call({ api, token -> api.centerImport(token, bytes, _state.value.year) }) { plan ->
            copy(plan = plan, sheets = plan.sheets.filter { it.selected }.map { it.name }.toSet())
        }
    }

    fun toggleSheet(name: String) = viewModelScope.launch {
        val s = _state.value
        val plan = s.plan ?: return@launch
        val sheets = if (name in s.sheets) s.sheets - name else s.sheets + name
        _state.update { it.copy(sheets = sheets) }
        call({ api, token -> api.centerReplan(token, plan.token, sheets.toList()) }) { copy(plan = it) }
    }

    fun apply() = viewModelScope.launch {
        val s = _state.value
        val plan = s.plan ?: return@launch
        call({ api, token -> api.centerApply(token, plan.token, s.sheets.toList()) }) { copy(applied = it, plan = null) }
        load()
    }

    fun rewards(kind: String) = viewModelScope.launch {
        _state.update { it.copy(rewardKind = kind, rewards = null) }
        call({ api, token -> api.centerRewards(token, _state.value.year, kind) }) { copy(rewards = it) }
    }

    /** The Tavanir-form Excel or the Word of the ticked columns, shared from the phone. */
    fun export(context: Context, format: String) = share(context, centerExportRoute(_state.value.year, _state.value.search, format), format)

    fun exportRewards(context: Context) = share(context, centerRewardsRoute(_state.value.year, _state.value.rewardKind), "xlsx")

    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun share(context: Context, route: String, format: String) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        val target = File(container.cacheDir, "centers/centers-${_state.value.year}-${System.currentTimeMillis()}.$format")
        val server = container.settingsRepository.current().syncTarget
        val token = container.vault.serverToken()
        val ok = token != null && server.isNotBlank() && ServerFiles(server, token).downloadRoute(route, target)
        _state.update { it.copy(busy = false, message = if (ok) null else R.string.export_failed) }
        if (ok) ShareUtil.share(context, target)
    }

    private suspend fun <T> call(block: suspend (ServerApi, String) -> ApiResult<T>, change: CentersState.(T) -> CentersState): ApiResult<T> {
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
        return result
    }
}
