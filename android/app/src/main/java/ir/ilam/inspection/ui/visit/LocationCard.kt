package ir.ilam.inspection.ui.visit

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.LocationSource
import ir.ilam.inspection.ui.common.EmptyState
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone

/**
 * The position of the case — one of the things a report is judged on.
 *
 * Three ways in: measured on site (the normal path, refined for as long as it
 * takes to settle), picked on the map, or typed in. The card always says which
 * it was, when, and how sure the phone was, because a point typed in at the
 * office is not a point measured at the meter.
 */
@Composable
fun LocationCard(report: ReportEntity, viewModel: VisitViewModel) {
    val context = LocalContext.current
    val meta by viewModel.locationFix.collectAsStateWithLifecycle()
    val session = rememberFixSession { fix, samples -> viewModel.applyFix(fix, samples) }
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

    val lat = report.latitude
    val lon = report.longitude
    val hasFix = lat != null && lon != null
    SectionCard(
        title = stringResource(R.string.location_title),
        icon = Icons.Filled.MyLocation,
        tone = Tone.INFO,
        trailing = {
            if (hasFix) {
                val quality = accuracyQuality(report.gpsAccuracy)
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
                        latitude = lat!!,
                        longitude = lon!!,
                        accuracy = report.gpsAccuracy,
                        color = Tavan.colors.info.strong,
                        onClick = { showMap = true }
                    )
                }
                LocationFacts(report = report, meta = meta)
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
                ManualCoordinates { latitude, longitude ->
                    viewModel.setCoordinates(latitude, longitude, LocationSource.MANUAL)
                    manualOpen = false
                }
            }
        }
    }

    if (showMap) {
        MapPickerDialog(
            initialLatitude = lat,
            initialLongitude = lon,
            onDismiss = { showMap = false },
            onConfirm = { latitude, longitude ->
                viewModel.setCoordinates(latitude, longitude, LocationSource.MAP)
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
