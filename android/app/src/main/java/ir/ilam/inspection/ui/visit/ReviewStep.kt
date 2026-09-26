package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.Completion
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.data.model.TechnicalInput
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.common.countyWithArea
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/** The four things a visit cannot close without, and the step that collects each. */
private val REQUIRED = listOf(
    Triple(R.string.missing_gps, R.string.review_item_gps, VisitStep.LOCATION),
    Triple(R.string.missing_measurement, R.string.review_item_measurement, VisitStep.TECHNICAL),
    Triple(R.string.missing_photo, R.string.review_item_photo, VisitStep.MEDIA),
    Triple(R.string.missing_description, R.string.review_item_description, VisitStep.MEDIA)
)

/**
 * The last step: is the visit ready to close, and does the case say what the
 * expert thinks it says. Every missing item is named and one tap from where
 * it is filled in — the app never just refuses.
 */
@Composable
fun ReviewStep(detail: ReportDetail, onStep: (Int) -> Unit) {
    val missing = Completion.missing(detail)
    val ready = missing.isEmpty()
    SectionCard(
        title = stringResource(R.string.review_title),
        icon = Icons.Filled.FactCheck,
        tone = if (ready) Tone.SUCCESS else Tone.WARNING,
        trailing = {
            StatusBadge(
                text = PersianNumbers.toPersian(REQUIRED.size - missing.size) + " / " + PersianNumbers.toPersian(REQUIRED.size),
                tone = if (ready) Tone.SUCCESS else Tone.WARNING
            )
        }
    ) {
        Text(
            text = stringResource(if (ready) R.string.review_ready else R.string.review_not_ready),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.sm)
        )
        REQUIRED.forEach { (missingRes, label, step) ->
            val ok = missingRes !in missing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onStep(step.ordinal) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (ok) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                    contentDescription = null,
                    tint = if (ok) Tavan.colors.success.strong else Tavan.colors.danger.strong,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                    Text(stringResource(label), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(step.title),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    CaseSummary(detail)
}

@Composable
private fun CaseSummary(detail: ReportDetail) {
    val report = detail.report
    val power = TechnicalInput.from(report).power()
    SectionCard(title = stringResource(R.string.review_summary), icon = Icons.Filled.Summarize) {
        ValueRow(stringResource(R.string.field_county), countyWithArea(report.county, report.areaCode))
        ValueRow(stringResource(R.string.field_address), report.address)
        ValueRow(
            label = stringResource(R.string.location_coordinates),
            value = if (report.latitude != null && report.longitude != null) {
                formatCoordinates(report.latitude, report.longitude)
            } else null,
            ltr = true
        )
        ValueRow(
            label = stringResource(R.string.location_accuracy),
            value = report.gpsAccuracy?.let { stringResource(R.string.location_accuracy_value, formatMetres(it)) }
        )
        ValueRow(
            label = stringResource(R.string.review_power),
            value = power.totalKilowatt?.let { stringResource(R.string.unit_kilowatt, PersianNumbers.toPersian(it)) },
            emphasize = true
        )
        ValueRow(
            label = stringResource(R.string.review_devices),
            value = stringResource(R.string.review_count, PersianNumbers.toPersian(detail.deviceCount)) +
                if (detail.totalPower > 0) " · " + stringResource(R.string.unit_watt, PersianNumbers.grouped(detail.totalPower)) else ""
        )
        ValueRow(
            label = stringResource(R.string.review_media),
            value = stringResource(R.string.review_count, PersianNumbers.toPersian(detail.media.size))
        )
        ValueRow(
            label = stringResource(R.string.review_attendees),
            value = stringResource(R.string.review_count, PersianNumbers.toPersian(detail.attendees.size))
        )
        ValueRow(stringResource(R.string.review_owner), report.ownerName)
        Text(
            text = stringResource(R.string.review_after),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm)
        )
    }
}
