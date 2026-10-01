package ir.ilam.inspection.data.repo

import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.data.SignInFlow
import ir.ilam.inspection.data.SignInOutcome
import ir.ilam.inspection.sync.ApiResult
import ir.ilam.inspection.sync.ServerApi

/**
 * Signing in, for the inspection apps.
 *
 * The rule itself — prove it once online, then trust the phone — is shared by
 * every TavanKav app and lives in [SignInFlow]. What is particular here is the
 * device pairing: the binding the manager set up is between a user code and
 * one installation's own code, and the server enforces it at that first
 * online entry. A phone with no server address still works standalone.
 */
class AccountRepository(
    private val vault: KeyStoreVault,
    private val settings: SettingsRepository
) {

    private val flow = SignInFlow(
        vault = vault,
        serverAddress = { settings.current().syncTarget },
        allowLocalOnly = true
    )

    suspend fun signIn(userCode: String, password: String): SignInOutcome = flow.signIn(userCode, password)

    /** Puts this installation in the manager's queue to be registered. */
    suspend fun requestRegistration(fullName: String, phone: String, county: String): ApiResult<Boolean> =
        ServerApi(settings.current().syncTarget)
            .requestDevice(vault.deviceCode(), fullName, phone, county)

    val deviceCode: String get() = vault.deviceCodeForDisplay()

    val activated: Boolean get() = vault.isActivated()
}
