package ir.ilam.inspection.ui.flow

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
import ir.ilam.inspection.sync.Flow
import ir.ilam.inspection.ui.common.MultilineField
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone

/**
 * The hand-off itself. Only the people the server offered for this case's
 * area are listed: an executor sees the experts of their own area, the
 * manager every executor and expert of the case's area. An executor left
 * unchosen means "the area's executor", as the server reads it.
 */
@Composable
fun FlowActionDialog(
    action: FlowAction,
    people: Flow.People?,
    onDismiss: () -> Unit,
    onConfirm: (personId: String?, dueDays: Int?, note: String) -> Unit
) {
    var person by remember { mutableStateOf<String?>(null) }
    var days by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val candidates = when (action) {
        FlowAction.EXECUTOR -> people?.executors.orEmpty()
        FlowAction.EXPERT -> people?.experts.orEmpty()
        FlowAction.FOLLOW_UP -> emptyList()
    }
    val ready = when (action) {
        FlowAction.EXPERT -> person != null
        FlowAction.FOLLOW_UP -> note.isNotBlank()
        FlowAction.EXECUTOR -> true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(when (action) {
                FlowAction.EXECUTOR -> R.string.fl_to_executor
                FlowAction.EXPERT -> R.string.fl_to_expert
                FlowAction.FOLLOW_UP -> R.string.fl_follow_up
            }))
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (action != FlowAction.FOLLOW_UP) {
                    if (candidates.isEmpty()) {
                        Text(stringResource(R.string.fl_no_people), color = MaterialTheme.colorScheme.error)
                    }
                    if (action == FlowAction.EXECUTOR) {
                        ToneChip(stringResource(R.string.fl_area_executor), person == null, Tone.BRAND, onClick = { person = null },
                            modifier = Modifier.fillMaxWidth())
                    }
                    candidates.forEach { candidate ->
                        ToneChip(candidate.name, person == candidate.id, Tone.ACCENT, onClick = { person = candidate.id },
                            modifier = Modifier.fillMaxWidth())
                    }
                }
                if (action == FlowAction.EXPERT) {
                    NumberField(stringResource(R.string.fl_due_days), days, { days = it.take(2) })
                }
                MultilineField(
                    label = stringResource(if (action == FlowAction.FOLLOW_UP) R.string.fl_follow_note else R.string.fl_note),
                    value = note,
                    onValueChange = { note = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(person, days.toIntOrNull(), note) }, enabled = ready) {
                Text(stringResource(R.string.fi_action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
