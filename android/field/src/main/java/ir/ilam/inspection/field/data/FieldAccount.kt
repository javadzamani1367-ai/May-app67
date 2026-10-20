package ir.ilam.inspection.field.data

import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.data.SignInFlow
import ir.ilam.inspection.data.SignInOutcome
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.ServerApi

/**
 * Signing in to the field app: the rule every TavanKav app shares (prove it
 * online once, then unlock offline), with no standalone mode — every field
 * account is made by the manager on the server, so the first entry must reach
 * it. A successful online entry also refreshes the name and permissions and
 * lifts an expired session.
 */
class FieldAccount(
    private val vault: KeyStoreVault,
    private val prefs: FieldPrefs
) {

    private val flow = SignInFlow(vault, serverAddress = { prefs.serverAddress }, allowLocalOnly = false, app = APP)

    suspend fun signIn(userCode: String, password: String): SignInOutcome {
        val outcome = flow.signIn(userCode, password)
        if (outcome is SignInOutcome.Granted) {
            outcome.login?.let { login ->
                prefs.fullName = login.fullName
                prefs.permissions = login.permissions
                prefs.sessionExpired = false
            }
            // Another account's items are in their own database; this one is pulled afresh.
            if (outcome.switched) prefs.lastPull = 0
        }
        return outcome
    }

    /** «درخواست کاربری»: this install's code to the manager's queue, tied to a person by national ID. */
    suspend fun requestRegistration(fullName: String, nationalId: String, phone: String, note: String): ApiResult<Boolean> =
        ServerApi(prefs.serverAddress).requestDevice(vault.deviceCode(), fullName, phone, "", nationalId, note, APP)

    val deviceCodeForDisplay: String get() = vault.deviceCodeForDisplay()

    val userCode: String get() = vault.userCode().orEmpty()

    val activated: Boolean get() = vault.isActivated()

    fun token(): String? = vault.serverToken()

    val deviceCode: String get() = vault.deviceCode()

    private companion object {
        /** The server admits only field accounts from this app (AuthController::login). */
        const val APP = "field"
    }
}
