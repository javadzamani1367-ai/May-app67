package ir.ilam.inspection.data.repo

import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.Flow
import ir.ilam.inspection.sync.ServerApi
import ir.ilam.inspection.sync.flowRaw
import ir.ilam.inspection.sync.parseAssigned
import ir.ilam.inspection.sync.parseStatuses
import org.json.JSONObject

/**
 * What the expert's phone keeps of the office's side of their cases: which
 * cases the manager or the area executor referred to them (who, until when,
 * with what note), and where each case they sent for approval stands — seen
 * yet, by whom, decided. Refreshed with every sync, kept for the hours with
 * no signal. The units' actions are deliberately not here: they are not the
 * expert's to follow.
 */
class ExpertFlowRepository(private val settings: SettingsRepository) {

    suspend fun refresh(api: ServerApi, token: String): Boolean {
        val assigned = api.flowRaw(token, "cases/assigned")
        val mine = api.flowRaw(token, "cases/mine")
        if (assigned is ApiResult.Ok) settings.put(KEY_ASSIGNED, assigned.value)
        if (mine is ApiResult.Ok) settings.put(KEY_STATUSES, mine.value)
        return assigned is ApiResult.Ok
    }

    suspend fun assignments(): List<Flow.Assignment> = read(KEY_ASSIGNED)?.let(::parseAssigned).orEmpty()

    suspend fun assignment(reportId: String): Flow.Assignment? = assignments().firstOrNull { it.reportId == reportId }

    suspend fun status(reportId: String): Flow.Status? = read(KEY_STATUSES)?.let(::parseStatuses)?.firstOrNull { it.reportId == reportId }

    private suspend fun read(key: String): JSONObject? = settings.raw(key)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private companion object {
        const val KEY_ASSIGNED = "flow_assigned"
        const val KEY_STATUSES = "flow_statuses"
    }
}
