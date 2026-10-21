package ir.ilam.inspection.ui.history

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Readings
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.fielditems.ProblemLine
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * Inspection history: every panel and pole of the user's areas with its readings,
 * newest first; one tap opens a plate's series. Excel and Word for the manager and
 * headquarters only — the server says which, and refuses the rest.
 */
@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onBack: () -> Unit, onOpen: (Readings.Summary) -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state.search == null) viewModel.load() }
    var plate by remember { mutableStateOf(state.filter.plate) }
    val filter = state.filter
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.hist_title), subtitle = stringResource(R.string.hist_subtitle), onBack = onBack) {
            if (state.search?.canExport == true) {
                HeaderAction(Icons.Filled.TableChart, stringResource(R.string.hist_excel), onClick = { viewModel.export(context, "xlsx") })
                HeaderAction(Icons.Filled.Description, stringResource(R.string.hist_word), onClick = { viewModel.export(context, "docx") })
            }
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(Spacing.sm)) }
        Column(modifier = Modifier.padding(horizontal = Spacing.screen)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(stringResource(R.string.hist_plate), plate, { plate = it }, modifier = Modifier.weight(1f), ltr = true,
                    onDone = { viewModel.setFilter(filter.copy(plate = plate)) })
                IconButton(onClick = { viewModel.setFilter(filter.copy(plate = plate)) }) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.hist_search), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(null to R.string.hist_all, Readings.PANEL to R.string.hist_panels, Readings.POLE to R.string.hist_poles)
                    .forEach { (asset, label) ->
                        ToneChip(stringResource(label), filter.asset == asset, Tone.BRAND, onClick = { viewModel.setFilter(filter.copy(asset = asset)) })
                    }
                ToneChip(stringResource(R.string.hist_only_abnormal), filter.anomaly, Tone.DANGER,
                    onClick = { viewModel.setFilter(filter.copy(anomaly = !filter.anomaly)) })
            }
        }
        val keys = state.search?.keys.orEmpty()
        if (keys.isEmpty() && state.search != null && !state.busy) {
            EmptyState(message = stringResource(R.string.hist_empty), icon = Icons.Filled.History)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
                items(keys, key = { it.key.toString() }) { summary -> SummaryCard(summary) { onOpen(summary) } }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: Readings.Summary, onOpen: () -> Unit) {
    val last = summary.last
    SectionCard(
        title = PersianNumbers.toPersian(summary.label),
        subtitle = summary.areaLabel + " · " + stringResource(R.string.hist_count, PersianNumbers.toPersian(summary.count)),
        icon = Icons.Filled.History,
        tone = if (summary.anomalies > 0) Tone.DANGER else Tone.BRAND,
        modifier = Modifier.clickable(onClick = onOpen),
        trailing = { if (summary.anomalies > 0) StatusBadge(PersianNumbers.toPersian(summary.anomalies), Tone.DANGER) }
    ) {
        Text(
            stringResource(R.string.hist_last, PersianDate.formatWithTime(summary.lastAt),
                if (summary.key.part == Readings.THERMAL) number(last?.tempMax) + "°"
                else listOf(last?.r, last?.s, last?.t).joinToString(" / ") { number(it) }),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
