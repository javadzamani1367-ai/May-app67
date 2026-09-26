package ir.ilam.inspection.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.theme.Tavan

/**
 * The only delete button in the app. Nothing an expert has recorded — a photo,
 * a video, a device, a name, a document — is thrown away on a single tap: a
 * mis-tap in the field would silently destroy evidence that cannot be
 * recaptured once the site is left.
 *
 * [itemName] is what the confirmation names, so the question is about the row
 * the finger landed on rather than about "this item".
 */
@Composable
fun ConfirmDeleteButton(
    itemName: String?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Saveable: the dialog survives the recreation a rotation causes, so an
    // answer is never lost half-way through being given.
    var asking by rememberSaveable { mutableStateOf(false) }

    IconButton(onClick = { asking = true }, modifier = modifier) {
        Icon(
            Icons.Outlined.Delete,
            contentDescription = stringResource(R.string.action_delete),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Tavan.colors.danger.strong) },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = {
                Text(
                    if (itemName.isNullOrBlank()) {
                        stringResource(R.string.confirm_delete_message)
                    } else {
                        stringResource(R.string.confirm_delete_named, itemName)
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        onConfirm()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Tavan.colors.danger.strong)
                ) {
                    Text(stringResource(R.string.answer_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) {
                    Text(stringResource(R.string.answer_no))
                }
            }
        )
    }
}
