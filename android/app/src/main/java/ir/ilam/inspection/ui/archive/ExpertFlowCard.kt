package ir.ilam.inspection.ui.archive

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.sync.Flow
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate

/**
 * For the expert: who referred this case to them and until when, and where
 * what they sent for approval stands — whether the area executor or the
 * manager has seen it yet, and what they decided. As of the last sync.
 */
@Composable
fun ExpertFlowCard(reportId: String) {
    val repository = LocalContext.current.container.expertFlowRepository
    val assignment by produceState<Flow.Assignment?>(null, reportId) { value = repository.assignment(reportId) }
    val status by produceState<Flow.Status?>(null, reportId) { value = repository.status(reportId) }
    val referred = assignment
    val sent = status
    if (referred == null && sent == null) return
    SectionCard(
        title = stringResource(R.string.ef_title),
        subtitle = stringResource(R.string.ef_as_of_sync),
        icon = Icons.Filled.AccountTree,
        tone = Tone.INFO,
        trailing = { sent?.let { StatusBadge(decisionLabel(it), decisionTone(it), solid = true) } }
    ) {
        Column {
            referred?.let {
                ValueRow(stringResource(R.string.ef_referred_by), listOfNotNull(it.by, roleName(it.byRole)).joinToString(" — "))
                it.dueAt?.let { due -> ValueRow(stringResource(R.string.ef_due), PersianDate.format(due)) }
                it.note?.let { note -> ValueRow(stringResource(R.string.ef_note), note) }
            }
            sent?.let {
                ValueRow(stringResource(R.string.ef_sent), PersianDate.formatWithTime(it.submittedAt))
                ValueRow(stringResource(R.string.ef_seen), it.seenAt?.let { at ->
                    PersianDate.formatWithTime(at) + (roleName(it.seenRole)?.let { r -> " — $r" } ?: "")
                } ?: stringResource(R.string.ef_not_seen))
                it.decidedAt?.let { at ->
                    ValueRow(stringResource(R.string.ef_decided), PersianDate.formatWithTime(at) + (roleName(it.decidedRole)?.let { r -> " — $r" } ?: ""))
                }
                it.comment?.let { comment -> ValueRow(stringResource(R.string.ef_comment), comment) }
            }
        }
    }
}

@Composable
private fun roleName(role: Int?): String? = when (role) {
    1 -> stringResource(R.string.role_manager)
    4 -> stringResource(R.string.role_executor)
    else -> null
}

@Composable
private fun decisionLabel(status: Flow.Status): String = stringResource(
    when {
        status.decision == 1 -> R.string.ef_approved
        status.decision == 2 -> R.string.ef_returned
        status.seenAt != null -> R.string.ef_under_review
        else -> R.string.ef_waiting
    }
)

private fun decisionTone(status: Flow.Status): Tone = when (status.decision) {
    1 -> Tone.SUCCESS
    2 -> Tone.DANGER
    else -> if (status.seenAt != null) Tone.INFO else Tone.WARNING
}
