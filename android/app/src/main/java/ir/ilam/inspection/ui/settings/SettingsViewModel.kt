package ir.ilam.inspection.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.repo.AppSettings
import ir.ilam.inspection.export.ShareUtil
import ir.ilam.inspection.sync.ServerCaseSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Expert identity, area codes, media quality, and the sync controls. */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.settingsRepository

    /** Shown so the manager can be told what to register against this phone. */
    val deviceCode: String = container.vault.deviceCodeForDisplay()

    /** Set at login and carried by every report this phone files. */
    val userCode: String = container.vault.userCode().orEmpty()

    val settings: StateFlow<AppSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val syncRunning: StateFlow<Boolean> = container.syncService.running
    val pairingCode: StateFlow<String> = container.syncService.pairingCode
    /** Waiting for the Windows archive — `synced_at` on the case itself. */
    val pendingSync: StateFlow<Int> = container.syncService.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Waiting for the server, which is a different number: the two routes keep
     * separate watermarks, so a case sent to the office over the internet still
     * has to reach the Windows archive, and the screen must not claim otherwise.
     */
    val pendingServer: StateFlow<Int> = container.database.serverSyncDao().pendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _address = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = _address.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    private val _serverBusy = MutableStateFlow(false)
    val serverBusy: StateFlow<Boolean> = _serverBusy.asStateFlow()

    private val _serverOutcome = MutableStateFlow<ServerCaseSync.Outcome?>(null)
    val serverOutcome: StateFlow<ServerCaseSync.Outcome?> = _serverOutcome.asStateFlow()

    private val _serverLastRun = MutableStateFlow<Long?>(null)
    val serverLastRun: StateFlow<Long?> = _serverLastRun.asStateFlow()

    /** Shown after a package is built: the archive operator has to type it. */
    private val _packagePassword = MutableStateFlow<String?>(null)
    val packagePassword: StateFlow<String?> = _packagePassword.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun setExpert(code: String, name: String) = launchSaving {
        repository.setExpert(code.trim(), name.trim())
    }

    fun setDefaultArea(code: String) = launchSaving { repository.setDefaultAreaCode(code.trim()) }

    fun setSyncTarget(target: String) = launchSaving { repository.setSyncTarget(target.trim()) }

    fun setMediaQuality(quality: Int) = launchSaving { repository.setMediaQuality(quality) }


    fun toggleServer() {
        viewModelScope.launch {
            if (container.syncService.running.value) {
                container.syncService.stop()
                _address.value = null
            } else {
                container.syncService.start()
                _address.value = container.syncService.address()
                if (_address.value == null) _message.value = R.string.sync_no_wifi
            }
        }
    }

    /** Builds the encrypted offline package and offers it to the share sheet. */
    fun exportPackage(context: Context) {
        viewModelScope.launch {
            val ids = container.syncService.pendingIds()
            val file = container.syncService.exportPackage(container.vault)
            if (file == null) {
                _message.value = R.string.export_failed
                return@launch
            }
            container.syncService.acknowledgeExported(ids)
            _packagePassword.value = container.vault.packagePassword()
            _message.value = R.string.sync_package_done
            ShareUtil.share(context, file)
        }
    }

    /**
     * Sends this phone's cases to the server, and on the manager's app fetches
     * everyone else's.
     *
     * The result is kept and shown rather than reduced to a tick: rows and
     * files travel separately, and "five cases sent, no photographs" is a
     * different situation from "everything sent" to someone deciding whether
     * they can leave a place that has signal.
     */
    fun syncWithServer() {
        if (_serverBusy.value) return
        viewModelScope.launch {
            _serverBusy.value = true
            val outcome = container.serverCaseSync.run()
            _serverOutcome.value = outcome
            if (outcome.reachedServer) _serverLastRun.value = System.currentTimeMillis()
            _serverBusy.value = false
        }
    }

    fun changePin(pin: String) {
        container.vault.setPin(pin)
        _message.value = R.string.settings_saved
    }

    private fun launchSaving(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            _message.value = R.string.settings_saved
        }
    }
}
