package ir.ilam.inspection.ui.location

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.ilam.inspection.core.R
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.LocationProvider
import ir.ilam.inspection.util.LocationWarmup
import kotlinx.coroutines.delay

/**
 * Keeps the position warm for as long as the calling screen is on, asking for
 * the permission once if it is missing, and returns the current reading.
 * A form calls this when it opens, so the receiver is locked on by the time
 * the user reaches the map.
 */
@Composable
fun rememberLiveLocation(askPermission: Boolean = true): LocationWarmup.Reading? {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) LocationWarmup.permissionGranted(context)
    }
    DisposableEffect(Unit) {
        LocationWarmup.acquire(context)
        onDispose { LocationWarmup.release() }
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && askPermission) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val reading by LocationWarmup.reading.collectAsState()
    return reading
}

/** Whether a fresh reading exists right now; re-checked every second, since freshness runs out by itself. */
@Composable
fun rememberFresh(reading: LocationWarmup.Reading?): Boolean {
    val fresh by produceState(reading?.isFresh() == true, reading) {
        while (true) {
            value = reading?.isFresh() == true
            delay(1_000)
        }
    }
    return fresh
}

/** Where location stands, in a word and a colour: searching, approximate, or live with its accuracy. */
enum class LiveState { OFF, SEARCHING, APPROXIMATE, LIVE }

@Composable
fun liveState(reading: LocationWarmup.Reading?, fresh: Boolean): LiveState {
    val context = LocalContext.current
    val enabled = LocationProvider(context).isLocationEnabled()
    return when {
        !enabled -> LiveState.OFF
        fresh -> LiveState.LIVE
        reading != null -> LiveState.APPROXIMATE
        else -> LiveState.SEARCHING
    }
}

/**
 * The live accuracy as a pill: the first thing the eye finds on the map, and
 * on a pole's page. Colour follows the accuracy, so "good enough" is seen,
 * not read.
 */
@Composable
fun LiveAccuracyPill(reading: LocationWarmup.Reading?, fresh: Boolean, modifier: Modifier = Modifier) {
    val state = liveState(reading, fresh)
    val tone = when (state) {
        LiveState.OFF -> Tone.DANGER
        LiveState.SEARCHING, LiveState.APPROXIMATE -> Tone.WARNING
        LiveState.LIVE -> accuracyQuality(reading?.fix?.accuracy).tone
    }
    val colors = Tavan.colors.of(tone)
    Surface(color = colors.container, shape = CircleShape, shadowElevation = 2.dp, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
                if (state == LiveState.SEARCHING || state == LiveState.APPROXIMATE) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.strong)
                }
                Icon(
                    if (state == LiveState.LIVE) Icons.Filled.GpsFixed else Icons.Filled.GpsNotFixed,
                    contentDescription = null,
                    tint = colors.strong,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = when (state) {
                    LiveState.OFF -> stringResource(R.string.fix_location_off_short)
                    LiveState.SEARCHING -> stringResource(R.string.map_locating)
                    LiveState.APPROXIMATE -> stringResource(R.string.live_approximate)
                    LiveState.LIVE -> stringResource(
                        R.string.live_accuracy, formatMetres(reading!!.fix.accuracy),
                        stringResource(accuracyQuality(reading.fix.accuracy).label)
                    )
                },
                style = MaterialTheme.typography.labelLarge,
                color = colors.onContainer
            )
        }
    }
}
