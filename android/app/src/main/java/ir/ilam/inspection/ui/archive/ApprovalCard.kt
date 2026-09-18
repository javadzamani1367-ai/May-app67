package ir.ilam.inspection.ui.archive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.ApprovalState
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.ui.common.MultilineField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.util.PersianDate

/**
 * Where the case stands with the manager, and the one action available from
 * here.
 *
 * An expert sees what they are waiting for, or what came back and why. A
 * manager sees the case with an accept and a send-back beside it, because the
 * manager reading the case is exactly the moment the decision gets made.
 */
@Composable
fun ApprovalCard(
    report: ReportEntity,
    onSubmit: () -> Unit,
    onDecide: (ApprovalState, String) -> Unit
) {
    val state = ApprovalState.of(report.approvalState)
    var comment by remember(report.id) { mutableStateOf("") }

    SectionCard(title = stringResource(R.string.approval_state_label)) {
        Column {
            Text(
                text = stringResource(stateLabel(state)),
                style = MaterialTheme.typography.bodyLarge,
                color = when (state) {
                    ApprovalState.RETURNED -> MaterialTheme.colorScheme.error
                    ApprovalState.APPROVED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            report.approvalAt?.let {
                Text(
                    text = PersianDate.formatWithTime(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            report.approvalComment?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.approval_comment) + ": " + it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (UserRole.isManager) {
                ManagerDecision(
                    comment = comment,
                    onCommentChange = { comment = it },
                    onDecide = { decision -> onDecide(decision, comment) }
                )
            } else {
                ExpertActions(state = state, onSubmit = onSubmit)
            }
        }
    }
}

@Composable
private fun ManagerDecision(
    comment: String,
    onCommentChange: (String) -> Unit,
    onDecide: (ApprovalState) -> Unit
) {
    MultilineField(
        label = stringResource(R.string.approval_comment),
        value = comment,
        onValueChange = onCommentChange
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { onDecide(ApprovalState.APPROVED) },
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(R.string.approval_approve)) }
        OutlinedButton(
            onClick = { onDecide(ApprovalState.RETURNED) },
            modifier = Modifier.weight(1f)
        ) { Text(stringResource(R.string.approval_return)) }
    }
}

@Composable
private fun ExpertActions(state: ApprovalState, onSubmit: () -> Unit) {
    when (state) {
        ApprovalState.PENDING -> Text(
            text = stringResource(R.string.approval_locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        ApprovalState.APPROVED -> Unit
        else -> Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) { Text(stringResource(R.string.approval_submit)) }
    }
}

private fun stateLabel(state: ApprovalState): Int = when (state) {
    ApprovalState.DRAFT -> R.string.approval_state_draft
    ApprovalState.PENDING -> R.string.approval_state_pending
    ApprovalState.APPROVED -> R.string.approval_state_approved
    ApprovalState.RETURNED -> R.string.approval_state_returned
}
