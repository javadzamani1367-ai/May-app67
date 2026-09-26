package ir.ilam.inspection.ui.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.ApprovalState
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.data.model.ReportType
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.BarRow
import ir.ilam.inspection.ui.common.DonutChart
import ir.ilam.inspection.ui.common.LegendRow
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.Slice
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.ToneIcon
import ir.ilam.inspection.ui.common.approvalLabel
import ir.ilam.inspection.ui.common.icon
import ir.ilam.inspection.ui.common.reportTypeLabel
import ir.ilam.inspection.ui.common.statusLabel
import ir.ilam.inspection.ui.common.tone
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.ui.theme.colorForReportType
import ir.ilam.inspection.util.PersianNumbers

/** How the caseload splits across states, the approval cycle, and what was found on meters. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusOverviewCard(state: DashboardState) {
    val c = state.counts
    val colors = Tavan.colors
    val parts = listOf(
        Triple(ReportStatus.PENDING, c.pending, colors.info.strong),
        Triple(ReportStatus.VISITED, c.visited, colors.success.strong),
        Triple(ReportStatus.ARCHIVED, c.archived, colors.neutral.strong)
    )
    SectionCard(
        title = stringResource(R.string.dashboard_report_status),
        icon = Icons.Filled.DonutLarge,
        modifier = Modifier.padding(top = Spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.sm)) {
            DonutChart(slices = parts.map { Slice(it.second.toFloat(), it.third) }, size = 112.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(PersianNumbers.toPersian(c.total), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.dashboard_total_cases),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.lg)) {
                parts.forEach { (status, count, color) ->
                    LegendRow(
                        label = statusLabel(status),
                        value = PersianNumbers.toPersian(count.toInt()),
                        color = color
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md), color = MaterialTheme.colorScheme.outlineVariant)
        SubHeading(stringResource(R.string.dashboard_approval_row))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                ApprovalState.PENDING to c.awaitingApproval,
                ApprovalState.RETURNED to c.returned,
                ApprovalState.APPROVED to c.approved
            ).forEach { (approval, count) ->
                StatusBadge(
                    text = approvalLabel(approval) + " · " + PersianNumbers.toPersian(count.toInt()),
                    tone = if (count > 0) approval.tone() else Tone.NEUTRAL,
                    icon = approval.icon()
                )
            }
        }

        SubHeading(stringResource(R.string.dashboard_findings), Modifier.padding(top = Spacing.md))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusBadge(
                text = stringResource(R.string.dashboard_tampered) + " · " + PersianNumbers.toPersian(c.tampered.toInt()),
                tone = if (c.tampered > 0) Tone.DANGER else Tone.NEUTRAL,
                icon = Icons.Filled.GppMaybe
            )
            StatusBadge(
                text = stringResource(R.string.dashboard_bypass) + " · " + PersianNumbers.toPersian(c.bypass.toInt()),
                tone = if (c.bypass > 0) Tone.WARNING else Tone.NEUTRAL,
                icon = Icons.Filled.ElectricalServices
            )
        }
    }
}

/** Is the work reaching the office: the server queue, the automatic sender, the manager's queue. */
@Composable
fun OperationsCard(state: DashboardState, onSettings: () -> Unit, onApprovals: () -> Unit) {
    val settings = state.settings
    val noServer = settings.syncTarget.isBlank()
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), onClick = onSettings) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToneIcon(icon = Icons.Filled.SatelliteAlt, tone = Tone.ACCENT, size = 34.dp)
                Text(
                    stringResource(R.string.dashboard_operations),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = Spacing.md)
                )
            }
            Box(modifier = Modifier.size(Spacing.sm))
            when {
                noServer -> OpsLine(Icons.Filled.CloudOff, Tone.NEUTRAL, stringResource(R.string.ops_server_off))
                state.serverPending > 0 -> OpsLine(
                    Icons.Filled.CloudUpload,
                    Tone.WARNING,
                    stringResource(R.string.ops_server_pending, PersianNumbers.toPersian(state.serverPending))
                )
                else -> OpsLine(Icons.Filled.CloudDone, Tone.SUCCESS, stringResource(R.string.ops_server_synced))
            }
            if (!noServer) {
                OpsLine(
                    if (settings.autoSync) Icons.Filled.Sync else Icons.Filled.SyncDisabled,
                    if (settings.autoSync) Tone.SUCCESS else Tone.NEUTRAL,
                    stringResource(if (settings.autoSync) R.string.ops_auto_on else R.string.ops_auto_off)
                )
            }
            val awaiting = state.counts.awaitingApproval.toInt()
            if (UserRole.isManager && awaiting > 0) {
                AppCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), onClick = onApprovals) {
                    Box(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                        OpsLine(
                            Icons.Filled.HourglassTop,
                            Tone.WARNING,
                            stringResource(R.string.ops_awaiting, PersianNumbers.toPersian(awaiting))
                        )
                    }
                }
            }
        }
    }
}

/** Which channels the cases came in through, as bars in each type's own colour. */
@Composable
fun TypeBreakdownCard(state: DashboardState) {
    val max = (state.byType.values.maxOrNull() ?: 0).coerceAtLeast(1)
    SectionCard(
        title = stringResource(R.string.dashboard_by_type),
        icon = Icons.Filled.Insights,
        modifier = Modifier.padding(top = Spacing.sm)
    ) {
        ReportType.entries.forEach { type ->
            val count = state.byType[type.code] ?: 0
            BarRow(
                label = reportTypeLabel(type),
                value = PersianNumbers.toPersian(count),
                fraction = count.toFloat() / max,
                color = colorForReportType(type.code, Tavan.colors.dark)
            )
        }
    }
}

@Composable
private fun OpsLine(icon: ImageVector, tone: Tone, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Tavan.colors.of(tone).strong, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Spacing.sm))
    }
}

@Composable
private fun SubHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 6.dp)
    )
}
