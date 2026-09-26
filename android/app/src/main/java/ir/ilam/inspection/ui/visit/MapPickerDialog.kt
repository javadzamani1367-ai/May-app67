package ir.ilam.inspection.ui.visit

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.LocationProvider
import ir.ilam.inspection.util.LocationRefiner
import ir.ilam.inspection.util.MapConfig
import ir.ilam.inspection.util.PersianNumbers
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint

/**
 * Pick a coordinate by moving the map under a fixed pin.
 *
 * It opens where the case already is, or — for a case with no position yet —
 * where the expert is standing, so nobody has to find their village by
 * zooming in from the whole province. The blue dot is the phone's own
 * position, live; the round button brings the map back to it, the way every
 * map app on the phone already does.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapPickerDialog(
    initialLatitude: Double?,
    initialLongitude: Double?,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val provider = remember { LocationProvider(context) }
    val hasCase = initialLatitude != null && initialLongitude != null
    val quick = remember { if (hasCase) null else hasPermission(context).takeIf { it }?.let { provider.lastKnown() } }
    val start = remember {
        when {
            hasCase -> GeoPoint(initialLatitude!!, initialLongitude!!)
            quick != null -> GeoPoint(quick.latitude, quick.longitude)
            else -> GeoPoint(MapConfig.DEFAULT_LATITUDE, MapConfig.DEFAULT_LONGITUDE)
        }
    }

    var centre by remember { mutableStateOf(start.latitude to start.longitude) }
    var me by remember { mutableStateOf<Fix?>(null) }
    var granted by remember { mutableStateOf(hasPermission(context)) }
    /** Once the expert moves the map, a new fix must not yank it away again. */
    var touched by remember { mutableStateOf(false) }
    /** "My location" pressed before any fix came in: go there when one does. */
    var followPending by remember { mutableStateOf(!hasCase) }

    val dot = remember { DotOverlay(context, androidx.compose.ui.graphics.Color(0xFF2563EB)) }
    val map = remember {
        newMapView(context).apply {
            setMultiTouchControls(true)
            controller.setZoom(if (hasCase || quick != null) CLOSE_ZOOM else REGION_ZOOM)
            controller.setCenter(start)
            overlays.add(dot)
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
                if (event.action == MotionEvent.ACTION_DOWN) touched = true
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

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    // The blue dot: live for as long as the map is open, and no longer.
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val refiner = LocationRefiner()
        runCatching {
            provider.fixes().collect { fix ->
                refiner.add(fix)
                val best = refiner.estimate() ?: return@collect
                me = best
                dot.point = GeoPoint(best.latitude, best.longitude)
                dot.accuracyMeters = best.accuracy.toFloat()
                if (followPending && !touched) {
                    map.controller.animateTo(GeoPoint(best.latitude, best.longitude), CLOSE_ZOOM, ANIMATION_MS)
                    followPending = false
                }
                map.invalidate()
            }
        }
    }

    fun goToMe() {
        val fix = me
        if (fix == null) {
            followPending = true
            touched = false
            if (!granted) permission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        map.controller.animateTo(GeoPoint(fix.latitude, fix.longitude), maxOf(map.zoomLevelDouble, CLOSE_ZOOM), ANIMATION_MS)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TavanTopBar(
                    title = stringResource(R.string.map_picker_title),
                    subtitle = stringResource(R.string.map_picker_hint),
                    onBack = onDismiss
                )
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
                    // The pin's tip, not its middle, marks the centre of the map.
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = null,
                        tint = Tavan.colors.danger.strong,
                        modifier = Modifier.size(52.dp).align(Alignment.Center).offset(y = (-24).dp)
                    )
                    MapControls(
                        locating = granted && me == null,
                        onZoomIn = { map.controller.zoomIn() },
                        onZoomOut = { map.controller.zoomOut() },
                        onMyLocation = ::goToMe,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.lg)
                    )
                }
                BottomActionBar(
                    note = { PickerReadout(centre, me) }
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.8f)
                    )
                    PrimaryButton(
                        text = stringResource(R.string.map_picker_confirm),
                        onClick = { onConfirm(centre.first, centre.second) },
                        icon = Icons.Filled.Check,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MapControls(
    locating: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onMyLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallFloatingActionButton(onClick = onZoomIn, containerColor = MaterialTheme.colorScheme.surface) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.map_zoom_in))
        }
        SmallFloatingActionButton(onClick = onZoomOut, containerColor = MaterialTheme.colorScheme.surface) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.map_zoom_out))
        }
        FloatingActionButton(
            onClick = onMyLocation,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = Tavan.colors.info.strong
        ) {
            if (locating) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(34.dp), strokeWidth = 2.dp, color = Tavan.colors.info.strong)
                    Icon(Icons.Filled.GpsNotFixed, contentDescription = stringResource(R.string.map_my_location))
                }
            } else {
                Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_my_location))
            }
        }
    }
}

@Composable
private fun PickerReadout(centre: Pair<Double, Double>, me: Fix?) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        Text(
            text = formatCoordinates(centre.first, centre.second),
            style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Ltr),
            modifier = Modifier.fillMaxWidth()
        )
        Row {
            Text(
                text = if (me == null) {
                    stringResource(R.string.map_locating)
                } else {
                    stringResource(
                        R.string.map_distance_to_you,
                        PersianNumbers.toPersian(
                            LocationRefiner.distanceMeters(me, Fix(centre.first, centre.second, 1.0)).toInt()
                        )
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(R.string.map_offline_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

private fun hasPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private const val CLOSE_ZOOM = 18.0
private const val REGION_ZOOM = 11.0
private const val ANIMATION_MS = 700L
