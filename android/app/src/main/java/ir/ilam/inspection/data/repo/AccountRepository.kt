package ir.ilam.inspection.data.repo

import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.ServerApi

/** What the entry screen should do about an attempt to get in. */
sealed class SignInOutcome {
    data object Granted : SignInOutcome()

    /** The server said no, and said why. [messageRes] is already resolved text. */
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
 * Signing in.
 *
 * The binding the manager set up is between a user code and one installation's
 * own code, and the server is what enforces it. But an expert works in
 * villages with no signal, so the rule is: prove it once, then trust the phone.
 *
 * - First entry must reach the server. It checks the password and that this
 *   device code is the one registered for that user.
 * - After that the phone keeps a hash of the password and unlocks on its own,
 *   with no network at all.
 * - When the server is reachable it is asked again, and a refusal — the
 *   manager moved the account to another phone, or disabled it — takes the
 *   activation away, so the next entry has to be online again.
 */
class AccountRepository(
    private val vault: KeyStoreVault,
    private val settings: SettingsRepository
) {

    suspend fun signIn(userCode: String, password: String): SignInOutcome {
        val api = ServerApi(settings.current().syncTarget)
        val deviceCode = vault.deviceCode()

        // No server address yet. The screen where that address is typed sits
        // behind this one, so refusing here would lock the manager out of the
        // very setting that fixes it — and a standalone phone with no server
        // at all is still a supported way to work.
        if (!api.configured) {
            return localOnly(userCode, password)
        }

        when (val result = api.login(userCode, password, deviceCode)) {
            is ApiResult.Ok -> {
                vault.setUserCode(userCode)
                vault.setPin(password)
                vault.markActivated(result.value.token)
                return SignInOutcome.Granted
            }
            is ApiResult.Refused -> {
                // The account is no longer valid on this phone. Dropping the
                // activation stops it unlocking offline tomorrow.
                vault.clearActivation()
                return SignInOutcome.Refused(result.message)
            }
            ApiResult.Unreachable -> Unit
        }

        // The server could not be reached. A phone that was paired before is
        // trusted on its own; one that never was has nothing to check against.
        if (!vault.isActivated() && !vault.hasPin()) {
            return SignInOutcome.NeedsActivation
        }
        if (!vault.verifyPin(password)) {
            return SignInOutcome.WrongPassword
        }
        vault.setUserCode(userCode)
        return SignInOutcome.Granted
    }

    /** First run without a server: the password typed here becomes the one. */
    private fun localOnly(userCode: String, password: String): SignInOutcome {
        if (!vault.hasPin()) {
            if (password.length < MIN_PASSWORD) {
                return SignInOutcome.PasswordTooShort
            }
            vault.setUserCode(userCode)
            vault.setPin(password)
            return SignInOutcome.Granted
        }
        if (!vault.verifyPin(password)) {
            return SignInOutcome.WrongPassword
        }
        vault.setUserCode(userCode)
        return SignInOutcome.Granted
    }

    /** Puts this installation in the manager's queue to be registered. */
    suspend fun requestRegistration(fullName: String, phone: String, county: String): ApiResult<Boolean> =
        ServerApi(settings.current().syncTarget)
            .requestDevice(vault.deviceCode(), fullName, phone, county)

    val deviceCode: String get() = vault.deviceCodeForDisplay()

    val activated: Boolean get() = vault.isActivated()

    private companion object {
        const val MIN_PASSWORD = 6
    }
}
