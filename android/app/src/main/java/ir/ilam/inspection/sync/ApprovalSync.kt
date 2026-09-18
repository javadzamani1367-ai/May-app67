package ir.ilam.inspection.sync

import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.data.model.ApprovalState
import ir.ilam.inspection.data.repo.SettingsRepository

/**
 * Tells the server about a decision made on a phone.
 *
 * Deliberately best effort. The decision is already written to this phone's
 * own database before this is called, so a village with no signal does not
 * stop a manager approving a case — the row travels with the next sync like
 * everything else. What this adds is speed: when there is a connection, the
 * expert is told within seconds rather than at the end of the day.
 */
class ApprovalSync(
    private val vault: KeyStoreVault,
    private val settings: SettingsRepository
) {

    suspend fun submit(reportId: String): Boolean =
        send { api, token -> api.submitApproval(token, reportId) }

    suspend fun decide(reportId: String, state: ApprovalState, comment: String): Boolean =
        send { api, token -> api.decideApproval(token, reportId, state, comment) }

    private suspend fun send(
        block: suspend (ServerApi, String) -> ApiResult<Boolean>
    ): Boolean {
        val api = ServerApi(settings.current().syncTarget)
        val token = vault.serverToken()
        if (!api.configured || token == null) return false
        return block(api, token) is ApiResult.Ok
    }
}
