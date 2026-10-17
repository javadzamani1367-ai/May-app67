package ir.ilam.inspection.ui.fielditems

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.FieldManage
import ir.ilam.inspection.ui.common.MultilineField
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone

/**
 * Handing an item to an area's crypto executor, or writing a follow-up note.
 *
 * The manager and the illegal-power executor hand items over; whoever is
 * following one up writes the note, which for an executor counts as acting
 * on it and holds back the alert to the manager.
 */
@Composable
fun FieldHandDialog(
    detail: FieldManage.Detail,
    onDismiss: () -> Unit,
    onHand: (executorId: String, note: String) -> Unit,
    onFollowUp: (note: String) -> Unit
) {
    var handing by remember { mutableStateOf(detail.canHand && detail.executors.isNotEmpty()) }
    var executor by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    val ready = if (handing) executor != null else note.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fh_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (detail.canHand && detail.executors.isNotEmpty()) {
                    ToneChip(stringResource(R.string.fh_hand), handing, Tone.BRAND, onClick = { handing = true },
                        modifier = Modifier.fillMaxWidth())
                }
                ToneChip(stringResource(R.string.fh_follow_up), !handing, Tone.NEUTRAL, onClick = { handing = false },
                    modifier = Modifier.fillMaxWidth())
                if (handing) {
                    Text(stringResource(R.string.fh_pick_executor), style = MaterialTheme.typography.labelLarge)
                    detail.executors.forEach { candidate ->
                        ToneChip(candidate.name, executor == candidate.id, Tone.ACCENT, onClick = { executor = candidate.id },
                            modifier = Modifier.fillMaxWidth())
                    }
                }
                MultilineField(
                    label = stringResource(if (handing) R.string.fh_note else R.string.fh_follow_note),
                    value = note,
                    onValueChange = { note = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val chosen = executor
                if (handing && chosen != null) onHand(chosen, note) else if (!handing) onFollowUp(note)
            }, enabled = ready) { Text(stringResource(R.string.fi_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
