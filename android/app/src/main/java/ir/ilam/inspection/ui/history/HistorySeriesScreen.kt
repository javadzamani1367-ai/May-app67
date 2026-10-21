package ir.ilam.inspection.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * One panel or pole's readings, newest first, with the three phases charted.
 * The manager and the area's crypto executor may refer the plate for a crypto
 * inspection from here; the case goes straight to the chosen expert.
 */
@Composable
fun HistorySeriesScreen(viewModel: HistoryViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var referring by remember { mutableStateOf(false) }
    val canRefer = state.search?.canRefer == true && state.experts.isNotEmpty()
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = PersianNumbers.toPersian(state.label), subtitle = state.key?.area?.let { PersianNumbers.toPersian(it) }, onBack = onBack)
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        state.message?.let {
            Text(stringResource(it), color = Tavan.colors.success.strong, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.sm))
        }
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen)) {
            ReadingList(state.series, state.key?.part.orEmpty(), viewModel::image)
        }
        if (canRefer) BottomActionBar {
            PrimaryButton(stringResource(R.string.hist_refer), onClick = { referring = true }, icon = Icons.AutoMirrored.Filled.Send,
                modifier = Modifier.fillMaxWidth())
        }
    }
    if (referring) ReferDialog(state, onDismiss = { referring = false }) { expert, note ->
        referring = false
        viewModel.refer(expert, note)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReferDialog(state: HistoryState, onDismiss: () -> Unit, onRefer: (String, String) -> Unit) {
    var expert by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.hist_refer)) },
        text = {
            Column {
                Text(stringResource(R.string.hist_refer_hint), style = MaterialTheme.typography.bodySmall)
                FlowRow {
                    state.experts.forEach { person ->
                        ToneChip(person.name, expert == person.id, Tone.BRAND, onClick = { expert = person.id })
                    }
                }
                AppTextField(stringResource(R.string.hist_refer_note), note, { note = it }, singleLine = false)
            }
        },
        confirmButton = { TextButton(onClick = { expert?.let { onRefer(it, note) } }, enabled = expert != null) { Text(stringResource(R.string.hist_refer)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
