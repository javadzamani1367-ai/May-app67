package ir.ilam.inspection.ui.pending

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.ui.common.ColorTag
import ir.ilam.inspection.ui.common.reportTypeLabel
import ir.ilam.inspection.ui.theme.Amber100
import ir.ilam.inspection.ui.theme.Amber700
import ir.ilam.inspection.ui.theme.Red100
import ir.ilam.inspection.ui.theme.Red700
import ir.ilam.inspection.ui.theme.colorForReportType
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.TrackingCode

private const val WARN_DAYS = 7
private const val LATE_DAYS = 15

/**
 * One case as a card: the tracking code large, the report type as a fixed
 * colour, and how long it has been waiting — amber past a week, red past two.
 */
@Composable
fun CaseCard(
    report: ReportEntity,
    daysWaiting: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = report.displayCode?.let { TrackingCode.forDisplay(it) }
                        ?: stringResource(R.string.card_no_tracking),
                    style = MaterialTheme.typography.titleLarge
                )
                ColorTag(
                    text = reportTypeLabel(report.reportType),
                    color = colorForReportType(report.reportType)
                )
            }
            Text(
                text = listOfNotNull(report.county, report.address).joinToString(" - "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (report.status == ReportStatus.PENDING.code) {
                    ElapsedTag(daysWaiting)
                } else {
                    Text(
                        text = PersianDate.format(report.visitDate ?: report.reportDate),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = PersianDate.format(report.reportDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ElapsedTag(days: Int) {
    val (background, foreground) = when {
        days > LATE_DAYS -> Red100 to Red700
        days > WARN_DAYS -> Amber100 to Amber700
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    ElapsedChip(
        text = stringResource(R.string.card_days_elapsed, PersianNumbers.toPersian(days)),
        background = background,
        foreground = foreground
    )
}

@Composable
private fun ElapsedChip(text: String, background: Color, foreground: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = foreground)
    }
}
