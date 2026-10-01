package ir.ilam.inspection.ui.lock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.theme.Tavan
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
    LaunchedEffect(Unit) {
        if (canUseFingerprint) fingerprint()
    }

    val message = state.serverMessage?.let { LockMessage(it, error = true) }
        ?: state.errorRes?.let { LockMessage(stringResource(it), error = true) }
        ?: state.noticeRes?.let { LockMessage(stringResource(it), error = false) }

    LockFrame(
        roleLabel = stringResource(if (UserRole.isManager) R.string.lock_role_manager else R.string.lock_role_expert),
        initialUserCode = vault.userCode().orEmpty(),
        busy = state.busy,
        message = message,
        canUseFingerprint = canUseFingerprint,
        onFingerprint = { fingerprint() },
        onSignIn = viewModel::signIn,
        onEdited = viewModel::dismissMessages,
        onServerAddress = viewModel::openServerAddress
    ) {
        DeviceCodeCard(code = viewModel.deviceCode, showHint = !vault.isActivated())
        if (!vault.isActivated()) {
            TextButton(onClick = viewModel::openRegistration, enabled = !state.busy) {
                Icon(Icons.Filled.AppRegistration, contentDescription = null, tint = Tavan.colors.onHeader)
                Text(
                    stringResource(R.string.lock_request_registration),
                    color = Tavan.colors.onHeader,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
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

/** The installation's code, with the instruction that goes with it. */
@Composable
private fun DeviceCodeCard(code: String, showHint: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.lock_device_code), style = MaterialTheme.typography.labelMedium, color = Tavan.colors.onHeaderMuted)
        Text(
            text = code,
            style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr),
            color = Tavan.colors.onHeader,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        if (showHint) {
            Text(
                text = stringResource(R.string.lock_device_code_hint),
                style = MaterialTheme.typography.bodySmall,
                color = Tavan.colors.onHeaderMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
