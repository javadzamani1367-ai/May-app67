package ir.ilam.inspection.ui.referrals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import ir.ilam.inspection.data.model.ReferralOutcome
import ir.ilam.inspection.ui.common.MultilineField
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone

/**
 * The light result: what was found, and a note. «سایر» needs the note — the
 * server refuses it without one, so the form does too. Saved on the phone
 * first and sent when there is a connection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultDialog(onDismiss: () -> Unit, onSave: (ReferralOutcome, String) -> Unit) {
    var outcome by rememberSaveable { mutableStateOf<ReferralOutcome?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    val ready = outcome != null && (outcome != ReferralOutcome.OTHER || note.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.referral_record_result)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.referral_result_hint), style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    OUTCOME_LABELS.forEach { (value, label) ->
                        ToneChip(
                            text = stringResource(label),
                            selected = outcome == value,
                            tone = if (value == ReferralOutcome.CONFIRMED) Tone.DANGER else Tone.INFO,
                            onClick = { outcome = value }
                        )
                    }
                }
                MultilineField(
                    label = stringResource(R.string.referral_result_note),
                    value = note,
                    onValueChange = { note = it },
                    error = if (outcome == ReferralOutcome.OTHER && note.isBlank()) stringResource(R.string.referral_result_note_required) else null
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { outcome?.let { onSave(it, note) } }, enabled = ready) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
