package ir.ilam.inspection.ui.location

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
import ir.ilam.inspection.core.R
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

/** How good an accuracy figure is, in words and a colour. */
data class AccuracyQuality(val label: Int, val tone: Tone)

fun accuracyQuality(metres: Double?): AccuracyQuality = when {
    metres == null -> AccuracyQuality(R.string.location_quality_unknown, Tone.NEUTRAL)
    metres <= 5.0 -> AccuracyQuality(R.string.location_quality_excellent, Tone.SUCCESS)
    metres <= 12.0 -> AccuracyQuality(R.string.location_quality_good, Tone.ACCENT)
    metres <= 25.0 -> AccuracyQuality(R.string.location_quality_fair, Tone.WARNING)
    else -> AccuracyQuality(R.string.location_quality_poor, Tone.DANGER)
}

/** Whole metres above ten, one decimal below — "±۴٫۲" but "±۱۸". */
fun formatMetres(metres: Double): String = PersianNumbers.toPersian(
    if (metres < 10) String.format(Locale.US, "%.1f", metres) else metres.toInt().toString()
)

fun formatCoordinates(latitude: Double, longitude: Double): String =
    PersianNumbers.toPersian(String.format(Locale.US, "%.6f , %.6f", latitude, longitude))

/**
 * What the receivers are saying while a capture runs: the accuracy so far,
 * large, so the expert can see it tighten; and the choice to stop waiting.
 */
@Composable
fun LiveFixPanel(session: FixSession) {
    val estimate = session.estimate
    val info = Tavan.colors.info
    Surface(
        color = info.container,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row {
                androidx.compose.material3.Icon(Icons.Filled.Sensors, contentDescription = null, tint = info.strong)
                Text(
                    stringResource(R.string.fix_refining),
                    style = MaterialTheme.typography.titleSmall,
                    color = info.onContainer,
                    modifier = Modifier.padding(start = Spacing.sm)
                )
            }
            Text(
                text = estimate?.let { stringResource(R.string.location_accuracy_value, formatMetres(it.accuracy)) }
                    ?: stringResource(R.string.fix_waiting_first),
                style = if (estimate != null) MaterialTheme.typography.displaySmall else MaterialTheme.typography.bodyLarge,
                color = estimate?.let { Tavan.colors.of(accuracyQuality(it.accuracy).tone).strong } ?: info.onContainer,
                modifier = Modifier.padding(top = Spacing.sm)
            )
            if (estimate != null) {
                Text(
                    formatCoordinates(estimate.latitude, estimate.longitude),
                    style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                    color = info.onContainer
                )
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                color = info.strong,
                trackColor = Tavan.colors.track
            )
            Text(
                stringResource(
                    R.string.fix_elapsed,
                    PersianNumbers.toPersian(session.seconds),
                    PersianNumbers.toPersian(session.samples)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = info.onContainer
            )
            Text(
                stringResource(R.string.fix_hint),
                style = MaterialTheme.typography.bodySmall,
                color = info.onContainer,
                modifier = Modifier.padding(top = Spacing.xs)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                PrimaryButton(
                    text = stringResource(R.string.fix_accept),
                    onClick = session::acceptNow,
                    enabled = estimate != null,
                    icon = Icons.Filled.Check,
                    tone = Tone.SUCCESS,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = stringResource(R.string.fix_stop),
                    onClick = session::stop,
                    modifier = Modifier.weight(0.7f)
                )
            }
        }
    }
}

/** Typed-in coordinates, validated before they can be saved. */
@Composable
fun ManualCoordinates(onSave: (Double, Double) -> Unit) {
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        NumberField(
            label = stringResource(R.string.manual_latitude),
            value = latitude,
            onValueChange = { latitude = it; invalid = false },
            decimal = true
        )
        NumberField(
            label = stringResource(R.string.manual_longitude),
            value = longitude,
            onValueChange = { longitude = it; invalid = false },
            decimal = true,
            imeAction = ImeAction.Done,
            error = if (invalid) stringResource(R.string.manual_coordinates_invalid) else null
        )
        PrimaryButton(
            text = stringResource(R.string.manual_coordinates_save),
            icon = Icons.Filled.GpsFixed,
            onClick = {
                val lat = latitude.toDoubleOrNull()
                val lon = longitude.toDoubleOrNull()
                if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    invalid = true
                } else {
                    onSave(lat, lon)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)
        )
    }
}
