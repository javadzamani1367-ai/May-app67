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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.util.BiometricGate
import ir.ilam.inspection.util.PersianNumbers

private const val MIN_PASSWORD = 6

/**
 * Entry gate. Owner names, national ids and the names of security and police
 * personnel are in this database, so there is no way past this screen.
 *
 * First run shows this installation's own code. The expert reads it to the
 * manager along with their mobile number; the manager records it against a
 * user code in the register, and that user code is what the expert enters
 * here and what every report they file carries. Removing the app destroys the
 * code, so a reinstall or a new phone has to go back to the manager.
 */
@Composable
fun LockScreen(vault: KeyStoreVault, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var userCode by remember { mutableStateOf(vault.userCode().orEmpty()) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val settingUp = remember { !vault.hasPin() }

    val wrongPassword = stringResource(R.string.lock_wrong_password)
    val shortPassword = stringResource(R.string.lock_short_password)
    val mismatch = stringResource(R.string.lock_mismatch)
    val missingCode = stringResource(R.string.lock_missing_user_code)
    val promptTitle = stringResource(R.string.lock_biometric_title)
    val cancelLabel = stringResource(R.string.action_cancel)

    fun askFingerprint() {
        context.findActivity()?.let { activity ->
            BiometricGate.prompt(activity, promptTitle, cancelLabel, onUnlocked)
        }
    }

    LaunchedEffect(settingUp) {
        if (!settingUp) askFingerprint()
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

        DeviceCodeCard(vault = vault, firstRun = settingUp)

        AppTextField(
            label = stringResource(R.string.lock_user_code),
            value = userCode,
            onValueChange = { userCode = it },
            imeAction = ImeAction.Next
        )
        AppTextField(
            label = stringResource(
                if (settingUp) R.string.lock_set_password else R.string.lock_enter_password
            ),
            value = password,
            onValueChange = { password = it },
            error = error,
            imeAction = if (settingUp) ImeAction.Next else ImeAction.Done
        )
        if (settingUp) {
            AppTextField(
                label = stringResource(R.string.lock_confirm_password),
                value = confirm,
                onValueChange = { confirm = it },
                imeAction = ImeAction.Done
            )
        }
        Button(
            onClick = {
                error = null
                when {
                    userCode.isBlank() -> error = missingCode
                    settingUp && password.length < MIN_PASSWORD -> error = shortPassword
                    settingUp && password != confirm -> error = mismatch
                    settingUp -> {
                        vault.setUserCode(userCode)
                        vault.setPin(password)
                        onUnlocked()
                    }
                    vault.verifyPin(password) -> {
                        vault.setUserCode(userCode)
                        onUnlocked()
                    }
                    else -> error = wrongPassword
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.action_confirm))
        }
        if (!settingUp && BiometricGate.isAvailable(context)) {
            TextButton(onClick = { askFingerprint() }) {
                Text(stringResource(R.string.lock_biometric))
            }
        }
    }
}

/** The installation's code, with the instruction that goes with it. */
@Composable
private fun DeviceCodeCard(vault: KeyStoreVault, firstRun: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.lock_device_code),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = PersianNumbers.toPersian(vault.deviceCodeForDisplay()),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        if (firstRun) {
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
