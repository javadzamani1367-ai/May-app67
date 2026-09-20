package ir.ilam.inspection.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.repo.SignInOutcome
import ir.ilam.inspection.sync.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LockState(
    val busy: Boolean = false,
    /** A message already in Persian, straight from the server. */
    val serverMessage: String? = null,
    val errorRes: Int? = null,
    val noticeRes: Int? = null,
    val askingRegistration: Boolean = false,
    val editingServer: Boolean = false,
    val serverAddress: String = "",
    val unlocked: Boolean = false
)

/** The entry screen's brain: sign in, or ask the manager to register this phone. */
class LockViewModel(private val container: AppContainer) : ViewModel() {

    private val account = container.accountRepository
    private val settings = container.settingsRepository

    private val _state = MutableStateFlow(LockState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    val deviceCode: String get() = account.deviceCode

    fun dismissMessages() = _state.update {
        it.copy(serverMessage = null, errorRes = null, noticeRes = null)
    }

    init {
        // Shown on the entry screen so the address can be corrected from here.
        viewModelScope.launch {
            val stored = settings.current().syncTarget
            _state.update { it.copy(serverAddress = stored) }
        }
    }

    fun openRegistration() = _state.update { it.copy(askingRegistration = true) }

    /**
     * The server address, editable from the entry screen.
     *
     * It lives in settings, and settings are behind this screen — so a wrong
     * address, or an account the server does not know, left the phone with no
     * way back to the one field that fixes it. The address is not a secret and
     * knowing it grants nothing, so correcting it here costs nothing.
     */
    fun openServerAddress() = _state.update { it.copy(editingServer = true) }

    fun closeServerAddress() = _state.update { it.copy(editingServer = false) }

    fun saveServerAddress(value: String) {
        viewModelScope.launch {
            val trimmed = value.trim()
            settings.setSyncTarget(trimmed)
            _state.update {
                it.copy(
                    serverAddress = trimmed,
                    editingServer = false,
                    noticeRes = R.string.settings_saved
                )
            }
        }
    }

    fun closeRegistration() = _state.update { it.copy(askingRegistration = false) }

    fun signIn(userCode: String, password: String) {
        if (userCode.isBlank()) {
            _state.update { it.copy(errorRes = R.string.lock_missing_user_code) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, serverMessage = null, errorRes = null) }
            val outcome = account.signIn(userCode.trim(), password)
            _state.update { current ->
                when (outcome) {
                    SignInOutcome.Granted -> current.copy(busy = false, unlocked = true)
                    SignInOutcome.WrongPassword ->
                        current.copy(busy = false, errorRes = R.string.lock_wrong_password)
                    SignInOutcome.NeedsActivation ->
                        current.copy(busy = false, errorRes = R.string.lock_needs_activation)
                    SignInOutcome.PasswordTooShort ->
                        current.copy(busy = false, errorRes = R.string.lock_short_password)
                    is SignInOutcome.Refused ->
                        current.copy(busy = false, serverMessage = outcome.message)
                }
            }
        }
    }

    fun requestRegistration(fullName: String, phone: String, county: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, serverMessage = null, errorRes = null) }
            val result = account.requestRegistration(fullName.trim(), phone.trim(), county.trim())
            _state.update { current ->
                when (result) {
                    is ApiResult.Ok -> current.copy(
                        busy = false,
                        askingRegistration = false,
                        noticeRes = R.string.lock_registration_sent
                    )
                    is ApiResult.Refused -> current.copy(busy = false, serverMessage = result.message)
                    ApiResult.Unreachable -> current.copy(
                        busy = false,
                        errorRes = R.string.lock_server_unreachable
                    )
                }
            }
        }
    }
}
