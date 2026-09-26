package ir.ilam.inspection.ui.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.GroupHeader
import ir.ilam.inspection.ui.common.KpiTile
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import java.util.Locale

/**
 * The first thing the app shows: the state of the work, in the order it is
 * acted on. Figures first — what is waiting, what was found, what needs
 * attention — then the cases behind them, oldest first, so the case that has
 * waited longest is the one most likely to be opened.
 */
@Composable
fun DashboardTab(
    state: DashboardState,
    daysWaiting: (ReportEntity) -> Int,
    onOpen: (ReportEntity) -> Unit,
    onShowTab: (ReportStatus) -> Unit,
    onNewVisit: () -> Unit,
    onSettings: () -> Unit,
    onApprovals: () -> Unit,
    modifier: Modifier = Modifier
) {
    val counts = state.counts
    if (counts.total == 0) {
        EmptyState(
            title = stringResource(R.string.dashboard_empty_title),
            message = stringResource(R.string.dashboard_empty),
            icon = Icons.Outlined.Assignment,
            modifier = modifier,
            action = {
                PrimaryButton(
                    text = stringResource(R.string.action_new_visit),
                    onClick = onNewVisit,
                    icon = Icons.Filled.Add
                )
            }
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.md, bottom = 96.dp)
    ) {
        item { KpiGrid(state, onShowTab) }
        item { StatusOverviewCard(state) }
        item { OperationsCard(state, onSettings, onApprovals) }
        caseGroup(
            title = R.string.dashboard_attention,
            cases = state.oldestPending,
            state = state,
            daysWaiting = daysWaiting,
            onOpen = onOpen,
            onSeeAll = { onShowTab(ReportStatus.PENDING) }
        )
        caseGroup(
            title = R.string.dashboard_recent,
            cases = state.recent,
            state = state,
            daysWaiting = daysWaiting,
            onOpen = onOpen,
            onSeeAll = { onShowTab(ReportStatus.VISITED) }
        )
        item { TypeBreakdownCard(state) }
    }
}

@Composable
private fun KpiGrid(state: DashboardState, onShowTab: (ReportStatus) -> Unit) {
    val c = state.counts
    val done = (c.visited + c.archived).toInt()
    val alerts = (c.overdue + c.returned).toInt()
    KpiPair {
        KpiTile(
            label = stringResource(R.string.kpi_pending),
            value = PersianNumbers.toPersian(c.pending.toInt()),
            icon = Icons.Filled.PendingActions,
            tone = Tone.INFO,
            caption = if (c.overdue > 0) {
                stringResource(R.string.kpi_pending_caption, PersianNumbers.toPersian(c.overdue.toInt()))
            } else null,
            onClick = { onShowTab(ReportStatus.PENDING) },
            modifier = Modifier.weight(1f)
        )
        KpiTile(
            label = stringResource(R.string.kpi_visits),
            value = PersianNumbers.toPersian(done),
            icon = Icons.Filled.FactCheck,
            tone = Tone.SUCCESS,
            caption = if (c.visitedToday > 0) {
                stringResource(R.string.kpi_visits_today, PersianNumbers.toPersian(c.visitedToday.toInt()))
            } else null,
            onClick = { onShowTab(ReportStatus.VISITED) },
            modifier = Modifier.weight(1f)
        )
    }
    KpiPair {
        KpiTile(
            label = stringResource(R.string.kpi_detected),
            value = PersianNumbers.toPersian(c.detected),
            icon = Icons.Filled.Memory,
            tone = Tone.DANGER,
            caption = stringResource(R.string.kpi_detected_caption),
            modifier = Modifier.weight(1f)
        )
        KpiTile(
            label = stringResource(R.string.kpi_alerts),
            value = PersianNumbers.toPersian(alerts),
            icon = Icons.Filled.WarningAmber,
            tone = if (alerts > 0) Tone.WARNING else Tone.NEUTRAL,
            caption = stringResource(
                R.string.kpi_alerts_caption,
                PersianNumbers.toPersian(c.overdue.toInt()),
                PersianNumbers.toPersian(c.returned.toInt())
            ),
            onClick = { onShowTab(ReportStatus.PENDING) },
            modifier = Modifier.weight(1f)
        )
    }
    KpiPair {
        KpiTile(
            label = stringResource(R.string.kpi_devices),
            value = PersianNumbers.toPersian(c.devices),
            icon = Icons.Filled.DeveloperBoard,
            tone = Tone.ACCENT,
            modifier = Modifier.weight(1f)
        )
        KpiTile(
            label = stringResource(R.string.kpi_power),
            value = PersianNumbers.toPersian(String.format(Locale.US, "%.1f", c.devicePower / 1000.0)),
            icon = Icons.Filled.Bolt,
            tone = Tone.BRAND,
            caption = stringResource(R.string.kpi_power_unit),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun KpiPair(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

private fun LazyListScope.caseGroup(
    title: Int,
    cases: List<ReportEntity>,
    state: DashboardState,
    daysWaiting: (ReportEntity) -> Int,
    onOpen: (ReportEntity) -> Unit,
    onSeeAll: () -> Unit
) {
    if (cases.isEmpty()) return
    item {
        GroupHeader(text = stringResource(title)) {
            TextButton(onClick = onSeeAll) { Text(stringResource(R.string.dashboard_see_all)) }
        }
    }
    items(cases, key = { "g$title-${it.id}" }) { report ->
        CaseCard(
            report = report,
            daysWaiting = daysWaiting(report),
            devices = state.deviceCounts[report.id] ?: 0,
            onClick = { onOpen(report) },
            onDeleteRequest = {},
            // The dashboard's list already has the screen margin.
            horizontalPadding = 0.dp
        )
    }
}
