package ir.ilam.inspection.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.BarRow
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.DateRow
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.JalaliDatePickerDialog
import ir.ilam.inspection.ui.common.KpiTile
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneIcon
import ir.ilam.inspection.ui.common.reportTypeLabel
import ir.ilam.inspection.ui.common.statusLabel
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.ui.theme.colorForReportType
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import java.util.Locale

/** The figures, the breakdowns as charts, and the filtered Excel export the office asks for. */
@Composable
fun StatsScreen(onBack: () -> Unit, onPerformance: () -> Unit) {
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
            TavanTopBar(
                title = stringResource(R.string.stats_title),
                subtitle = stringResource(R.string.stats_subtitle),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
        ) {
            // Manager only: how the units are performing is a different
            // question from how many cases exist, and it has its own report.
            if (UserRole.isManager) {
                AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), onClick = onPerformance) {
                    Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                        ToneIcon(icon = Icons.Filled.Groups, tone = Tone.BRAND)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                            Text(stringResource(R.string.performance_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.stats_performance_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                KpiTile(stringResource(R.string.stats_total), PersianNumbers.toPersian(total), Icons.Filled.Folder, Tone.BRAND, Modifier.weight(1f))
                KpiTile(stringResource(R.string.stats_pending), PersianNumbers.toPersian(pending), Icons.Filled.PendingActions, Tone.INFO, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 5.dp)) {
                KpiTile(stringResource(R.string.stats_visited), PersianNumbers.toPersian(visited), Icons.Filled.TaskAlt, Tone.SUCCESS, Modifier.weight(1f))
                KpiTile(stringResource(R.string.stats_archived), PersianNumbers.toPersian(archived), Icons.Filled.Archive, Tone.NEUTRAL, Modifier.weight(1f))
            }
            KpiTile(
                label = stringResource(R.string.stats_total_power),
                value = PersianNumbers.toPersian(String.format(Locale.US, "%.1f", totalPower / 1000.0)),
                icon = Icons.Filled.Bolt,
                tone = Tone.ACCENT,
                caption = stringResource(R.string.kpi_power_unit),
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
            )

            val typeMax = (byType.values.maxOrNull() ?: 0).coerceAtLeast(1)
            SectionCard(title = stringResource(R.string.stats_by_type), icon = Icons.Filled.Insights) {
                ReportType.entries.forEach { type ->
                    val count = byType[type.code] ?: 0
                    BarRow(
                        label = reportTypeLabel(type),
                        value = PersianNumbers.toPersian(count),
                        fraction = count.toFloat() / typeMax,
                        color = colorForReportType(type.code, Tavan.colors.dark)
                    )
                }
            }

            // Only the counties that have cases, busiest first: seventeen rows of
            // zeros said nothing.
            val counties = viewModel.counties.map { it to (byCounty[it] ?: 0) }.filter { it.second > 0 }
                .sortedByDescending { it.second }
            val countyMax = (counties.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
            SectionCard(title = stringResource(R.string.stats_by_county), icon = Icons.Filled.Map, tone = Tone.INFO) {
                if (counties.isEmpty()) {
                    Text(
                        stringResource(R.string.stats_by_county_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                counties.forEach { (county, count) ->
                    BarRow(
                        label = county,
                        value = PersianNumbers.toPersian(count),
                        fraction = count.toFloat() / countyMax,
                        color = Tavan.colors.info.strong
                    )
                }
            }

            SectionCard(
                title = stringResource(R.string.export_excel),
                subtitle = stringResource(R.string.stats_export_hint),
                icon = Icons.Filled.TableView,
                tone = Tone.SUCCESS
            ) {
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
                DateRow(
                    label = stringResource(R.string.filter_from_date),
                    value = filter.fromDate?.let { PersianDate.format(it) } ?: stringResource(R.string.filter_all),
                    onClick = { pickingFrom = true }
                )
                DateRow(
                    label = stringResource(R.string.filter_to_date),
                    value = filter.toDate?.let { PersianDate.format(it) } ?: stringResource(R.string.filter_all),
                    onClick = { pickingTo = true }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.action_clear_filter),
                        onClick = viewModel::clearFilter,
                        icon = Icons.Filled.FilterAlt,
                        modifier = Modifier.weight(1f)
                    )
                    PrimaryButton(
                        text = stringResource(R.string.export_excel),
                        onClick = { viewModel.exportExcel(context) },
                        busy = busy,
                        icon = Icons.Filled.TableView,
                        tone = Tone.SUCCESS,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
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
