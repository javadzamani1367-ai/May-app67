package ir.ilam.inspection.field.ui.lock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.data.SignInOutcome
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.sync.FieldSyncWorker
import ir.ilam.inspection.ui.lock.LockFrame
import ir.ilam.inspection.ui.lock.LockMessage
import ir.ilam.inspection.ui.lock.ServerAddressDialog
import ir.ilam.inspection.util.BiometricGate
import ir.ilam.inspection.util.findActivity
import kotlinx.coroutines.launch

/**
 * The shared entry gate with this app's rules: the first entry reaches the
 * server, which made the account; after that the phone unlocks offline, by
 * password or fingerprint. An online entry also sends whatever was waiting.
 */
@Composable
fun FieldLockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val container = context.field
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<LockMessage?>(null) }
    var editingServer by remember { mutableStateOf(false) }

    val needsServer = stringResource(R.string.field_lock_needs_server)
    val wrongPassword = stringResource(R.string.field_lock_wrong_password)
    val missingCode = stringResource(R.string.field_lock_missing_code)
    val saved = stringResource(R.string.field_server_saved)
    val promptTitle = stringResource(R.string.field_lock_biometric_title)
    val cancelLabel = stringResource(R.string.action_cancel)

    // Fingerprint replaces the password only on a phone already proven online.
    val canUseFingerprint = container.account.activated && BiometricGate.isAvailable(context)
    val fingerprint = { context.findActivity()?.let { BiometricGate.prompt(it, promptTitle, cancelLabel, onUnlocked) } }
    LaunchedEffect(Unit) {
        if (canUseFingerprint && !container.prefs.sessionExpired) fingerprint()
    }

    LockFrame(
        roleLabel = stringResource(R.string.field_role),
        initialUserCode = container.account.userCode,
        busy = busy,
        message = message,
        canUseFingerprint = canUseFingerprint && !container.prefs.sessionExpired,
        onFingerprint = { fingerprint() },
        onSignIn = { code, password ->
            if (code.isBlank()) {
                message = LockMessage(missingCode, error = true)
            } else {
                scope.launch {
                    busy = true
                    val outcome = container.account.signIn(code.trim(), password)
                    busy = false
                    message = when (outcome) {
                        is SignInOutcome.Granted -> {
                            if (outcome.login != null) FieldSyncWorker.runSoon(context)
                            onUnlocked()
                            null
                        }
                        is SignInOutcome.Refused -> LockMessage(outcome.message, error = true)
                        SignInOutcome.WrongPassword -> LockMessage(wrongPassword, error = true)
                        SignInOutcome.NeedsActivation, SignInOutcome.PasswordTooShort -> LockMessage(needsServer, error = true)
                    }
                }
            }
        },
        onEdited = { message = null },
        onServerAddress = { editingServer = true }
    )

    if (editingServer) {
        ServerAddressDialog(
            current = container.prefs.serverAddress,
            busy = busy,
            onDismiss = { editingServer = false },
            onSave = {
                container.prefs.serverAddress = it
                editingServer = false
                message = LockMessage(saved, error = false)
            }
        )
    }
}
