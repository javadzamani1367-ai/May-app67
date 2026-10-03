package ir.ilam.inspection.ui.location

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.ilam.inspection.core.R
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.Fix

/**
 * A recorded position, in every TavanKav app the same way.
 *
 * Three ways in: measured on site (the normal path, refined for as long as it
 * takes to settle), picked on the map, or typed in. What the app knows about
 * how the point was taken goes in [facts], under the map.
 */
@Composable
fun PositionCard(
    latitude: Double?,
    longitude: Double?,
    accuracy: Double?,
    onMeasured: (Fix, Int) -> Unit,
    onPicked: (Double, Double) -> Unit,
    onTyped: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.location_title),
    facts: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val session = rememberFixSession(onMeasured)
    var showMap by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(false) }
    var permissionRefused by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionRefused = !granted
        if (granted) session.start()
    }
    fun capture() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) session.start() else permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val hasFix = latitude != null && longitude != null
    SectionCard(
        title = title,
        modifier = modifier,
        icon = Icons.Filled.MyLocation,
        tone = Tone.INFO,
        trailing = {
            if (hasFix) {
                val quality = accuracyQuality(accuracy)
                StatusBadge(text = stringResource(quality.label), tone = quality.tone)
            }
        }
    ) {
        Column {
            if (hasFix) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .padding(vertical = Spacing.sm)
                        .clip(MaterialTheme.shapes.medium)
                ) {
                    MiniMap(
                        latitude = latitude!!,
                        longitude = longitude!!,
                        accuracy = accuracy,
                        color = Tavan.colors.info.strong,
                        onClick = { showMap = true }
                    )
                }
                facts()
            } else if (!session.running) {
                EmptyState(
                    title = stringResource(R.string.location_not_recorded_title),
                    message = stringResource(R.string.location_not_recorded_hint),
                    icon = Icons.Filled.GpsFixed,
                    modifier = Modifier.height(230.dp)
                )
            }

            if (session.running) {
                LiveFixPanel(session)
            } else {
                FixOutcomeNote(session.outcome, permissionRefused)
                PrimaryButton(
                    text = stringResource(if (hasFix) R.string.action_recapture else R.string.action_capture_precise),
                    onClick = ::capture,
                    icon = Icons.Filled.GpsFixed,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SecondaryButton(
                    text = stringResource(R.string.action_pick_on_map),
                    onClick = { showMap = true },
                    icon = Icons.Filled.Map,
                    enabled = !session.running,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = stringResource(R.string.action_manual_coordinates),
                    onClick = { manualOpen = !manualOpen },
                    icon = Icons.Filled.EditLocationAlt,
                    enabled = !session.running,
                    modifier = Modifier.weight(1f)
                )
            }
            if (manualOpen) {
                ManualCoordinates { lat, lon ->
                    onTyped(lat, lon)
                    manualOpen = false
                }
            }
        }
    }

    if (showMap) {
        MapPickerDialog(
            initialLatitude = latitude,
            initialLongitude = longitude,
            onDismiss = { showMap = false },
            onConfirm = { lat, lon ->
                onPicked(lat, lon)
                showMap = false
            }
        )
    }
}

@Composable
private fun FixOutcomeNote(outcome: FixOutcome?, permissionRefused: Boolean) {
    val (text, tone) = when {
        permissionRefused -> stringResource(R.string.gps_permission_needed) to Tone.DANGER
        outcome is FixOutcome.Recorded -> stringResource(
            R.string.fix_done, formatMetres(outcome.fix.accuracy)
        ) to Tone.SUCCESS
        outcome is FixOutcome.Coarse -> stringResource(
            R.string.fix_timeout_coarse, formatMetres(outcome.fix.accuracy)
        ) to Tone.WARNING
        outcome == FixOutcome.NoFix -> stringResource(R.string.gps_failed) to Tone.DANGER
        outcome == FixOutcome.LocationOff -> stringResource(R.string.fix_location_off) to Tone.DANGER
        else -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Tavan.colors.of(tone).strong,
        modifier = Modifier.padding(top = Spacing.sm)
    )
}
