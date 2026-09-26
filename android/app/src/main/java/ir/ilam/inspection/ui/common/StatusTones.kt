package ir.ilam.inspection.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Verified
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.ApprovalState
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.data.model.Urgency
import ir.ilam.inspection.ui.theme.Tone

/*
 * What each state of a case looks like. One table, so a case that is amber on
 * its card is amber on the dashboard and amber in its own header.
 */

fun ReportStatus.tone(): Tone = when (this) {
    ReportStatus.PENDING -> Tone.INFO
    ReportStatus.VISITED -> Tone.SUCCESS
    ReportStatus.ARCHIVED -> Tone.NEUTRAL
}

fun ReportStatus.icon(): ImageVector = when (this) {
    ReportStatus.PENDING -> Icons.Filled.PendingActions
    ReportStatus.VISITED -> Icons.Filled.CheckCircle
    ReportStatus.ARCHIVED -> Icons.Filled.Archive
}

fun ApprovalState.tone(): Tone = when (this) {
    ApprovalState.DRAFT -> Tone.NEUTRAL
    ApprovalState.PENDING -> Tone.WARNING
    ApprovalState.APPROVED -> Tone.SUCCESS
    ApprovalState.RETURNED -> Tone.DANGER
}

fun ApprovalState.icon(): ImageVector = when (this) {
    ApprovalState.DRAFT -> Icons.Filled.EditNote
    ApprovalState.PENDING -> Icons.Filled.HourglassTop
    ApprovalState.APPROVED -> Icons.Filled.Verified
    ApprovalState.RETURNED -> Icons.Filled.Replay
}

@Composable
fun approvalLabel(state: ApprovalState): String = stringResource(
    when (state) {
        ApprovalState.DRAFT -> R.string.approval_state_draft
        ApprovalState.PENDING -> R.string.approval_state_pending
        ApprovalState.APPROVED -> R.string.approval_state_approved
        ApprovalState.RETURNED -> R.string.approval_state_returned
    }
)

fun Urgency.tone(): Tone = when (this) {
    Urgency.NORMAL -> Tone.NEUTRAL
    Urgency.WARN -> Tone.WARNING
    Urgency.LATE -> Tone.DANGER
}
