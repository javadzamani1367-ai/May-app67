package ir.ilam.inspection.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.BrandMark
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.BiometricGate

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
    val canUseFingerprint = vault.isActivated() && BiometricGate.isAvailable(context)

    LaunchedEffect(state.unlocked) {
        if (state.unlocked) onUnlocked()
    }

    // Fingerprint stands in for the password, never for the activation: it can
    // only be offered once this phone has already been paired.
    LaunchedEffect(Unit) {
        if (canUseFingerprint) {
            context.findActivity()?.let { BiometricGate.prompt(it, promptTitle, cancelLabel, onUnlocked) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Tavan.colors.header, Tavan.colors.headerDeep)))
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandHeader()

        AppCard(modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.lock_welcome), style = MaterialTheme.typography.titleLarge)
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
                    imeAction = ImeAction.Next,
                    ltr = true,
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) }
                )
                AppTextField(
                    label = stringResource(R.string.lock_enter_password),
                    value = password,
                    onValueChange = { password = it; viewModel.dismissMessages() },
                    imeAction = ImeAction.Done,
                    password = true,
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) }
                )
                PrimaryButton(
                    text = stringResource(R.string.lock_sign_in),
                    onClick = { viewModel.signIn(userCode, password) },
                    busy = state.busy,
                    icon = Icons.AutoMirrored.Filled.Login,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                if (canUseFingerprint) {
                    SecondaryButton(
                        text = stringResource(R.string.lock_biometric),
                        onClick = {
                            context.findActivity()?.let { BiometricGate.prompt(it, promptTitle, cancelLabel, onUnlocked) }
                        },
                        icon = Icons.Filled.Fingerprint,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                }
            }
        }

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
        // Always available: settings are behind this screen, so without this
        // a wrong address has no way of being corrected.
        TextButton(onClick = viewModel::openServerAddress, enabled = !state.busy) {
            Icon(Icons.Filled.Dns, contentDescription = null, tint = Tavan.colors.onHeaderMuted)
            Text(
                stringResource(R.string.lock_server_address),
                color = Tavan.colors.onHeaderMuted,
                modifier = Modifier.padding(start = 8.dp)
            )
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
        color = if (error) Tavan.colors.danger.strong else Tavan.colors.success.strong,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    )
}

/** The mark, the name in both scripts, and which of the two apps this is. */
@Composable
private fun BrandHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 16.dp)) {
        BrandMark(size = 92.dp)
        Text(
            stringResource(R.string.brand_name),
            style = MaterialTheme.typography.displaySmall,
            color = Tavan.colors.onHeader,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(stringResource(R.string.brand_latin), style = MaterialTheme.typography.labelLarge, color = Tavan.colors.onHeaderMuted)
        Text(
            stringResource(R.string.brand_tagline),
            style = MaterialTheme.typography.bodySmall,
            color = Tavan.colors.onHeaderMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        StatusBadge(
            text = stringResource(if (UserRole.isManager) R.string.lock_role_manager else R.string.lock_role_expert),
            tone = Tone.ACCENT,
            solid = true,
            modifier = Modifier.padding(top = 10.dp)
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

/** `LocalContext.current` is usually a theme wrapper rather than the activity. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
