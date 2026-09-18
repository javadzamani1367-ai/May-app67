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
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.util.BiometricGate

private const val PIN_LENGTH = 6

/**
 * Entry gate. A PIN is mandatory — owner names, national ids and the names of
 * security personnel are in this database. Fingerprint is offered when the
 * device has it enrolled.
 */
@Composable
fun LockScreen(vault: KeyStoreVault, onUnlocked: () -> Unit) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val settingUp = remember { !vault.hasPin() }

    val wrongPin = stringResource(R.string.lock_wrong_pin)
    val mismatch = stringResource(R.string.lock_mismatch)
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
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.lock_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(if (settingUp) R.string.lock_set_pin else R.string.lock_enter_pin),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        NumberField(
            label = stringResource(R.string.lock_enter_pin),
            value = pin,
            onValueChange = { if (it.length <= PIN_LENGTH) pin = it },
            error = error,
            imeAction = if (settingUp) ImeAction.Next else ImeAction.Done
        )
        if (settingUp) {
            NumberField(
                label = stringResource(R.string.lock_confirm_pin),
                value = confirm,
                onValueChange = { if (it.length <= PIN_LENGTH) confirm = it },
                imeAction = ImeAction.Done
            )
        }
        Button(
            onClick = {
                error = null
                when {
                    pin.length != PIN_LENGTH -> error = wrongPin
                    settingUp && pin != confirm -> error = mismatch
                    settingUp -> {
                        vault.setPin(pin)
                        onUnlocked()
                    }
                    vault.verifyPin(pin) -> onUnlocked()
                    else -> error = wrongPin
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

/** `LocalContext.current` is usually a theme wrapper rather than the activity. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
