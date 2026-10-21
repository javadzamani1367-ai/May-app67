package ir.ilam.inspection.field.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.History
import ir.ilam.inspection.field.data.HistoryRow
import ir.ilam.inspection.field.data.HistoryTarget
import ir.ilam.inspection.field.data.PhaseReading
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.sync.HistoryLookup
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/**
 * «مقایسه با سوابق» beside a panel's main breaker or feeder, a pole, or a thermal
 * plate. Disabled until the plate is typed: the history is the plate's.
 */
@Composable
fun CompareButton(target: HistoryTarget, current: PhaseReading? = null, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    SecondaryButton(
        text = stringResource(if (target.plateKey.isEmpty()) R.string.history_needs_plate else R.string.history_compare),
        onClick = { open = true },
        icon = Icons.Filled.History,
        enabled = target.plateKey.isNotEmpty(),
        modifier = modifier.fillMaxWidth().padding(top = Spacing.sm)
    )
    if (open) HistoryDialog(target, current, onDismiss = { open = false })
}

/**
 * The box a little smaller than the screen: this visit first, then every earlier
 * reading of the same plate, newest to oldest, with each phase's change from the
 * reading before it. Scrolls when the history is long.
 */
@Composable
fun HistoryDialog(target: HistoryTarget, current: PhaseReading?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cache = context.field.history
    val prefs = context.field.prefs
    val lookup by produceState<HistoryLookup?>(null, target) { value = cache.lookup(target) }
    val now = remember(current) {
        current?.let {
            HistoryRow(System.currentTimeMillis(), it.r.toDoubleOrNull(), it.s.toDoubleOrNull(), it.t.toDoubleOrNull(), current = true)
        }?.takeIf { it.r != null || it.s != null || it.t != null }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f)
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(title(target), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                }
                val loaded = lookup
                if (loaded == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md))
                } else {
                    Text(
                        if (loaded.live) stringResource(R.string.history_live)
                        else stringResource(R.string.history_copy, loaded.copiedAt?.let { PersianDate.formatWithTime(it) } ?: "—"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val rows = listOfNotNull(now) + loaded.rows
                    if (rows.isEmpty()) {
                        Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = Spacing.lg))
                    } else {
                        val thermal = target.part == History.THERMAL
                        if (!thermal) PhaseChart(rows, modifier = Modifier.padding(vertical = Spacing.sm))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(rows) { index, row ->
                                if (thermal) ThermalRow(row, cache)
                                else AmpRow(row, rows.drop(index + 1).filterNot { it.current }, prefs.historyRisePct, prefs.historyRiseMinA)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}

@Composable
private fun title(target: HistoryTarget): String {
    val plate = PersianNumbers.toPersian(target.plate.trim())
    return when (target.part) {
        History.MAIN -> stringResource(R.string.history_title_main, plate)
        History.POLE_PART -> stringResource(R.string.history_title_pole, plate)
        History.THERMAL -> stringResource(
            if (target.asset == History.PANEL) R.string.history_title_thermal_panel else R.string.history_title_thermal_pole, plate)
        else -> stringResource(R.string.history_title_feeder, plate, target.part)
    }
}
