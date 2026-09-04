package ir.ilam.inspection.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.data.model.ReportType
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.JalaliDatePickerDialog
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.common.reportTypeLabel
import ir.ilam.inspection.ui.common.statusLabel
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

/** Counters plus the filtered Excel export the office asks for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContainer = context.container
    val viewModel: StatsViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(appContainer) { StatsViewModel(it) } }
    )
    val total by viewModel.total.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val visited by viewModel.visited.collectAsStateWithLifecycle()
    val archived by viewModel.archived.collectAsStateWithLifecycle()
    val byType by viewModel.byType.collectAsStateWithLifecycle()
    val byCounty by viewModel.byCounty.collectAsStateWithLifecycle()
    val totalPower by viewModel.totalPower.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            SectionCard(title = stringResource(R.string.stats_title)) {
                Column {
                    ValueRow(stringResource(R.string.stats_total), PersianNumbers.toPersian(total))
                    ValueRow(stringResource(R.string.stats_pending), PersianNumbers.toPersian(pending))
                    ValueRow(stringResource(R.string.stats_visited), PersianNumbers.toPersian(visited))
                    ValueRow(stringResource(R.string.stats_archived), PersianNumbers.toPersian(archived))
                    ValueRow(
                        stringResource(R.string.stats_total_power),
                        stringResource(R.string.stats_watt, PersianNumbers.grouped(totalPower))
                    )
                }
            }
            SectionCard(title = stringResource(R.string.stats_by_type)) {
                Column {
                    ReportType.entries.forEach { type ->
                        ValueRow(
                            reportTypeLabel(type),
                            PersianNumbers.toPersian(byType[type.code] ?: 0)
                        )
                    }
                }
            }
            SectionCard(title = stringResource(R.string.stats_by_county)) {
                Column {
                    viewModel.counties.forEach { county ->
                        ValueRow(county, PersianNumbers.toPersian(byCounty[county] ?: 0))
                    }
                }
            }
            SectionCard(title = stringResource(R.string.export_excel)) {
                Column {
                    DropdownField(
                        label = stringResource(R.string.form_status),
                        options = ReportStatus.entries.toList(),
                        selected = filter.status,
                        optionLabel = { statusLabel(it) },
                        onSelect = viewModel::setStatus
                    )
                    DropdownField(
                        label = stringResource(R.string.intake_report_type),
                        options = ReportType.entries.toList(),
                        selected = filter.type,
                        optionLabel = { reportTypeLabel(it) },
                        onSelect = viewModel::setType
                    )
                    DropdownField(
                        label = stringResource(R.string.intake_county),
                        options = viewModel.counties,
                        selected = filter.county,
                        optionLabel = { it },
                        onSelect = viewModel::setCounty
                    )
                    OutlinedButton(
                        onClick = { pickingFrom = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            stringResource(R.string.filter_from_date) + ": " +
                                (filter.fromDate?.let { PersianDate.format(it) }
                                    ?: stringResource(R.string.filter_all))
                        )
                    }
                    OutlinedButton(
                        onClick = { pickingTo = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text(
                            stringResource(R.string.filter_to_date) + ": " +
                                (filter.toDate?.let { PersianDate.format(it) }
                                    ?: stringResource(R.string.filter_all))
                        )
                    }
                    OutlinedButton(
                        onClick = viewModel::clearFilter,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Text(stringResource(R.string.action_clear_filter))
                    }
                    Button(
                        onClick = { viewModel.exportExcel(context) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        Text(stringResource(R.string.export_excel))
                    }
                }
            }
            Spacer(modifier = Modifier.padding(bottom = 24.dp))
        }
    }

    if (pickingFrom) {
        JalaliDatePickerDialog(
            initialMillis = filter.fromDate ?: System.currentTimeMillis(),
            onDismiss = { pickingFrom = false },
            onPicked = {
                viewModel.setFromDate(it)
                pickingFrom = false
            }
        )
    }
    if (pickingTo) {
        JalaliDatePickerDialog(
            initialMillis = filter.toDate ?: System.currentTimeMillis(),
            onDismiss = { pickingTo = false },
            onPicked = {
                viewModel.setToDate(it)
                pickingTo = false
            }
        )
    }
}
