package ir.ilam.inspection.data

import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.ServerApi

/** What the entry screen should do about an attempt to get in. */
sealed class SignInOutcome {
    /** In. [login] is the server's answer when this attempt reached it. */
    data class Granted(val login: ServerApi.LoginResult? = null) : SignInOutcome()

    /** The server said no, and said why; [message] is its own text. */
    data class Refused(val message: String) : SignInOutcome()

    /** Wrong password, decided on this phone against the stored hash. */
    data object WrongPassword : SignInOutcome()

    /**
     * No server was reachable and this installation was never activated, so
     * there is nothing to check the attempt against.
     */
    data object NeedsActivation : SignInOutcome()

    /** First run on a phone with no server: the chosen password is too weak. */
    data object PasswordTooShort : SignInOutcome()
}

/**
 * Signing in, the same way in every TavanKav app.
 *
 * Field work happens where there is no signal, so the rule is: prove it once,
 * then trust the phone.
 *
 * - First entry must reach the server, which checks the password (and, for an
 *   expert, that this installation is the one registered to them).
 * - After that the phone keeps a salted hash of the password and unlocks on
 *   its own, with no network at all.
 * - When the server is reachable it is asked again, and a refusal — account
 *   disabled, moved, password changed — takes the activation away, so the next
 *   entry has to be online again.
 *
 * [allowLocalOnly] lets an inspection app with no server address configured
 * work standalone, with the first password typed becoming the one. The field
 * app turns it off: every field account is made by the manager on the server.
 */
class SignInFlow(
    private val vault: KeyStoreVault,
    private val serverAddress: suspend () -> String,
    private val allowLocalOnly: Boolean
) {

    suspend fun signIn(userCode: String, password: String): SignInOutcome {
        val api = ServerApi(serverAddress())
        if (!api.configured) {
            return if (allowLocalOnly) localOnly(userCode, password) else offline(userCode, password)
        }

        when (val result = api.login(userCode, password, vault.deviceCode())) {
            is ApiResult.Ok -> {
                vault.setUserCode(userCode)
                vault.setPin(password)
                vault.markActivated(result.value.token)
                return SignInOutcome.Granted(result.value)
            }
            is ApiResult.Refused -> {
                // A lock-out is about this attempt, not the account: the phone
                // stays paired and simply has to wait. Anything else means the
                // account is no longer valid here, and dropping the activation
                // stops it unlocking offline tomorrow.
                if (result.code != LOCKED) vault.clearActivation()
                return SignInOutcome.Refused(result.message)
            }
            ApiResult.Unreachable -> return offline(userCode, password)
        }
    }

    /** The server could not be reached: a phone paired before is trusted on its own. */
    private fun offline(userCode: String, password: String): SignInOutcome {
        if (!vault.isActivated() && !vault.hasPin()) return SignInOutcome.NeedsActivation
        if (!allowLocalOnly && !vault.isActivated()) return SignInOutcome.NeedsActivation
        if (!vault.verifyPin(password)) return SignInOutcome.WrongPassword
        vault.setUserCode(userCode)
        return SignInOutcome.Granted()
    }

    /** First run without a server: the password typed here becomes the one. */
    private fun localOnly(userCode: String, password: String): SignInOutcome {
        if (!vault.hasPin()) {
            if (password.length < MIN_PASSWORD) return SignInOutcome.PasswordTooShort
            vault.setUserCode(userCode)
            vault.setPin(password)
            return SignInOutcome.Granted()
        }
        if (!vault.verifyPin(password)) return SignInOutcome.WrongPassword
        vault.setUserCode(userCode)
        return SignInOutcome.Granted()
    }

    private companion object {
        const val MIN_PASSWORD = 6
        const val LOCKED = "locked"
    }
}
