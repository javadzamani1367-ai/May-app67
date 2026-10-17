package ir.ilam.inspection.ui.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Manage
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate

/**
 * What the server has to tell this account: a case handed to them, an alert
 * that someone has not acted, a unit's progress, a duplicate serial (the
 * manager's alone). A notice about a case opens that case.
 */
@Composable
fun NotificationsScreen(viewModel: ManageViewModel, onBack: () -> Unit, onOpenCase: (String) -> Unit) {
    val state by viewModel.notes.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loadNotes() }
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.nt_title), onBack = onBack) {
            HeaderAction(Icons.Filled.Refresh, stringResource(R.string.fi_refresh), { viewModel.loadNotes() })
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        if (!state.busy && state.notes.isEmpty() && state.problem == null) {
            EmptyState(message = stringResource(R.string.nt_empty), icon = Icons.Filled.Campaign)
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
            items(state.notes, key = { it.id }) { note -> NoteRow(note, onOpenCase) }
        }
    }
}

@Composable
private fun NoteRow(note: Manage.Note, onOpenCase: (String) -> Unit) {
    val open = note.reportId?.let { id -> { onOpenCase(id) } }
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = open) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(note.title, style = MaterialTheme.typography.titleSmall)
            note.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(PersianDate.formatWithTime(note.at), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!note.read) StatusBadge(stringResource(R.string.nt_new), noteTone(note.kind))
        }
    }
}

private fun noteTone(kind: String): Tone = when (kind) {
    "expert_late", "executor_late", "device_duplicate", "no_executor" -> Tone.DANGER
    "case_referred", "case_assigned", "approval_pending" -> Tone.WARNING
    "approved", "response" -> Tone.SUCCESS
    else -> Tone.INFO
}
