package ir.ilam.inspection.ui.centers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.CenterAdmin
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.centerRuleDelete
import ir.ilam.inspection.sync.centerRuleSave
import ir.ilam.inspection.sync.centerRules
import ir.ilam.inspection.sync.centerSettings
import ir.ilam.inspection.sync.centerSettingsSave
import ir.ilam.inspection.sync.centerYearLock
import ir.ilam.inspection.ui.fielditems.FieldProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CenterAdminState(
    val rules: CenterAdmin.Rules? = null,
    /** The instruction being written; null while the list shows. */
    val draft: CenterAdmin.Draft? = null,
    val settings: CenterAdmin.Settings? = null,
    val settingsDirty: Boolean = false,
    val busy: Boolean = false,
    val problem: FieldProblem? = null,
    val message: Int? = null
)

/**
 * The manager's instructions and settings of the comprehensive statistics,
 * as on the web panel: write, correct or delete a reward instruction; tick who
 * sees which column and the export defaults; lock a year.
 */
class CenterAdminViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CenterAdminState())
    val state: StateFlow<CenterAdminState> = _state.asStateFlow()

    fun loadRules() = viewModelScope.launch { call({ api, t -> api.centerRules(t) }) { copy(rules = it) } }

    fun loadSettings() = viewModelScope.launch { call({ api, t -> api.centerSettings(t) }) { copy(settings = it, settingsDirty = false) } }

    /** A new instruction starts from the latest one, so only the changed numbers are typed. */
    fun startNew() = _state.update { s ->
        val latest = s.rules?.rules?.firstOrNull()?.draft
        s.copy(draft = latest?.copy(id = null, letterNo = "", letterId = "", letterDate = "", effectiveFrom = "", note = "")
            ?: CenterAdmin.Draft(roles = s.rules?.staffRoles.orEmpty().map { CenterAdmin.Role(it, "") }), message = null)
    }

    fun edit(rule: CenterAdmin.Rule) = _state.update { it.copy(draft = rule.draft, message = null) }

    fun change(block: CenterAdmin.Draft.() -> CenterAdmin.Draft) = _state.update { s -> s.copy(draft = s.draft?.block()) }

    fun closeDraft() = _state.update { it.copy(draft = null, problem = null) }

    fun saveDraft() = viewModelScope.launch {
        val draft = _state.value.draft ?: return@launch
        val ok = call({ api, t -> api.centerRuleSave(t, draft) }) { copy(rules = it) }
        if (ok) _state.update { it.copy(draft = null, message = R.string.ct_saved) }
    }

    fun delete(id: String) = viewModelScope.launch { call({ api, t -> api.centerRuleDelete(t, id) }) { copy(rules = it) } }

    fun changeSettings(block: CenterAdmin.Settings.() -> CenterAdmin.Settings) =
        _state.update { s -> s.copy(settings = s.settings?.block(), settingsDirty = true) }

    fun saveSettings() = viewModelScope.launch {
        val settings = _state.value.settings ?: return@launch
        if (call({ api, t -> api.centerSettingsSave(t, settings) }) { copy(settings = it, settingsDirty = false) }) {
            _state.update { it.copy(message = R.string.ct_saved) }
        }
    }

    fun lockYear(year: Int, lock: Boolean) = viewModelScope.launch {
        call({ api, t -> api.centerYearLock(t, year, lock) }) { copy(settings = settings?.copy(years = it.years) ?: it) }
    }

    private suspend fun <T> call(block: suspend (ServerApi, String) -> ApiResult<T>, change: CenterAdminState.(T) -> CenterAdminState): Boolean {
        _state.update { it.copy(busy = true, message = null) }
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
        return result is ApiResult.Ok
    }
}
