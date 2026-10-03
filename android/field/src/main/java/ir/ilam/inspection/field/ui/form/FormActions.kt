package ir.ilam.inspection.field.ui.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.field.R
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone

/**
 * The end of every form: finish and send, or throw the draft away — the
 * latter only after a second, explicit yes.
 */
@Composable
fun FormActions(canFinish: Boolean, busy: Boolean, onFinish: () -> Unit, onDiscard: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg)) {
        PrimaryButton(
            text = stringResource(R.string.form_finish),
            onClick = onFinish,
            enabled = canFinish && !busy,
            busy = busy,
            icon = Icons.AutoMirrored.Filled.Send,
            tone = Tone.SUCCESS,
            modifier = Modifier.fillMaxWidth()
        )
        SecondaryButton(
            text = stringResource(R.string.form_discard),
            onClick = { confirming = true },
            icon = Icons.Filled.DeleteOutline,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
        )
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.form_discard_title)) },
            text = { Text(stringResource(R.string.form_discard_message)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onDiscard() }) { Text(stringResource(R.string.form_discard_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}
