package ir.ilam.inspection.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.util.BiometricGate
import ir.ilam.inspection.util.PersianNumbers

/**
 * Entry gate. Owner names, national ids and the names of security and police
 * personnel are in this database, so there is no way past this screen.
 *
 * The installation shows the code it generated for itself. The manager
 * registers that code against a user, and from then on this phone is the only
 * one that user can sign in from. The first sign-in has to reach the server,
 * because that is where the pairing lives; afterwards the phone unlocks on its
 * own, since field work happens where there is no signal.
 */
@Composable
fun LockScreen(vault: KeyStoreVault, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val appContainer = context.container
    val viewModel: LockViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(appContainer) { LockViewModel(it) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    var userCode by remember { mutableStateOf(vault.userCode().orEmpty()) }
    var password by remember { mutableStateOf("") }

    val promptTitle = stringResource(R.string.lock_biometric_title)
    val cancelLabel = stringResource(R.string.action_cancel)

    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }

    // Fingerprint stands in for the password, never for the activation: it can
    // only be offered once this phone has already been paired.
    LaunchedEffect(Unit) {
        if (vault.isActivated() && BiometricGate.isAvailable(context)) {
            context.findActivity()?.let { activity ->
                BiometricGate.prompt(activity, promptTitle, cancelLabel, onUnlocked)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineMedium
        )

        DeviceCodeCard(code = viewModel.deviceCode, showHint = !vault.isActivated())

        if (state.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
        state.serverMessage?.let { Message(it, error = true) }
        state.errorRes?.let { Message(stringResource(it), error = true) }
        state.noticeRes?.let { Message(stringResource(it), error = false) }

        AppTextField(
            label = stringResource(R.string.lock_user_code),
            value = userCode,
            onValueChange = { userCode = it; viewModel.dismissMessages() },
            imeAction = ImeAction.Next
        )
        AppTextField(
            label = stringResource(R.string.lock_enter_password),
            value = password,
            onValueChange = { password = it; viewModel.dismissMessages() },
            imeAction = ImeAction.Done
        )
        Button(
            onClick = { viewModel.signIn(userCode, password) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.action_confirm))
        }

        if (!vault.isActivated()) {
            TextButton(onClick = viewModel::openRegistration, enabled = !state.busy) {
                Text(stringResource(R.string.lock_request_registration))
            }
        }

        // Always available: settings are behind this screen, so without this
        // a wrong address has no way of being corrected.
        TextButton(onClick = viewModel::openServerAddress, enabled = !state.busy) {
            Text(stringResource(R.string.lock_server_address))
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

@Composable
private fun Message(text: String, error: Boolean) {
    Text(
        text = text,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    )
}

/** The installation's code, with the instruction that goes with it. */
@Composable
private fun DeviceCodeCard(code: String, showHint: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.lock_device_code),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = PersianNumbers.toPersian(code),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        if (showHint) {
            Text(
                text = stringResource(R.string.lock_device_code_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** `LocalContext.current` is usually a theme wrapper rather than the activity. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
