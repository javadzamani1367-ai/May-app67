package ir.ilam.inspection.ui.location

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ir.ilam.inspection.core.R
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.MapConfig
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint

/**
 * The location of an item, as one page: a map that opens on the user, at once.
 *
 * The map follows the blue dot — the phone's live position, warmed up since
 * the form opened — and "record my position" takes it with its accuracy. To
 * mark a point somewhere else (the house across the field), the user drags
 * the map: a pin appears at the centre and the button records the pin. "My
 * position" brings the map back. There is no typed-in coordinate: a mistyped
 * digit puts a report in the next village.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun LivePositionPage(
    title: String,
    recordedLatitude: Double?,
    recordedLongitude: Double?,
    onRecordFix: (Fix, Int) -> Unit,
    onRecordPoint: (Double, Double) -> Unit,
    onBack: () -> Unit,
    subtitle: String? = null
) {
    val context = LocalContext.current
    val reading = rememberLiveLocation()
    val fresh = rememberFresh(reading)
    val hasRecord = recordedLatitude != null && recordedLongitude != null

    /** True while the map follows the user; a drag turns it into picking. */
    var following by remember { mutableStateOf(true) }
    var centre by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val me = remember { DotOverlay(context, Color(0xFF2563EB)) }
    val recorded = remember { DotOverlay(context, Color(0xFFDC2626)) }
    val map = remember {
        val start = when {
            reading != null -> GeoPoint(reading.fix.latitude, reading.fix.longitude)
            hasRecord -> GeoPoint(recordedLatitude!!, recordedLongitude!!)
            else -> GeoPoint(MapConfig.DEFAULT_LATITUDE, MapConfig.DEFAULT_LONGITUDE)
        }
        newMapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(if (reading != null || hasRecord) CLOSE_ZOOM else REGION_ZOOM)
            controller.setCenter(start)
            overlays.add(recorded)
            overlays.add(me)
            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    centre = mapCenter.latitude to mapCenter.longitude
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    centre = mapCenter.latitude to mapCenter.longitude
                    return false
                }
            })
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_MOVE) following = false
                false
            }
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    recorded.point = if (hasRecord) GeoPoint(recordedLatitude!!, recordedLongitude!!) else null

    // The blue dot moves with every reading; while following, the map does too.
    var firstCentre by remember { mutableStateOf(reading == null) }
    LaunchedEffect(reading) {
        val current = reading ?: return@LaunchedEffect
        val point = GeoPoint(current.fix.latitude, current.fix.longitude)
        me.point = point
        me.accuracyMeters = current.fix.accuracy.toFloat()
        if (following) {
            if (firstCentre) {
                map.controller.setZoom(CLOSE_ZOOM)
                firstCentre = false
            }
            map.controller.animateTo(point)
        }
        map.invalidate()
    }

    fun followMe() {
        following = true
        reading?.let { map.controller.animateTo(GeoPoint(it.fix.latitude, it.fix.longitude), maxOf(map.zoomLevelDouble, CLOSE_ZOOM), 600L) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = title, subtitle = subtitle, onBack = onBack)
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
            if (!following) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    tint = Tavan.colors.danger.strong,
                    modifier = Modifier.size(52.dp).align(Alignment.Center).offset(y = (-24).dp)
                )
            }
            LiveAccuracyPill(reading, fresh, modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.md))
            MapControls(
                locating = !fresh,
                onZoomIn = { map.controller.zoomIn() },
                onZoomOut = { map.controller.zoomOut() },
                onMyLocation = ::followMe,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg)
            )
        }
        BottomActionBar(note = { PageNote(following, hasRecord, centre, reading?.fix?.takeIf { fresh }) }) {
            if (following) {
                PrimaryButton(
                    text = if (fresh) stringResource(R.string.live_record_me_accuracy, formatMetres(reading!!.fix.accuracy))
                    else stringResource(R.string.live_record_me),
                    onClick = { reading?.let { onRecordFix(it.fix, it.samples); onBack() } },
                    enabled = fresh,
                    icon = Icons.Filled.Check,
                    tone = Tone.SUCCESS,
                    modifier = Modifier.weight(1f)
                )
            } else {
                SecondaryButton(
                    text = stringResource(R.string.map_my_location),
                    onClick = ::followMe,
                    icon = Icons.Filled.MyLocation,
                    modifier = Modifier.weight(0.8f)
                )
                PrimaryButton(
                    text = stringResource(R.string.live_record_pin),
                    onClick = { centre?.let { onRecordPoint(it.first, it.second); onBack() } },
                    enabled = centre != null,
                    icon = Icons.Filled.Check,
                    modifier = Modifier.weight(1.2f)
                )
            }
        }
    }
}

@Composable
private fun PageNote(following: Boolean, hasRecord: Boolean, centre: Pair<Double, Double>?, me: Fix?) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        val shown = if (following) me?.let { it.latitude to it.longitude } else centre
        if (shown != null) {
            Text(
                formatCoordinates(shown.first, shown.second),
                style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            stringResource(if (following) R.string.live_follow_hint else R.string.live_pick_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hasRecord) {
            Text(
                stringResource(R.string.live_recorded_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Tavan.colors.danger.strong
            )
        }
    }
}

private const val CLOSE_ZOOM = 18.0
private const val REGION_ZOOM = 11.0
