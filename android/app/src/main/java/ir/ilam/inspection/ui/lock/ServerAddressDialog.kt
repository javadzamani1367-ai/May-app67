package ir.ilam.inspection.ui.lock

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
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
import androidx.compose.ui.text.input.KeyboardType
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.common.AppTextField

/**
 * The server address, reachable from the entry screen.
 *
 * Settings sit behind the sign-in, so an address that is wrong — or right, but
 * pointing at a server that does not know the account typed above — left the
 * phone with no way back to the one field that fixes it. That happens on every
 * first setup: an account invented while there was no server stops working the
 * moment one is configured.
 *
 * Leaving it empty is a supported state, not an error: the app then works
 * entirely offline and hands cases to the Windows archive over Wi-Fi, cable or
 * a `.cvz` package.
 */
@Composable
fun ServerAddressDialog(
    current: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var address by remember { mutableStateOf(current) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Dns, contentDescription = null) },
        title = { Text(stringResource(R.string.lock_server_address)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.lock_server_address_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AppTextField(
                    label = stringResource(R.string.settings_sync_target),
                    value = address,
                    onValueChange = { address = it },
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Uri,
                    ltr = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(address) }, enabled = !busy) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
