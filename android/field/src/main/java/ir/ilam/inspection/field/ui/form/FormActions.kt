package ir.ilam.inspection.field.ui.form

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R

/** Throwing a draft away: only after a second, explicit yes. */
@Composable
fun DiscardDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.form_discard_title)) },
        text = { Text(stringResource(R.string.form_discard_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.form_discard_yes)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
