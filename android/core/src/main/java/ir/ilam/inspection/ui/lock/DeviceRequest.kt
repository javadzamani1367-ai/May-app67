package ir.ilam.inspection.ui.lock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.core.R
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.util.NationalId

/**
 * This install's code on the entry screen of every TavanKav app, with the
 * request button under it. Each install of each app has its own code; the
 * manager writes it against the person, and a reinstall means a new one.
 */
@Composable
fun DeviceCodeCard(code: String, busy: Boolean, onRequest: () -> Unit) {
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
        Text(
            text = stringResource(R.string.lock_device_code_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Tavan.colors.onHeaderMuted,
            textAlign = TextAlign.Center
        )
        TextButton(onClick = onRequest, enabled = !busy) {
            Icon(Icons.Filled.AppRegistration, contentDescription = null, tint = Tavan.colors.onHeader)
            Text(stringResource(R.string.lock_request_registration), color = Tavan.colors.onHeader, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * «درخواست کاربری»: name, national ID, phone and a note for the manager. The
 * install's code is not typed — it goes with the request, so it cannot be read
 * out wrong over a phone line.
 */
@Composable
fun RegistrationDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSend: (fullName: String, nationalId: String, phone: String, note: String) -> Unit
) {
    var fullName by remember { mutableStateOf("") }
    var nationalId by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val badId = nationalId.isNotBlank() && !NationalId.isValid(nationalId)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.AppRegistration, contentDescription = null) },
        title = { Text(stringResource(R.string.lock_registration_title)) },
        text = {
            Column {
                Text(stringResource(R.string.lock_registration_hint), style = MaterialTheme.typography.bodySmall)
                AppTextField(label = stringResource(R.string.reg_full_name), value = fullName, onValueChange = { fullName = it })
                NumberField(
                    label = stringResource(R.string.reg_national_id), value = nationalId, onValueChange = { nationalId = it },
                    error = if (badId) stringResource(R.string.reg_bad_national_id) else null
                )
                NumberField(label = stringResource(R.string.reg_phone), value = phone, onValueChange = { phone = it })
                AppTextField(
                    label = stringResource(R.string.reg_note), value = note, onValueChange = { note = it },
                    singleLine = false, imeAction = ImeAction.Done
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(fullName.trim(), NationalId.normalized(nationalId), phone.trim(), note.trim()) },
                enabled = !busy && fullName.isNotBlank() && NationalId.isValid(nationalId)
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
