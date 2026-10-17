package ir.ilam.inspection.ui.flow

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.export.ShareUtil
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.Manage
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.ServerFiles
import ir.ilam.inspection.sync.deviceAlerts
import ir.ilam.inspection.sync.deviceHistory
import ir.ilam.inspection.sync.devices
import ir.ilam.inspection.sync.markNotificationsRead
import ir.ilam.inspection.sync.notifications
import ir.ilam.inspection.sync.scores
import ir.ilam.inspection.ui.fielditems.FieldProblem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ScoresState(val days: Int = 30, val tables: List<Manage.ScoreTable> = emptyList(), val busy: Boolean = false,
                       val problem: FieldProblem? = null)

data class DevicesState(
    val query: String = "", val duplicatesOnly: Boolean = false, val page: Manage.DevicePage? = null,
    val alerts: List<Manage.Alert> = emptyList(), val history: List<Manage.Device>? = null, val historySerial: String = "",
    val busy: Boolean = false, val problem: FieldProblem? = null
)

data class NotesState(val notes: List<Manage.Note> = emptyList(), val busy: Boolean = false, val problem: FieldProblem? = null)

/** Scores, the device registry and notifications: live tables, read from the server each time they are shown. */
class ManageViewModel(private val container: AppContainer) : ViewModel() {

    private val _scores = MutableStateFlow(ScoresState())
    val scores: StateFlow<ScoresState> = _scores.asStateFlow()

    private val _devices = MutableStateFlow(DevicesState())
    val devices: StateFlow<DevicesState> = _devices.asStateFlow()

    private val _notes = MutableStateFlow(NotesState())
    val notes: StateFlow<NotesState> = _notes.asStateFlow()

    private var searching: Job? = null

    fun loadScores(days: Int = _scores.value.days) = viewModelScope.launch {
        _scores.update { it.copy(days = days, busy = true) }
        val (from, to) = range(days)
        val result = call { api, token -> api.scores(token, from, to) }
        _scores.update { if (result is ApiResult.Ok) it.copy(tables = result.value, busy = false, problem = null) else it.copy(busy = false, problem = problemOf(result)) }
    }

    /** The scores as an Excel file, shared like every other export. */
    fun shareScores(context: Context) = share(context, "scores") { range(_scores.value.days).let { (f, t) -> "manage/scores&from=$f&to=$t&format=xlsx" } }

    fun loadDevices() {
        searching?.cancel()
        searching = viewModelScope.launch {
            _devices.update { it.copy(busy = true) }
            val state = _devices.value
            val result = call { api, token -> api.devices(token, state.query, state.duplicatesOnly, 0) }
            val alerts = call { api, token -> api.deviceAlerts(token, markSeen = true) }
            _devices.update {
                if (result is ApiResult.Ok) it.copy(page = result.value, busy = false, problem = null,
                    alerts = (alerts as? ApiResult.Ok)?.value ?: it.alerts)
                else it.copy(busy = false, problem = problemOf(result))
            }
        }
    }

    fun searchDevices(query: String) {
        _devices.update { it.copy(query = query) }
        searching?.cancel()
        searching = viewModelScope.launch { delay(400); loadDevices() }
    }

    fun duplicatesOnly(on: Boolean) {
        _devices.update { it.copy(duplicatesOnly = on) }
        loadDevices()
    }

    fun openHistory(serial: String) = viewModelScope.launch {
        _devices.update { it.copy(historySerial = serial, history = null, busy = true) }
        val result = call { api, token -> api.deviceHistory(token, serial) }
        _devices.update { it.copy(busy = false, history = (result as? ApiResult.Ok)?.value.orEmpty()) }
    }

    fun closeHistory() = _devices.update { it.copy(history = null) }

    fun shareDevices(context: Context) = share(context, "devices") {
        val state = _devices.value
        "manage/devices&format=xlsx" + (if (state.duplicatesOnly) "&dup=1" else "") +
            (if (state.query.isBlank()) "" else "&q=" + java.net.URLEncoder.encode(state.query.trim(), "UTF-8"))
    }

    /** The account's notifications; opening the list marks the unread ones read. */
    fun loadNotes() = viewModelScope.launch {
        _notes.update { it.copy(busy = true) }
        val result = call { api, token -> api.notifications(token) }
        if (result !is ApiResult.Ok) return@launch _notes.update { it.copy(busy = false, problem = problemOf(result)) }
        _notes.update { it.copy(notes = result.value, busy = false, problem = null) }
        val unread = result.value.filterNot { it.read }.map { it.id }
        if (unread.isNotEmpty()) call { api, token -> api.markNotificationsRead(token, unread) }
    }

    private fun share(context: Context, name: String, route: () -> String) = viewModelScope.launch {
        val server = container.settingsRepository.current().syncTarget
        val token = container.vault.serverToken() ?: return@launch
        val target = File(container.cacheDir, "manage/$name-${System.currentTimeMillis()}.xlsx").apply { parentFile?.mkdirs() }
        if (ServerFiles(server, token).downloadRoute(route(), target)) ShareUtil.share(context, target)
    }

    private fun range(days: Int): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return (now - days * DAY) to now
    }

    private suspend fun <T> call(block: suspend (ServerApi, String) -> ApiResult<T>): ApiResult<T> {
        val api = ServerApi(container.settingsRepository.current().syncTarget)
        val token = container.vault.serverToken()
        if (!api.configured || token == null) return ApiResult.Unreachable
        return block(api, token)
    }

    private fun problemOf(result: ApiResult<*>): FieldProblem =
        if (result is ApiResult.Refused) FieldProblem.Refused(result.message) else FieldProblem.Offline

    private companion object {
        const val DAY = 86_400_000L
    }
}
