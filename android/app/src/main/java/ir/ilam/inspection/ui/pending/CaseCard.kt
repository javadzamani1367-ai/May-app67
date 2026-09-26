package ir.ilam.inspection.ui.pending

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.ApprovalState
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.data.model.Urgency
import ir.ilam.inspection.ui.common.ColorTag
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.approvalLabel
import ir.ilam.inspection.ui.common.countyWithArea
import ir.ilam.inspection.ui.common.icon
import ir.ilam.inspection.ui.common.reportTypeLabel
import ir.ilam.inspection.ui.common.statusLabel
import ir.ilam.inspection.ui.common.tone
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.ui.theme.colorForReportType
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.TrackingCode

/**
 * One case as a card. Read top to bottom it answers, in order: which case
 * (the code, largest), what kind (the type's colour, also on the edge), what
 * state and how urgent (badges), where (the place), and when.
 *
 * Long press asks to delete; the dialog spells out what goes with it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun CaseCard(
    report: ReportEntity,
    daysWaiting: Int,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
    devices: Int = 0,
    horizontalPadding: Dp = Spacing.screen
) {
    val typeColor = colorForReportType(report.reportType, Tavan.colors.dark)
    val status = ReportStatus.of(report.status)
    val approval = ApprovalState.of(report.approvalState)
    val shape = MaterialTheme.shapes.large
    val surface = if (Tavan.colors.dark) MaterialTheme.colorScheme.surfaceContainer
    else MaterialTheme.colorScheme.surfaceContainerLowest

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 5.dp)
            .clip(shape)
            .background(surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .combinedClickable(onClick = onClick, onLongClick = onDeleteRequest)
            .height(IntrinsicSize.Min)
    ) {
        // The type's colour down the leading edge: the list can be scanned by
        // colour before a single word is read.
        Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(typeColor))
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md, vertical = Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = report.displayCode?.let { TrackingCode.forDisplay(it) }
                        ?: stringResource(R.string.card_no_tracking),
                    style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Ltr),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (status == ReportStatus.PENDING) {
                    val urgency = Urgency.of(daysWaiting)
                    StatusBadge(
                        text = stringResource(R.string.case_waiting_days, PersianNumbers.toPersian(daysWaiting)),
                        tone = if (urgency == Urgency.NORMAL) Tone.INFO else urgency.tone(),
                        icon = Icons.Filled.Schedule,
                        solid = urgency == Urgency.LATE
                    )
                } else {
                    StatusBadge(text = statusLabel(status), tone = status.tone(), icon = status.icon())
                }
            }

            FlowRow(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ColorTag(text = reportTypeLabel(report.reportType), color = typeColor)
                if (devices > 0) {
                    StatusBadge(
                        text = stringResource(R.string.case_devices_found, PersianNumbers.toPersian(devices)),
                        tone = Tone.DANGER,
                        icon = Icons.Filled.Memory
                    )
                }
                if (approval != ApprovalState.DRAFT) {
                    StatusBadge(text = approvalLabel(approval), tone = approval.tone(), icon = approval.icon())
                }
            }

            MetaLine(
                icon = Icons.Filled.Place,
                text = listOfNotNull(
                    countyWithArea(report.county, report.areaCode),
                    report.address?.takeIf { it.isNotBlank() }
                ).joinToString(" · ").ifBlank { stringResource(R.string.case_no_address) }
            )
            MetaLine(
                icon = Icons.Filled.CalendarMonth,
                text = listOfNotNull(
                    PersianDate.format(report.reportDate),
                    report.visitDate?.let { PersianDate.format(it) }
                ).distinct().joinToString("  ←  ")
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.align(Alignment.CenterVertically).padding(end = Spacing.sm)
        )
    }
}

@Composable
private fun MetaLine(icon: ImageVector, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
