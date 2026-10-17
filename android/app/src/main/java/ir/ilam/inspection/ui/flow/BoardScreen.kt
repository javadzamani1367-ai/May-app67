package ir.ilam.inspection.ui.flow

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.sync.Flow
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/** The stages of a case still waiting on someone, in the order a case goes through them. */
val BOARD_STAGES = listOf("awaiting_referral", "with_executor", "with_expert", "in_progress", "awaiting_approval", "returned", "approved")

/**
 * The follow-up board: every open case of the account's areas, each in the
 * stage it is stuck at, late ones first. The manager sees every area; an
 * executor only their own, and the server decides which.
 */
@Composable
fun BoardScreen(viewModel: FlowViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onIntake: () -> Unit) {
    val state by viewModel.board.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadBoard() }
    val board = state.board
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(
            title = stringResource(R.string.fl_board_title),
            subtitle = stringResource(if (UserRole.isExecutor) R.string.fl_board_subtitle_executor else R.string.fl_board_subtitle),
            onBack = onBack
        ) {
            HeaderAction(Icons.Filled.Add, stringResource(R.string.fl_intake_title), onIntake)
            HeaderAction(Icons.Filled.Refresh, stringResource(R.string.fi_refresh), { viewModel.loadBoard() })
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            ToneChip(count(stringResource(R.string.fi_all), board?.cases?.size ?: 0), state.stage == null, Tone.BRAND,
                onClick = { viewModel.showStage(null) })
            ToneChip(count(stringResource(R.string.fl_late), board?.late ?: 0), state.stage == BoardState.LATE, Tone.DANGER,
                onClick = { viewModel.showStage(BoardState.LATE) })
            BOARD_STAGES.forEach { stage ->
                val n = board?.stageCounts?.get(stage) ?: 0
                if (n > 0) ToneChip(count(stageLabel(stage), n), state.stage == stage, stageTone(stage),
                    onClick = { viewModel.showStage(stage) })
            }
        }
        if (board != null && state.shown.isEmpty()) {
            EmptyState(message = stringResource(R.string.fl_board_empty), icon = Icons.Filled.Inbox)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
                items(state.shown, key = { it.id }) { case -> BoardRow(case, onClick = { onOpen(case.id) }) }
            }
        }
    }
}

@Composable
private fun BoardRow(case: Flow.BoardCase, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(PersianNumbers.toPersian(case.code.ifBlank { "—" }), style = MaterialTheme.typography.titleMedium.copy(
                    textDirection = TextDirection.Ltr), modifier = Modifier.weight(1f))
                StatusBadge(stageLabel(case.stage), if (case.late) Tone.DANGER else stageTone(case.stage), solid = case.late)
            }
            Text(case.type + " · " + case.area, style = MaterialTheme.typography.bodyMedium)
            case.address?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            val people = listOfNotNull(
                case.executor?.let { stringResource(R.string.fl_executor_is, it) },
                case.expert?.let { stringResource(R.string.fl_expert_is, it) }
            )
            if (people.isNotEmpty()) Text(people.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            (case.dueAt ?: case.referDueAt)?.let {
                Text(stringResource(R.string.fl_due_is, PersianDate.format(it)), style = MaterialTheme.typography.bodySmall,
                    color = if (case.late) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (case.autoReferred) Text(stringResource(R.string.fl_auto_referred), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun stageLabel(stage: String): String = stringResource(
    when (stage) {
        "awaiting_referral" -> R.string.fl_stage_awaiting_referral
        "with_executor" -> R.string.fl_stage_with_executor
        "with_expert" -> R.string.fl_stage_with_expert
        "awaiting_approval" -> R.string.fl_stage_awaiting_approval
        "returned" -> R.string.fl_stage_returned
        "approved" -> R.string.fl_stage_approved
        else -> R.string.fl_stage_in_progress
    }
)

fun stageTone(stage: String): Tone = when (stage) {
    "awaiting_referral", "with_executor" -> Tone.WARNING
    "returned" -> Tone.DANGER
    "approved" -> Tone.SUCCESS
    else -> Tone.INFO
}

@Composable
private fun count(label: String, n: Int): String = label + " " + PersianNumbers.toPersian(n)
