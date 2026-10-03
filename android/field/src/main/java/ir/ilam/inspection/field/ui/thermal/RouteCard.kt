package ir.ilam.inspection.field.ui.thermal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.thermal.TrackState
import ir.ilam.inspection.ui.common.InfoTile
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The route: start it before taking the first thermal picture, stop it after
 * the last. While it runs the phone records its position about once a second,
 * screen off or HIKMICRO in front; the notification shows it is still going.
 */
@Composable
fun RouteCard(
    recording: Boolean,
    track: TrackState,
    startedAt: Long?,
    endedAt: Long?,
    points: Int,
    skewSeconds: Long?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    var refused by remember { mutableStateOf(false) }
    val needed = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refused = it[Manifest.permission.ACCESS_FINE_LOCATION] != true
        if (!refused) onStart()
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(recording) {
        while (recording) { now = System.currentTimeMillis(); delay(1000) }
    }

    SectionCard(
        title = stringResource(R.string.thermal_route_title),
        subtitle = stringResource(R.string.thermal_route_hint),
        icon = Icons.Filled.GpsFixed,
        tone = Tone.INFO,
        trailing = {
            StatusBadge(
                text = stringResource(when {
                    recording -> R.string.thermal_route_recording
                    endedAt != null -> R.string.thermal_route_done
                    else -> R.string.thermal_route_idle
                }),
                tone = if (recording) Tone.SUCCESS else Tone.NEUTRAL,
                solid = recording
            )
        }
    ) {
        Column {
            if (startedAt != null) {
                val shownPoints = if (recording) track.points else points
                val elapsed = ((if (recording) now else endedAt ?: now) - startedAt) / 1000
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(vertical = Spacing.sm)) {
                    InfoTile(stringResource(R.string.thermal_points), PersianNumbers.toPersian(shownPoints), modifier = Modifier.weight(1f), tone = Tone.INFO)
                    InfoTile(stringResource(R.string.thermal_elapsed), duration(elapsed), modifier = Modifier.weight(1f))
                    InfoTile(stringResource(R.string.thermal_accuracy),
                        track.accuracy?.takeIf { recording }?.let { "±" + PersianNumbers.toPersian(it.toInt()) } ?: "—",
                        modifier = Modifier.weight(1f))
                }
            }
            if (recording && track.gpsOff) Warning(stringResource(R.string.thermal_gps_off))
            val skew = if (recording) track.clockSkewSeconds else skewSeconds
            if (skew != null && abs(skew) > SKEW_WARN_SECONDS) {
                Warning(stringResource(R.string.thermal_clock_skew, PersianNumbers.toPersian(abs(skew))))
            }
            if (refused) Warning(stringResource(R.string.thermal_location_refused))
            PrimaryButton(
                text = stringResource(when {
                    recording -> R.string.thermal_route_stop
                    startedAt != null -> R.string.thermal_route_resume
                    else -> R.string.thermal_route_start
                }),
                onClick = {
                    if (recording) onStop() else {
                        val granted = needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                        if (granted) onStart() else launcher.launch(needed)
                    }
                },
                icon = if (recording) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                tone = if (recording) Tone.DANGER else Tone.BRAND,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)
            )
        }
    }
}

@Composable
fun Warning(text: String) {
    Text(text, color = Tavan.colors.warning.strong, style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = Spacing.sm))
}

private fun duration(seconds: Long): String =
    PersianNumbers.toPersian("%d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60))

/** More than this between the phone's clock and GPS time, and the file times are worth a second look. */
private const val SKEW_WARN_SECONDS = 30
