package ir.minerfinder.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ir.minerfinder.R
import ir.minerfinder.core.util.Jalali
import ir.minerfinder.data.SessionSummary
import ir.minerfinder.graph
import ir.minerfinder.ui.common.Fa
import ir.minerfinder.ui.common.Field
import ir.minerfinder.ui.common.Header
import ir.minerfinder.ui.common.SecondaryButton
import ir.minerfinder.ui.theme.Palette
import kotlinx.coroutines.flow.emptyFlow
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val dao = graph.database.sessions()
    val summaries by dao.summaries().collectAsState(initial = emptyList())
    val recording by graph.recorder.current.collectAsState()
    val chosen by graph.mapSession.collectAsState()
    val sessionId = chosen ?: recording ?: summaries.firstOrNull()?.id
    val points by remember(sessionId) { sessionId?.let { dao.points(it) } ?: emptyFlow() }.collectAsState(initial = emptyList())
    val bearings by remember(sessionId) { sessionId?.let { dao.bearings(it) } ?: emptyFlow() }.collectAsState(initial = emptyList())
    val me by graph.locations.location.collectAsState()

    val estimateTitle = stringResource(R.string.map_estimate)
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            // Ilam, until there is something better to show.
            controller.setCenter(GeoPoint(33.6374, 46.4227))
        }
    }
    val overlays = remember { MapOverlays(map, estimateTitle) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        map.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }
    LaunchedEffect(points, bearings, me) { overlays.update(points, bearings, me) }
    LaunchedEffect(sessionId, points.isNotEmpty()) {
        points.lastOrNull()?.let { map.controller.setCenter(GeoPoint(it.lat, it.lon)) }
    }
    val estimate = remember(bearings) { overlays.estimate(bearings) }
    val summary = summaries.firstOrNull { it.id == sessionId }

    Column(Modifier.fillMaxSize()) {
        Header(stringResource(R.string.tab_map), summary?.let { sessionTitle(it) } ?: stringResource(R.string.map_no_session))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Field(stringResource(R.string.map_points), Fa.int(points.size) + " — " + stringResource(R.string.map_bearings) + " " + Fa.int(bearings.size))
            if (estimate != null) {
                Field(
                    stringResource(R.string.map_estimate),
                    Fa.num(estimate.point.lat, 5) + "، " + Fa.num(estimate.point.lon, 5) + " (±" + Fa.num(estimate.rmsMeters) + " " + stringResource(R.string.unit_m) + ")",
                    Palette.Hot,
                )
            } else if (bearings.size < 2) {
                Text(stringResource(R.string.map_need_bearings), color = Palette.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.map_center_me), enabled = me != null, onClick = {
                    me?.let { map.controller.animateTo(GeoPoint(it.latitude, it.longitude)) }
                })
                SecondaryButton(stringResource(R.string.map_center_estimate), enabled = estimate != null, onClick = {
                    estimate?.let { map.controller.animateTo(GeoPoint(it.point.lat, it.point.lon)) }
                })
                if (chosen != null) {
                    SecondaryButton(stringResource(R.string.map_current), onClick = { graph.mapSession.value = null })
                }
            }
        }
    }
}

@Composable
fun sessionTitle(s: SessionSummary): String =
    stringResource(R.string.session_title, Fa.digits(Jalali.format(s.startedAt)))
