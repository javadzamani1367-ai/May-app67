package ir.roozban.feature.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.PersianNames
import ir.roozban.core.calendar.PersianWeek
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.theme.TagColors
import ir.roozban.core.domain.Report
import ir.roozban.core.domain.ReportRange
import ir.roozban.core.model.Project
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportsScreen(onOpenReview: (Boolean) -> Unit, onBack: () -> Unit, viewModel: ReportsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reports_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), stringResource(R.string.reports_back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val ranges = listOf(ReportRange.WEEK to R.string.reports_week, ReportRange.MONTH to R.string.reports_month)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ranges.forEachIndexed { i, (r, label) ->
                    SegmentedButton(
                        selected = state.range == r,
                        onClick = { viewModel.setRange(r) },
                        shape = SegmentedButtonDefaults.itemShape(i, ranges.size),
                    ) { Text(stringResource(label)) }
                }
            }
            val report = state.report
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                // RTL: the earlier period is on the right.
                IconButton(onClick = viewModel::previous) { Icon(painterResource(DsR.drawable.ic_chevron_right), stringResource(R.string.reports_previous)) }
                Text(
                    report?.let { formatPeriod(it.period, state.range == ReportRange.MONTH) }.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::next, enabled = !state.isCurrent) {
                    Icon(painterResource(DsR.drawable.ic_chevron_left), stringResource(R.string.reports_next))
                }
            }
            if (report != null) ReportBody(report, state.previous, state.projects, state.range)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = { onOpenReview(false) }, label = { Text(stringResource(R.string.reports_open_daily)) },
                    leadingIcon = { Icon(painterResource(DsR.drawable.ic_review), null) })
                AssistChip(onClick = { onOpenReview(true) }, label = { Text(stringResource(R.string.reports_open_weekly)) },
                    leadingIcon = { Icon(painterResource(DsR.drawable.ic_review), null) })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ReportBody(report: Report, previous: Report?, projects: Map<String, Project>, range: ReportRange) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(stringResource(R.string.reports_completed), PersianDigits.format(report.completed),
            previous?.let { formatChange(report.completed, it.completed) }, Modifier.weight(1f))
        StatCard(stringResource(R.string.reports_tracked), formatMinutes(report.trackedMinutes),
            previous?.let { formatChange(report.trackedMinutes, it.trackedMinutes) }, Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(stringResource(R.string.reports_focus), stringResource(R.string.reports_focus_value, PersianDigits.format(report.focusSessions)),
            formatMinutes(report.focusMinutes), Modifier.weight(1f), subtitleIsChange = false)
        StatCard(stringResource(R.string.reports_habits),
            report.habitRate?.let { stringResource(R.string.reports_percent, PersianDigits.format((it * 100).roundToInt())) } ?: "—",
            null, Modifier.weight(1f))
    }
    if (report.completed == 0 && report.trackedMinutes == 0 && report.focusSessions == 0) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.reports_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val dayLabels = report.days.map { d ->
        if (range == ReportRange.WEEK) {
            PersianNames.WEEKDAYS_SHORT[PersianWeek.indexOf(d.date.dayOfWeek)]
        } else {
            d.date.toJalali().day.let { if (it == 1 || it % 5 == 0) PersianDigits.format(it) else "" }
        }
    }
    val todayIndex = report.days.indexOfFirst { it.date == java.time.LocalDate.now() }.takeIf { it >= 0 }
    Section(stringResource(R.string.reports_completed_per_day)) {
        BarChart(report.days.map { it.completed }, dayLabels, MaterialTheme.colorScheme.primary, highlight = todayIndex,
            description = report.days.joinToString("، ") { PersianDigits.format(it.completed) })
    }
    if (report.trackedMinutes > 0) {
        Section(stringResource(R.string.reports_time_per_day)) {
            BarChart(report.days.map { it.trackedMinutes }, dayLabels, MaterialTheme.colorScheme.secondary, highlight = todayIndex,
                description = report.days.joinToString("، ") { formatMinutes(it.trackedMinutes) })
        }
        val slices = report.byProject.filter { it.minutes > 0 }.take(6).map { slice ->
            val project = slice.projectId?.let { projects[it] }
            DonutSlice(
                value = slice.minutes.toFloat(),
                color = if (project != null) TagColors.color(project.color) else MaterialTheme.colorScheme.outline,
                label = project?.name ?: stringResource(R.string.reports_no_project),
                valueLabel = formatMinutesShort(slice.minutes),
            )
        }
        if (slices.isNotEmpty()) {
            Section(stringResource(R.string.reports_by_project)) {
                DonutChart(slices, center = formatMinutesShort(report.trackedMinutes))
            }
        }
        Section(stringResource(R.string.reports_hours)) {
            BarChart(
                report.hourly,
                (0 until 24).map { if (it % 6 == 0) PersianDigits.format(it) else "" },
                MaterialTheme.colorScheme.tertiary,
                height = 90.dp,
                highlight = report.peakHour,
                description = stringResource(R.string.reports_hours),
            )
            report.peakHour?.let {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.reports_peak_hour, formatHour(it)), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (report.topTasks.isNotEmpty()) {
            Section(stringResource(R.string.reports_top_tasks)) {
                report.topTasks.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(t.title ?: stringResource(R.string.reports_no_task), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(formatMinutes(t.minutes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatCard(label: String, value: String, subtitle: String?, modifier: Modifier, subtitleIsChange: Boolean = true) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            if (subtitle != null) {
                Text(
                    if (subtitleIsChange) stringResource(R.string.reports_vs_previous, subtitle) else subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(16.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
