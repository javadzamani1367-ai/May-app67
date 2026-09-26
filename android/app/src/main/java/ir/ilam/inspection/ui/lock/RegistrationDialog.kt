package ir.ilam.inspection.ui.lock

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppRegistration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.NumberField

/**
 * Asks the manager to register this installation. The device code is not typed
 * in here — it is taken from the installation itself and sent with the
 * request, so it cannot be mistyped or read out wrong over a phone line.
 */
@Composable
fun RegistrationDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (fullName: String, phone: String, county: String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var county by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.AppRegistration, contentDescription = null) },
        title = { Text(stringResource(R.string.lock_registration_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.lock_registration_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                AppTextField(
                    label = stringResource(R.string.users_full_name),
                    value = fullName,
                    onValueChange = { fullName = it }
                )
                NumberField(
                    label = stringResource(R.string.users_phone),
                    value = phone,
                    onValueChange = { phone = it }
                )
                AppTextField(
                    label = stringResource(R.string.users_county),
                    value = county,
                    onValueChange = { county = it },
                    imeAction = ImeAction.Done
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(fullName, phone, county) },
                enabled = !busy && fullName.isNotBlank()
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
