package ir.ilam.inspection.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.db.UserEntity
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.ServerApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UsersState(
    val busy: Boolean = false,
    val offline: Boolean = false,
    val messageRes: Int? = null,
    val serverMessage: String? = null,
    val requests: List<ServerApi.DeviceRequest> = emptyList()
)

/**
 * The manager's register of who may use the system.
 *
 * The server is the authority, because that is where a sign-in is checked
 * against a device code — a register kept only on this phone would look right
 * and enforce nothing. The local table is a copy, so the list can still be
 * read with no signal; saving cannot, and says so rather than pretending.
 */
class UsersViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.userRepository

    /** Read from the local copy so the screen has something without a network. */
    val users: StateFlow<List<UserEntity>> = repository.users
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(UsersState())
    val state: StateFlow<UsersState> = _state.asStateFlow()

    private val _editing = MutableStateFlow<UserEntity?>(null)
    val editing: StateFlow<UserEntity?> = _editing.asStateFlow()

    /** Typed only when the manager is issuing or resetting one. */
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    init {
        refresh()
    }

    fun startNew() {
        _password.value = ""
        _editing.value = UserEntity(userCode = "", fullName = "")
    }

    fun edit(user: UserEntity) {
        _password.value = ""
        _editing.value = user
    }

    fun change(user: UserEntity) {
        _editing.value = user
    }

    fun setPassword(value: String) {
        _password.value = value
    }

    fun cancel() {
        _editing.value = null
    }

    fun clearMessage() = _state.update { it.copy(messageRes = null, serverMessage = null) }

    /** Pulls the register and the waiting installations from the server. */
    fun refresh() {
        viewModelScope.launch {
            val api = api() ?: return@launch
            val token = container.vault.serverToken() ?: return@launch
            _state.update { it.copy(busy = true) }

            when (val remote = api.users(token)) {
                is ApiResult.Ok -> {
                    remote.value.forEach { user ->
                        repository.save(
                            UserEntity(
                                id = user.id,
                                userCode = user.userCode,
                                fullName = user.fullName,
                                county = user.county,
                                phone = user.phone,
                                deviceCode = user.deviceCode,
                                active = if (user.active) 1 else 0,
                                note = user.note
                            )
                        )
                    }
                    _state.update { it.copy(offline = false) }
                }
                is ApiResult.Refused ->
                    _state.update { it.copy(serverMessage = remote.message) }
                ApiResult.Unreachable -> _state.update { it.copy(offline = true) }
            }

            val requests = api.deviceRequests(token)
            _state.update {
                it.copy(
                    busy = false,
                    requests = (requests as? ApiResult.Ok)?.value.orEmpty()
                )
            }
        }
    }

    /** Fills the form from a waiting installation, device code and all. */
    fun adopt(request: ServerApi.DeviceRequest) {
        _password.value = ""
        _editing.value = UserEntity(
            userCode = "",
            fullName = request.fullName,
            county = request.county,
            phone = request.phone,
            deviceCode = request.deviceCode
        )
    }

    fun save() {
        val user = _editing.value ?: return
        if (user.userCode.isBlank() || user.fullName.isBlank()) {
            _state.update { it.copy(messageRes = R.string.users_incomplete) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, messageRes = null, serverMessage = null) }
            val api = api()
            val token = container.vault.serverToken()
            if (api == null || token == null) {
                // Saving locally would produce a register that enforces nothing.
                _state.update { it.copy(busy = false, messageRes = R.string.users_need_server) }
                return@launch
            }

            val result = api.saveUser(token, user.toRemote(), _password.value)
            _state.update { current ->
                when (result) {
                    is ApiResult.Ok -> current.copy(busy = false, messageRes = R.string.users_saved)
                    is ApiResult.Refused ->
                        current.copy(busy = false, serverMessage = result.message)
                    ApiResult.Unreachable ->
                        current.copy(busy = false, messageRes = R.string.users_need_server)
                }
            }
            if (result is ApiResult.Ok) {
                repository.save(user)
                _editing.value = null
                _password.value = ""
                refresh()
            }
        }
    }

    fun delete(user: UserEntity) {
        viewModelScope.launch {
            val api = api()
            val token = container.vault.serverToken()
            if (api == null || token == null) {
                _state.update { it.copy(messageRes = R.string.users_need_server) }
                return@launch
            }
            when (api.deactivateUser(token, user.id)) {
                is ApiResult.Ok -> {
                    repository.delete(user)
                    _state.update { it.copy(messageRes = R.string.users_deleted) }
                }
                is ApiResult.Refused, ApiResult.Unreachable ->
                    _state.update { it.copy(messageRes = R.string.users_need_server) }
            }
        }
    }

    private suspend fun api(): ServerApi? =
        ServerApi(container.settingsRepository.current().syncTarget).takeIf { it.configured }

    private fun UserEntity.toRemote(): ServerApi.RemoteUser = ServerApi.RemoteUser(
        id = id,
        userCode = userCode,
        fullName = fullName,
        county = county.orEmpty(),
        phone = phone.orEmpty(),
        deviceCode = deviceCode.orEmpty(),
        active = active == 1,
        note = note.orEmpty()
    )
}
