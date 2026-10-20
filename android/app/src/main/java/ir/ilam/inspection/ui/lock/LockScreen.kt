package ir.ilam.inspection.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.util.AppRestart
import ir.ilam.inspection.util.BiometricGate
import ir.ilam.inspection.util.findActivity

/**
 * Entry gate. Owner names, national ids and the names of security and police
 * personnel are in this database, so there is no way past this screen.
 *
 * The installation shows the code it generated for itself. The manager
 * registers that code against a user, and from then on this phone is the only
 * one that user can sign in from. The first sign-in has to reach the server,
 * because that is where the pairing lives; afterwards the phone unlocks on its
 * own, since field work happens where there is no signal.
 *
 * The form itself is the one every TavanKav app shares ([LockFrame]); the
 * device code and the registration request are this app's own.
 */
@Composable
fun LockScreen(vault: KeyStoreVault, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val appContainer = context.container
    val viewModel: LockViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(appContainer) { LockViewModel(it) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val promptTitle = stringResource(R.string.lock_biometric_title)
    val cancelLabel = stringResource(R.string.action_cancel)
    // Fingerprint stands in for the password, never for the activation: it can
    // only be offered once this phone has already been paired.
    val canUseFingerprint = vault.isActivated() && BiometricGate.isAvailable(context)
    val fingerprint = { context.findActivity()?.let { BiometricGate.prompt(it, promptTitle, cancelLabel, onUnlocked) } }

    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }
    LaunchedEffect(state.restart) {
        if (state.restart) AppRestart.restart(context)
    }
    LaunchedEffect(Unit) {
        if (canUseFingerprint) fingerprint()
    }

    val message = state.serverMessage?.let { LockMessage(it, error = true) }
        ?: state.errorRes?.let { LockMessage(stringResource(it), error = true) }
        ?: state.noticeRes?.let { LockMessage(stringResource(it), error = false) }

    LockFrame(
        roleLabel = stringResource(
            when (UserRole.current) {
                UserRole.MANAGER -> R.string.lock_role_manager
                UserRole.EXECUTOR -> R.string.lock_role_executor
                UserRole.ILLEGAL -> R.string.lock_role_illegal
                UserRole.EXPERT -> R.string.lock_role_expert
            }
        ),
        initialUserCode = vault.userCode().orEmpty(),
        busy = state.busy,
        message = message,
        canUseFingerprint = canUseFingerprint,
        onFingerprint = { fingerprint() },
        onSignIn = viewModel::signIn,
        onEdited = viewModel::dismissMessages,
        onServerAddress = viewModel::openServerAddress
    ) {
        DeviceCodeCard(code = viewModel.deviceCode, busy = state.busy, onRequest = viewModel::openRegistration)
    }

    if (state.editingServer) {
        ServerAddressDialog(
            current = state.serverAddress,
            busy = state.busy,
            onDismiss = viewModel::closeServerAddress,
            onSave = viewModel::saveServerAddress
        )
    }

    if (state.askingRegistration) {
        RegistrationDialog(
            busy = state.busy,
            onDismiss = viewModel::closeRegistration,
            onSend = viewModel::requestRegistration
        )
    }
}
