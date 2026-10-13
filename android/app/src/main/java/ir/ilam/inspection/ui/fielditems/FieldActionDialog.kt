package ir.ilam.inspection.ui.fielditems

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import ir.ilam.inspection.ui.common.DateRow
import ir.ilam.inspection.ui.common.JalaliDatePickerDialog
import ir.ilam.inspection.ui.common.MultilineField
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import java.util.Calendar

/**
 * The next step of an item: only the steps the server allows from where it
 * stands. A referral needs an expert and may have a due date (to the end of
 * that day); a rejection or a revisit needs a reason, since the field user is
 * shown it.
 */
@Composable
fun FieldActionDialog(
    detail: FieldManage.Detail,
    onDismiss: () -> Unit,
    onConfirm: (status: Int, note: String, expertId: String?, dueAt: Long?) -> Unit
) {
    var step by remember { mutableStateOf(detail.next.firstOrNull()) }
    var expert by remember { mutableStateOf<String?>(null) }
    var due by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    val current = step
    val ready = current != null && (!current.needsNote || note.isNotBlank()) && (!current.needsExpert || expert != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fi_action_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Label(R.string.fi_action_to)
                detail.next.forEach { option ->
                    ToneChip(stringResource(fieldStatusLabel(option.status)), option == current, fieldStatusTone(option.status),
                        onClick = { step = option }, modifier = Modifier.fillMaxWidth())
                }
                if (current?.needsExpert == true) {
                    Label(R.string.fi_action_expert)
                    if (detail.experts.isEmpty()) {
                        Text(stringResource(R.string.fi_action_no_experts), color = MaterialTheme.colorScheme.error)
                    }
                    detail.experts.forEach { candidate ->
                        ToneChip(candidate.name, expert == candidate.id, Tone.ACCENT, onClick = { expert = candidate.id },
                            modifier = Modifier.fillMaxWidth())
                    }
                    DateRow(
                        label = stringResource(R.string.fi_action_due),
                        value = due?.let { PersianDate.format(it) } ?: stringResource(R.string.fi_action_no_due),
                        onClick = { picking = true }
                    )
                    if (due != null) {
                        TextButton(onClick = { due = null }) { Text(stringResource(R.string.fi_action_clear_due)) }
                    }
                }
                MultilineField(
                    label = stringResource(if (current?.needsNote == true) R.string.fi_action_reason else R.string.fi_action_note),
                    value = note,
                    onValueChange = { note = it },
                    error = if (current?.needsNote == true && note.isBlank()) stringResource(R.string.fi_action_reason_needed) else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (current != null) {
                        val referral = current.needsExpert
                        onConfirm(current.status, note, if (referral) expert else null, if (referral) due else null)
                    }
                },
                enabled = ready
            ) { Text(stringResource(R.string.fi_action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )

    if (picking) {
        JalaliDatePickerDialog(
            initialMillis = due ?: (System.currentTimeMillis() + 3 * DAY),
            onDismiss = { picking = false },
            onPicked = {
                picking = false
                due = endOfDay(it)
            }
        )
    }
}

@Composable
private fun Label(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = Spacing.xs))
}

private const val DAY = 86_400_000L

/** A due date means "by the end of that day", as the web panel records it. */
private fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis
