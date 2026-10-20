package ir.ilam.inspection.ui.persons

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.Persons
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.applyTicks
import ir.ilam.inspection.sync.decideRequest
import ir.ilam.inspection.sync.person
import ir.ilam.inspection.sync.personRequests
import ir.ilam.inspection.sync.persons
import ir.ilam.inspection.sync.resetPassword
import ir.ilam.inspection.sync.savePerson
import ir.ilam.inspection.sync.setDevice
import ir.ilam.inspection.ui.fielditems.FieldProblem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PersonsState(
    val query: String = "",
    val area: String? = null,
    val directory: Persons.Directory? = null,
    val detail: Persons.Detail? = null,
    val requests: List<Persons.Request> = emptyList(),
    /** New accounts' one-time passwords, shown once. */
    val made: List<Persons.Made> = emptyList(),
    val savedId: String? = null,
    val busy: Boolean = false,
    val problem: FieldProblem? = null
)

/**
 * The manager's people: the list by area and role, one person's page (details,
 * area × role ticks, install codes, work by area), and the request queue. All
 * live from the server, with the same rules as the web panel.
 */
class PersonsViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(PersonsState())
    val state: StateFlow<PersonsState> = _state.asStateFlow()
    private var searching: Job? = null

    fun load() = viewModelScope.launch {
        val s = _state.value
        run({ api, token -> api.persons(token, s.query, s.area) }) { value -> copy(directory = value) }
        run({ api, token -> api.personRequests(token) }) { value -> copy(requests = value) }
    }

    fun search(query: String) {
        _state.update { it.copy(query = query) }
        searching?.cancel()
        searching = viewModelScope.launch {
            delay(400)
            load()
        }
    }

    fun setArea(area: String?) {
        _state.update { it.copy(area = area) }
        load()
    }

    fun open(id: String) = viewModelScope.launch {
        _state.update { it.copy(detail = null, made = emptyList()) }
        run({ api, token -> api.person(token, id) }) { value -> copy(detail = value) }
    }

    fun save(id: String?, fields: Map<String, String>, onSaved: (String) -> Unit) = viewModelScope.launch {
        run({ api, token -> api.savePerson(token, id, fields) }) { value -> copy(savedId = value) }
        _state.value.savedId?.let { saved ->
            _state.update { it.copy(savedId = null) }
            onSaved(saved)
        }
    }

    fun applyTicks(personId: String, ticks: Set<String>, permissions: Map<String, Int>) = viewModelScope.launch {
        run({ api, token -> api.applyTicks(token, personId, ticks, permissions) }) { value -> copy(made = value) }
        reopen(personId)
    }

    fun resetPassword(personId: String, account: Persons.Account) = viewModelScope.launch {
        run({ api, token -> api.resetPassword(token, account.id) }) { value ->
            copy(made = listOf(Persons.Made(account.code, value, account.kindName, account.areaLabel)))
        }
        reopen(personId)
    }

    fun setDevice(personId: String, app: String, code: String) = viewModelScope.launch {
        run({ api, token -> api.setDevice(token, personId, app, code) }) { this }
        reopen(personId)
    }

    fun decide(request: Persons.Request, register: Boolean, onPerson: (String) -> Unit) = viewModelScope.launch {
        var opened: String? = null
        run({ api, token -> api.decideRequest(token, request.id, if (register) "register" else "reject") }) { value ->
            opened = value
            this
        }
        load()
        opened?.let(onPerson)
    }

    fun passwordsShown() = _state.update { it.copy(made = emptyList()) }

    private suspend fun reopen(personId: String) {
        val made = _state.value.made
        run({ api, token -> api.person(token, personId) }) { value -> copy(detail = value, made = made) }
    }

    private suspend fun <T> run(block: suspend (ServerApi, String) -> ApiResult<T>, change: PersonsState.(T) -> PersonsState) {
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
