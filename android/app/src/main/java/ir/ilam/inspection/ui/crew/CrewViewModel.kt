package ir.ilam.inspection.ui.crew

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.export.ShareUtil
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.CrewWork
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.ServerFiles
import ir.ilam.inspection.sync.crewExportRoute
import ir.ilam.inspection.sync.crewReport
import ir.ilam.inspection.sync.saveCrewOvertime
import ir.ilam.inspection.util.PersianDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** The periods the screen offers: this Jalali month, or the last 7, 30 or 90 days. */
enum class CrewPeriod(val days: Int) { MONTH(0), WEEK(7), MONTH30(30), QUARTER(90) }

data class CrewState(
    val period: CrewPeriod = CrewPeriod.MONTH,
    val report: CrewWork.Report? = null,
    val busy: Boolean = false,
    /** Null, or why there is nothing fresh: offline, or the server's own words. */
    val problem: String? = null,
    val offline: Boolean = false,
    /** The person opened on the screen, by key. */
    val selected: String? = null,
    val saved: Boolean = false
) {
    val member: CrewWork.Member? get() = report?.members?.firstOrNull { it.key == selected }
}

/**
 * Crew performance, live from the server — the figures are built there from
 * every phone's work, so nothing is kept on this one but the files shared.
 */
class CrewViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(CrewState())
    val state: StateFlow<CrewState> = _state.asStateFlow()

    init {
        load()
    }

    fun setPeriod(period: CrewPeriod) {
        _state.update { it.copy(period = period) }
        load()
    }

    fun select(key: String?) = _state.update { it.copy(selected = key, saved = false) }

    fun load() {
        viewModelScope.launch {
            val (api, token) = client() ?: run { _state.update { it.copy(offline = true) }; return@launch }
            val (from, to) = range()
            _state.update { it.copy(busy = true) }
            when (val result = api.crewReport(token, from, to)) {
                is ApiResult.Ok -> _state.update { it.copy(report = result.value, busy = false, problem = null, offline = false) }
                is ApiResult.Refused -> _state.update { it.copy(busy = false, problem = result.message) }
                ApiResult.Unreachable -> _state.update { it.copy(busy = false, offline = true) }
            }
        }
    }

    fun saveOvertime(memberKey: String, hours: String, note: String) {
        viewModelScope.launch {
            val (api, token) = client() ?: return@launch
            val (from, to) = range()
            _state.update { it.copy(busy = true, saved = false) }
            when (val result = api.saveCrewOvertime(token, memberKey, hours, note, from, to)) {
                is ApiResult.Ok -> {
                    _state.update { it.copy(saved = true) }
                    load()
                }
                is ApiResult.Refused -> _state.update { it.copy(busy = false, problem = result.message) }
                ApiResult.Unreachable -> _state.update { it.copy(busy = false, offline = true) }
            }
        }
    }

    /** The server's Excel or Word, downloaded and handed to the share sheet. */
    fun share(context: Context, format: String, memberKey: String?) {
        viewModelScope.launch {
            val server = container.settingsRepository.current().syncTarget
            val token = container.vault.serverToken() ?: return@launch
            val (from, to) = range()
            _state.update { it.copy(busy = true) }
            val target = File(context.cacheDir, "crew/crew-${PersianDate.trackingStamp(from)}-${PersianDate.trackingStamp(to)}.$format")
            val ok = server.isNotBlank() && ServerFiles(server, token).downloadRoute(crewExportRoute(from, to, format, memberKey), target)
            _state.update { it.copy(busy = false, offline = !ok && it.offline) }
            if (ok) ShareUtil.share(context, target) else _state.update { it.copy(offline = true) }
        }
    }

    /** Start of the first day to the end of today, in the phone's (Iran) time. */
    private fun range(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val to = PersianDate.endOfDay(now)
        val from = when (val period = _state.value.period) {
            CrewPeriod.MONTH -> PersianDate.today().let { PersianDate.toEpochMillis(it.year, it.month, 1) }
            else -> PersianDate.startOfDay(now - (period.days - 1) * DAY_MILLIS)
        }
        return from to to
    }

    private suspend fun client(): Pair<ServerApi, String>? {
        val api = ServerApi(container.settingsRepository.current().syncTarget)
        val token = container.vault.serverToken() ?: return null
        return if (api.configured) api to token else null
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
