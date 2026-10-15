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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import ir.ilam.inspection.util.LocationRefiner
import ir.ilam.inspection.util.LocationWarmup
import ir.ilam.inspection.util.MapConfig
import ir.ilam.inspection.util.MockLocation
import ir.ilam.inspection.util.PlacedPoint
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint

/**
 * Recording a position, the one way every app does it: on the map.
 *
 * The pin sits in the middle of the map. The map opens on the user's own
 * satellite position and follows it, so standing at the spot, one tap on
 * «ثبت این نقطه» records it; if the point is elsewhere or the receiver is off,
 * the user drags the map until the pin is on it. The moment, the user's own
 * position and the distance between them are recorded with the point.
 *
 * [presenceLimit] (metres) is for points the user must be standing at — a
 * panel, a pole: with a precise fix farther than that from the pin, recording
 * is refused; without a precise fix, it waits for one.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun LivePositionPage(
    title: String,
    recordedLatitude: Double?,
    recordedLongitude: Double?,
    onRecord: (PlacedPoint) -> Unit,
    onBack: () -> Unit,
    subtitle: String? = null,
    presenceLimit: Double? = null
) {
    val context = LocalContext.current
    val reading = rememberLiveLocation()
    val fresh = rememberFresh(reading)
    val satellites by LocationWarmup.satellites.collectAsState()
    val mock by MockLocation.detected.collectAsState()
    val hasRecord = recordedLatitude != null && recordedLongitude != null
    val precise = reading?.takeIf { fresh && it.precise }?.fix

    /** True while the pin rides on the user's position; a drag sets it down where the finger leaves it. */
    var following by remember { mutableStateOf(!hasRecord) }
    var centre by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var imagery by rememberSaveable { mutableStateOf(false) }
    var refusal by remember { mutableStateOf<Refusal?>(null) }
    val me = remember { DotOverlay(context, Color(0xFF2563EB)) }
    val recorded = remember { DotOverlay(context, Color(0xFFDC2626)) }
    val map = remember {
        val start = when {
            hasRecord -> GeoPoint(recordedLatitude!!, recordedLongitude!!)
            reading != null -> GeoPoint(reading.fix.latitude, reading.fix.longitude)
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
            // Only a real drag sets the pin down. A gloved tap wobbles a few
            // pixels; counting that as a drag stopped the pin following the
            // user, and it then sat on the first rough fix as if the map were slow.
            val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop * 2f
            var downX = 0f
            var downY = 0f
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y }
                    MotionEvent.ACTION_MOVE -> if (kotlin.math.hypot(event.x - downX, event.y - downY) > slop) following = false
                    MotionEvent.ACTION_POINTER_DOWN -> following = false
                }
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
    LaunchedEffect(imagery) {
        if (imagery) MapLayers.satellite(context, map) else if (map.tileProvider.tileSource.name() == IMAGERY) MapLayers.streets(context, map)
    }
    recorded.point = if (hasRecord) GeoPoint(recordedLatitude!!, recordedLongitude!!) else null

    // The blue dot moves with every reading; while following, the pin does too.
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
            centre = current.fix.latitude to current.fix.longitude
        }
        map.invalidate()
    }

    fun followMe() {
        following = true
        reading?.let { map.controller.animateTo(GeoPoint(it.fix.latitude, it.fix.longitude), maxOf(map.zoomLevelDouble, CLOSE_ZOOM), 600L) }
    }

    fun record() {
        val pin = centre ?: (map.mapCenter.latitude to map.mapCenter.longitude)
        val point = PlacedPoint(
            latitude = pin.first,
            longitude = pin.second,
            accuracy = null,
            at = System.currentTimeMillis(),
            observer = precise,
            satellites = satellites
        )
        val distance = point.distance
        if (presenceLimit != null) {
            if (distance == null) {
                refusal = Refusal.NO_FIX
                return
            }
            if (distance > presenceLimit) {
                refusal = Refusal.TOO_FAR
                return
            }
        }
        // Standing on the point: the receiver's accuracy is the point's accuracy.
        val onMe = precise != null && distance != null && distance <= maxOf(precise.accuracy, ON_ME_METERS)
        onRecord(point.copy(accuracy = if (onMe) precise!!.accuracy else null))
        onBack()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(title = title, subtitle = subtitle, onBack = onBack)
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = null,
                tint = Tavan.colors.danger.strong,
                modifier = Modifier.size(52.dp).align(Alignment.Center).offset(y = (-24).dp)
            )
            PositionStatus(reading, fresh, satellites, mock, modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.md))
            Column(modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg)) {
                LayerSwitch(imagery = imagery, onToggle = { imagery = !imagery })
                MapControls(
                    locating = !fresh,
                    onZoomIn = { map.controller.zoomIn() },
                    onZoomOut = { map.controller.zoomOut() },
                    onMyLocation = ::followMe,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }
        }
        BottomActionBar(note = { PositionNote(centre, precise, following, hasRecord, imagery) }) {
            SecondaryButton(
                text = stringResource(R.string.map_my_location),
                onClick = ::followMe,
                icon = Icons.Filled.MyLocation,
                modifier = Modifier.weight(0.8f)
            )
            PrimaryButton(
                text = stringResource(R.string.live_record_pin),
                onClick = ::record,
                enabled = centre != null || reading != null || hasRecord,
                icon = Icons.Filled.Check,
                tone = Tone.SUCCESS,
                modifier = Modifier.weight(1.2f)
            )
        }
    }
    refusal?.let { RefusalDialog(it, presenceLimit ?: 0.0, onDismiss = { refusal = null }) }
}

/** Distance from the user to a point, or null without a precise position. */
fun distanceFrom(me: Fix?, latitude: Double, longitude: Double): Double? =
    me?.let { LocationRefiner.distanceMeters(it, Fix(latitude, longitude, 0.0)) }

private const val CLOSE_ZOOM = 18.0
private const val REGION_ZOOM = 11.0
/** Within this of the user, a pin counts as "where I stand". */
private const val ON_ME_METERS = 5.0
internal const val IMAGERY = "esri-world-imagery"
