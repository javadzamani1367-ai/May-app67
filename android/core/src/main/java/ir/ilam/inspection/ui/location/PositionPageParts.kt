package ir.ilam.inspection.ui.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.core.R
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.LocationWarmup
import ir.ilam.inspection.util.PersianNumbers

/** Why a point was not recorded. */
enum class Refusal { NO_FIX, TOO_FAR }

/** Accuracy pill, satellites in use, and — loudly — a fake-location app if one was caught. */
@Composable
fun PositionStatus(
    reading: LocationWarmup.Reading?,
    fresh: Boolean,
    satellites: Int,
    mock: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveAccuracyPill(reading, fresh)
            Surface(color = MaterialTheme.colorScheme.surface, shape = CircleShape, shadowElevation = 2.dp) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SatelliteAlt, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = if (satellites >= 4) Tavan.colors.success.strong else Tavan.colors.warning.strong)
                    Text(" " + PersianNumbers.toPersian(satellites), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (mock) {
            Surface(color = Tavan.colors.danger.container, shape = MaterialTheme.shapes.medium, shadowElevation = 2.dp,
                modifier = Modifier.padding(horizontal = Spacing.lg)) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = Tavan.colors.danger.strong)
                    Text(stringResource(R.string.position_mock), style = MaterialTheme.typography.bodySmall,
                        color = Tavan.colors.danger.onContainer, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** Streets or satellite imagery; imagery needs the internet. */
@Composable
fun LayerSwitch(imagery: Boolean, onToggle: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onToggle,
        containerColor = if (imagery) Tavan.colors.info.strong else MaterialTheme.colorScheme.surface,
        contentColor = if (imagery) MaterialTheme.colorScheme.surface else Tavan.colors.info.strong
    ) {
        Icon(if (imagery) Icons.Filled.Layers else Icons.Filled.SatelliteAlt,
            contentDescription = stringResource(if (imagery) R.string.position_layer_streets else R.string.position_layer_imagery))
    }
}

/** Under the map: where the pin is, how far it is from the user, and what to do. */
@Composable
fun PositionNote(
    centre: Pair<Double, Double>?,
    precise: Fix?,
    following: Boolean,
    hasRecord: Boolean,
    imagery: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        if (centre != null) {
            Text(
                formatCoordinates(centre.first, centre.second),
                style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
                modifier = Modifier.fillMaxWidth()
            )
        }
        val distance = centre?.let { distanceFrom(precise, it.first, it.second) }
        Text(
            when {
                distance != null -> stringResource(R.string.position_distance, formatMetres(distance))
                else -> stringResource(R.string.position_no_precise)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (distance == null) Tavan.colors.warning.strong else MaterialTheme.colorScheme.onSurface
        )
        Text(
            stringResource(if (following) R.string.live_follow_hint else R.string.live_pick_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (imagery) {
            Text(stringResource(R.string.position_imagery_note), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hasRecord) {
            Text(stringResource(R.string.live_recorded_hint), style = MaterialTheme.typography.labelSmall,
                color = Tavan.colors.danger.strong)
        }
    }
}

@Composable
fun RefusalDialog(refusal: Refusal, limit: Double, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = Tavan.colors.danger.strong) },
        title = { Text(stringResource(if (refusal == Refusal.TOO_FAR) R.string.position_far_title else R.string.position_wait_title)) },
        text = {
            Text(
                if (refusal == Refusal.TOO_FAR) stringResource(R.string.position_far_message, formatMetres(limit))
                else stringResource(R.string.position_wait_message)
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.position_ok)) } }
    )
}
