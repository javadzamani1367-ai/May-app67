package ir.ilam.inspection.ui.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.sync.Manage
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.AppTextField
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
 * The miner device registry, the manager's alone: every device of every
 * area, searchable by serial, model, subscriber, holder or subscription. A
 * serial found in more than one case is a device that moved — the alerts at
 * the top say where it was before and with whom.
 */
@Composable
fun DevicesScreen(viewModel: ManageViewModel, onBack: () -> Unit) {
    val state by viewModel.devices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.loadDevices() }
    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = stringResource(R.string.dv_title), subtitle = stringResource(R.string.dv_subtitle), onBack = onBack) {
            HeaderAction(Icons.Filled.Share, stringResource(R.string.sc_excel), { viewModel.shareDevices(context) })
        }
        if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        ProblemLine(state.problem)
        Column(modifier = Modifier.padding(horizontal = Spacing.screen)) {
            AppTextField(stringResource(R.string.dv_search), state.query, viewModel::searchDevices)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ToneChip(stringResource(R.string.dv_all), !state.duplicatesOnly, Tone.BRAND, onClick = { viewModel.duplicatesOnly(false) })
                ToneChip(stringResource(R.string.dv_duplicates), state.duplicatesOnly, Tone.DANGER, onClick = { viewModel.duplicatesOnly(true) })
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
            if (state.alerts.isNotEmpty()) item { Alerts(state.alerts, viewModel::openHistory) }
            items(state.page?.devices.orEmpty(), key = { it.reportId + (it.serial ?: "") }) { device ->
                DeviceRow(device, onClick = { device.serialKey?.let(viewModel::openHistory) })
            }
        }
    }
    state.history?.let { history ->
        AlertDialog(
            onDismissRequest = viewModel::closeHistory,
            title = { Text(stringResource(R.string.dv_history, state.historySerial)) },
            text = {
                Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    history.forEach { DeviceLines(it) }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::closeHistory) { Text(stringResource(R.string.action_confirm)) } }
        )
    }
}

@Composable
private fun Alerts(alerts: List<Manage.Alert>, onOpen: (String) -> Unit) {
    SectionCard(title = stringResource(R.string.dv_alerts), icon = Icons.Filled.WarningAmber, tone = Tone.DANGER) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            alerts.take(20).forEach { alert ->
                AppCard(onClick = { onOpen(alert.serial) }) {
                    Column(modifier = Modifier.padding(Spacing.sm)) {
                        Text(alert.serial, style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr))
                        Text(stringResource(R.string.dv_before, alert.before), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.dv_now, alert.now), style = MaterialTheme.typography.bodySmall)
                        if (!alert.seen) StatusBadge(stringResource(R.string.dv_new), Tone.DANGER)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: Manage.Device, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) {
        Column(modifier = Modifier.padding(Spacing.md)) { DeviceLines(device) }
    }
}

@Composable
private fun DeviceLines(device: Manage.Device) {
    Row {
        Text(device.serial ?: "—", style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
            modifier = Modifier.weight(1f))
        if (device.seenIn > 1) StatusBadge(stringResource(R.string.dv_seen_in, PersianNumbers.toPersian(device.seenIn)), Tone.DANGER, solid = true)
    }
    Text(listOfNotNull(device.model, device.powerWatt?.let { PersianNumbers.toPersian(it.toInt()) + " W" }).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall)
    Text(PersianNumbers.toPersian(device.code) + " · " + device.area + (device.foundAt?.let { " · " + PersianDate.format(it) } ?: ""),
        style = MaterialTheme.typography.bodySmall)
    val people = listOfNotNull(device.subscriber?.let { stringResource(R.string.dv_subscriber, it) },
        device.owner?.let { stringResource(R.string.dv_owner, it) })
    if (people.isNotEmpty()) Text(people.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
}
