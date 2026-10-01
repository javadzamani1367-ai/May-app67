package ir.ilam.inspection.ui.visit

import ir.ilam.inspection.ui.location.formatCoordinates
import ir.ilam.inspection.ui.location.formatMetres
import ir.ilam.inspection.ui.location.accuracyQuality
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.LocationFixEntity
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.LocationSource
import ir.ilam.inspection.ui.common.InfoTile
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import java.util.Locale

/** The recorded point as facts: where, how sure, when, and from what. */
@Composable
fun LocationFacts(report: ReportEntity, meta: LocationFixEntity?) {
    val lat = report.latitude ?: return
    val lon = report.longitude ?: return
    ValueRow(
        label = stringResource(R.string.location_coordinates),
        value = formatCoordinates(lat, lon),
        ltr = true,
        emphasize = true
    )
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(top = Spacing.xs)) {
        val quality = accuracyQuality(report.gpsAccuracy)
        InfoTile(
            label = stringResource(R.string.location_accuracy),
            value = report.gpsAccuracy?.let { stringResource(R.string.location_accuracy_value, formatMetres(it)) }
                ?: stringResource(R.string.value_empty),
            icon = Icons.Filled.Straighten,
            tone = quality.tone,
            modifier = Modifier.weight(1f)
        )
        InfoTile(
            label = stringResource(R.string.location_captured_at),
            value = meta?.let { PersianDate.formatWithTime(it.capturedAt) } ?: stringResource(R.string.value_empty),
            icon = Icons.Filled.Schedule,
            modifier = Modifier.weight(1.4f)
        )
    }
    if (meta != null) {
        val source = LocationSource.of(meta.source)
        val sourceText = stringResource(
            when (source) {
                LocationSource.SENSOR -> R.string.location_source_sensor
                LocationSource.MAP -> R.string.location_source_map
                LocationSource.MANUAL -> R.string.location_source_manual
            }
        )
        ValueRow(
            label = stringResource(R.string.location_source),
            value = if (source == LocationSource.SENSOR && meta.samples > 1) {
                sourceText + " · " + stringResource(R.string.location_samples, PersianNumbers.toPersian(meta.samples))
            } else sourceText
        )
    }
}
