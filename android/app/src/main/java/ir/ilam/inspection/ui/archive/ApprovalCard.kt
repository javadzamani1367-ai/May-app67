package ir.ilam.inspection.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.approvalLabel
import ir.ilam.inspection.ui.common.icon
import ir.ilam.inspection.ui.common.tone
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
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

    SectionCard(
        title = stringResource(R.string.approval_state_label),
        icon = state.icon(),
        tone = state.tone(),
        subtitle = report.approvalAt?.let { stringResource(R.string.approval_decided_at, PersianDate.formatWithTime(it)) },
        trailing = { StatusBadge(text = approvalLabel(state), tone = state.tone(), solid = state == ApprovalState.RETURNED) }
    ) {
        Column {
            report.approvalComment?.takeIf { it.isNotBlank() }?.let {
                val colors = Tavan.colors.of(if (state == ApprovalState.RETURNED) Tone.DANGER else Tone.NEUTRAL)
                Surface(
                    color = colors.container,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            stringResource(R.string.approval_comment),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.strong
                        )
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onContainer)
                    }
                }
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
    Text(
        stringResource(R.string.approval_manager_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    MultilineField(
        label = stringResource(R.string.approval_comment),
        value = comment,
        onValueChange = onCommentChange
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PrimaryButton(
            text = stringResource(R.string.approval_approve),
            onClick = { onDecide(ApprovalState.APPROVED) },
            icon = Icons.Filled.Verified,
            tone = Tone.SUCCESS,
            modifier = Modifier.weight(1f)
        )
        SecondaryButton(
            text = stringResource(R.string.approval_return),
            onClick = { onDecide(ApprovalState.RETURNED) },
            icon = Icons.Filled.Replay,
            tone = Tone.DANGER,
            modifier = Modifier.weight(1f)
        )
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
        else -> PrimaryButton(
            text = stringResource(R.string.approval_submit),
            onClick = onSubmit,
            icon = Icons.AutoMirrored.Filled.Send,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
    }
}
