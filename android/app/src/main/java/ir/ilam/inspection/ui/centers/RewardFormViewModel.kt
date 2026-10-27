package ir.ilam.inspection.ui.centers

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.export.FileOutbox
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.RewardForms
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.ServerFiles
import ir.ilam.inspection.sync.rewardForm
import ir.ilam.inspection.sync.rewardFormPost
import ir.ilam.inspection.sync.rewardFormRoute
import ir.ilam.inspection.ui.fielditems.FieldProblem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class RewardFormState(
    val form: RewardForms.Form? = null,
    /** The people as edited on the phone, until saved. */
    val people: List<RewardForms.Person> = emptyList(),
    val dirty: Boolean = false,
    val busy: Boolean = false,
    val problem: FieldProblem? = null,
    val message: Int? = null
)

/**
 * One centre's reward form in the manager app: tick who shares, choose names
 * from the list, set organisation, action and percentage, add or remove
 * people; save, make final, open again; the one-page Excel and Word saved on
 * the phone or sent.
 */
class RewardFormViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(RewardFormState())
    val state: StateFlow<RewardFormState> = _state.asStateFlow()
    private var row = ""
    private var kind = ""

    fun open(row: String, kind: String) {
        if (this.row == row && this.kind == kind && _state.value.form != null) return
        this.row = row
        this.kind = kind
        viewModelScope.launch { call { api, token -> api.rewardForm(token, row, kind) } }
    }

    fun edit(index: Int, change: RewardForms.Person.() -> RewardForms.Person) = _state.update { s ->
        s.copy(people = s.people.mapIndexed { i, p -> if (i == index) p.change() else p }, dirty = true)
    }

    fun add() = _state.update { s ->
        val org = if (kind == "enforcement") "faraja" else if (kind == "collection") "company" else ""
        s.copy(people = s.people + RewardForms.Person("", "", "", org, "", if (kind == "staff") 0.0 else null, "manager", true), dirty = true)
    }

    /** Only someone the manager added is removed; anyone else is unticked instead. */
    fun remove(index: Int) = _state.update { s -> s.copy(people = s.people.filterIndexed { i, _ -> i != index }, dirty = true) }

    fun save() = post(_state.value.people, null)

    fun finalize() = post(if (_state.value.dirty) _state.value.people else null, "final")

    fun unlock() = post(null, "unlock")

    fun export(context: Context, format: String) = viewModelScope.launch {
        _state.update { it.copy(busy = true, message = null) }
        val target = File(container.cacheDir, "centers/reward-$kind-${System.currentTimeMillis()}.$format")
        val server = container.settingsRepository.current().syncTarget
        val token = container.vault.serverToken()
        val ok = token != null && server.isNotBlank() && ServerFiles(server, token).downloadRoute(rewardFormRoute(row, kind, format), target)
        _state.update { it.copy(busy = false, message = if (ok) null else R.string.export_failed) }
        if (ok) FileOutbox.offer(target)
    }

    private fun post(people: List<RewardForms.Person>?, action: String?) = viewModelScope.launch {
        val result = call { api, token -> api.rewardFormPost(token, row, kind, people, action) }
        if (result is ApiResult.Ok) _state.update { it.copy(message = R.string.ct_saved) }
    }

    private suspend fun call(block: suspend (ServerApi, String) -> ApiResult<RewardForms.Form>): ApiResult<RewardForms.Form> {
        _state.update { it.copy(busy = true, message = null) }
        val api = ServerApi(container.settingsRepository.current().syncTarget)
        val token = container.vault.serverToken()
        val result = if (!api.configured || token == null) ApiResult.Unreachable else block(api, token)
        _state.update {
            when (result) {
                is ApiResult.Ok -> it.copy(form = result.value, people = result.value.people, dirty = false, busy = false, problem = null)
                is ApiResult.Refused -> it.copy(busy = false, problem = FieldProblem.Refused(result.message))
                ApiResult.Unreachable -> it.copy(busy = false, problem = FieldProblem.Offline)
            }
        }
        return result
    }
}
