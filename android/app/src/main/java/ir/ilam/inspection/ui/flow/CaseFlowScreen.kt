package ir.ilam.inspection.ui.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.sync.Flow
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/** Which of the three hand-offs the dialog is open for. */
enum class FlowAction { EXECUTOR, EXPERT, FOLLOW_UP }

/**
 * One case's journey, live: who registered it, which executor and expert have
 * it, until when, the timeline of everything that happened, and the hand-offs
 * this account may make — the manager to an area executor, the manager or the
 * executor to an expert of the case's area, and a follow-up note.
 */
@Composable
fun CaseFlowScreen(reportId: String, viewModel: FlowViewModel, onBack: () -> Unit, onOpenCase: (String) -> Unit) {
    val state by viewModel.case.collectAsStateWithLifecycle()
    var action by remember { mutableStateOf<FlowAction?>(null) }
    LaunchedEffect(reportId) { viewModel.openCase(reportId) }
    val flow = state.summary
    val open = flow != null && flow.status == 0 && flow.canAct

    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(
            title = flow?.code?.let { PersianNumbers.toPersian(it) } ?: stringResource(R.string.fl_case_title),
            subtitle = flow?.let { it.type + " · " + it.area },
            onBack = onBack
        )
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        if (state.saved) {
            Text(stringResource(R.string.fi_saved), color = Tavan.colors.success.strong, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm))
        }
        if (flow == null) return@Column
        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            Hands(flow)
            // The full case is the phone's own copy; a case registered a moment ago arrives with the next sync.
            SecondaryButton(stringResource(R.string.fl_open_case), onClick = { onOpenCase(reportId) },
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs))
            Timeline(flow.timeline)
        }
        BottomActionBar {
            if (open) {
                PrimaryButton(stringResource(R.string.fl_to_expert), onClick = { action = FlowAction.EXPERT }, enabled = !state.busy,
                    icon = Icons.Filled.PersonSearch, modifier = Modifier.weight(1f))
                // Handing to the area executor belongs to the top of the domain: the manager for
                // crypto, headquarters for illegal power.
                if (if (flow?.caseKind == 1) UserRole.isIllegalHq else UserRole.isFullManager) {
                    SecondaryButton(stringResource(R.string.fl_to_executor), onClick = { action = FlowAction.EXECUTOR },
                        enabled = !state.busy, icon = Icons.Filled.SupervisorAccount, modifier = Modifier.weight(1f))
                }
            }
            SecondaryButton(stringResource(R.string.fl_follow_up), onClick = { action = FlowAction.FOLLOW_UP }, enabled = !state.busy,
                icon = Icons.Filled.EditNote, modifier = Modifier.weight(1f))
        }
    }

    action?.let { chosen ->
        FlowActionDialog(chosen, state.people, onDismiss = { action = null }) { personId, dueDays, note ->
            val done = { action = null }
            when (chosen) {
                FlowAction.EXECUTOR -> viewModel.toExecutor(reportId, personId, note, done)
                FlowAction.EXPERT -> personId?.let { viewModel.toExpert(reportId, it, dueDays, note, done) }
                FlowAction.FOLLOW_UP -> viewModel.followUp(reportId, note, done)
            }
        }
    }
}

@Composable
private fun Hands(flow: Flow.Summary) {
    SectionCard(
        title = flow.originName.ifBlank { stringResource(R.string.fl_case_title) },
        icon = Icons.Filled.AccountTree,
        tone = Tone.BRAND,
        trailing = { if (flow.expertLate) StatusBadge(stringResource(R.string.fl_late), Tone.DANGER, solid = true) }
    ) {
        Column {
            flow.intakeCode?.let { ValueRow(stringResource(R.string.fl_intake_code), PersianNumbers.toPersian(it), ltr = true) }
            flow.intakeBy?.let { ValueRow(stringResource(R.string.fl_intake_by), it) }
            flow.intakeNote?.let { ValueRow(stringResource(R.string.fl_intake_note), it) }
            ValueRow(stringResource(R.string.fl_address), flow.address)
            flow.referDueAt?.let { ValueRow(stringResource(R.string.fl_refer_due), PersianDate.formatWithTime(it)) }
            ValueRow(stringResource(R.string.fl_executor), flow.executor?.let {
                if (flow.autoReferred) it + " · " + stringResource(R.string.fl_auto_referred) else it
            })
            ValueRow(stringResource(R.string.fl_expert), flow.expert)
            flow.dueAt?.let { ValueRow(stringResource(R.string.fl_visit_due), PersianDate.format(it)) }
            ValueRow(stringResource(R.string.fl_visited), flow.visitedAt?.let { PersianDate.format(it) })
            ValueRow(stringResource(R.string.fl_approved), flow.approvedAt?.let { PersianDate.format(it) })
            ValueRow(stringResource(R.string.fl_dispatched), flow.dispatchedAt?.let { PersianDate.format(it) })
        }
    }
}

@Composable
private fun Timeline(events: List<Flow.Event>) {
    if (events.isEmpty()) return
    SectionCard(title = stringResource(R.string.fi_history), icon = Icons.Filled.History, tone = Tone.NEUTRAL) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            events.asReversed().forEach { event ->
                Column {
                    Text(event.what, style = MaterialTheme.typography.titleSmall)
                    Text(PersianDate.formatWithTime(event.at) + (event.who?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    event.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}
