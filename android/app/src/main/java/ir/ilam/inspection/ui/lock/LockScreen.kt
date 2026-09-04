package ir.ilam.inspection.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import ir.ilam.inspection.R
import ir.ilam.inspection.data.KeyStoreVault
import ir.ilam.inspection.ui.common.NumberField

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

    LaunchedEffect(settingUp) {
        if (!settingUp) {
            tryBiometric(context.findFragmentActivity(), onUnlocked)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
            error = error
        )
        if (settingUp) {
            NumberField(
                label = stringResource(R.string.lock_confirm_pin),
                value = confirm,
                onValueChange = { if (it.length <= PIN_LENGTH) confirm = it }
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
        if (!settingUp && canUseBiometric(context)) {
            TextButton(onClick = { tryBiometric(context.findFragmentActivity(), onUnlocked) }) {
                Text(stringResource(R.string.lock_biometric))
            }
        }
    }
}

/**
 * `LocalContext.current` is often a theme wrapper rather than the activity, so
 * the host is found by walking the wrapper chain.
 */
private fun android.content.Context.findFragmentActivity(): FragmentActivity? {
    var current: android.content.Context? = this
    while (current is android.content.ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}

private fun canUseBiometric(context: android.content.Context): Boolean =
    BiometricManager.from(context)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS

private fun tryBiometric(activity: FragmentActivity?, onUnlocked: () -> Unit) {
    if (activity == null || !canUseBiometric(activity)) return
    val prompt = BiometricPrompt(
        activity,
        androidx.core.content.ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }
        }
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.lock_biometric_title))
            .setNegativeButtonText(activity.getString(R.string.action_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
    )
}
