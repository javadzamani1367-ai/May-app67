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
    private val settings: SettingsRepository,
    private val cases: ServerCaseSync
) {

    /**
     * The case goes up before the submission does.
     *
     * The server records a submission against a case it holds, so submitting
     * one it has never seen is refused outright. Pushing first is not an
     * optimisation here: without it the manager's queue stays empty however
     * many cases the experts send.
     */
    suspend fun submit(reportId: String): Boolean {
        if (!cases.pushOne(reportId)) return false
        return send { api, token -> api.submitApproval(token, reportId) }
    }

    /**
     * A decision, with the decided case pushed alongside it. The decision lives
     * in the `approvals` table on the server and in the case's own
     * `approval_state`; pushing keeps the expert's next pull in agreement with
     * the notification they just received.
     */
    suspend fun decide(reportId: String, state: ApprovalState, comment: String): Boolean {
        val reported = send { api, token -> api.decideApproval(token, reportId, state, comment) }
        cases.pushOne(reportId)
        return reported
    }

    private suspend fun send(
        block: suspend (ServerApi, String) -> ApiResult<Boolean>
    ): Boolean {
        val api = ServerApi(settings.current().syncTarget)
        val token = vault.serverToken()
        if (!api.configured || token == null) return false
        return block(api, token) is ApiResult.Ok
    }
}
