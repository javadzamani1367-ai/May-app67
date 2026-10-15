package ir.ilam.inspection.field.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import ir.ilam.inspection.field.R
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone

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

/**
 * Leaving a form with something entered. Three answers, not two: most of the
 * time the user only wants to come back later, and a plain yes/no would make
 * them choose between losing the work and staying put. Big buttons, one under
 * the other, since it is answered with gloves on.
 */
@Composable
fun ExitDialog(onKeep: () -> Unit, onDelete: () -> Unit, onStay: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        title = { Text(stringResource(R.string.form_exit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.form_exit_message))
                PrimaryButton(stringResource(R.string.form_exit_keep), onKeep, icon = Icons.Filled.Save,
                    tone = Tone.SUCCESS, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.form_exit_delete), onDelete, icon = Icons.Filled.DeleteOutline,
                    tone = Tone.DANGER, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = onStay) { Text(stringResource(R.string.form_exit_stay)) } }
    )
}
