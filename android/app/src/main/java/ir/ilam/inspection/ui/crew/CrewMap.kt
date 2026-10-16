package ir.ilam.inspection.ui.crew

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ir.ilam.inspection.ui.location.newMapView
import ir.ilam.inspection.ui.map.CasePoint
import ir.ilam.inspection.ui.map.CasePointsOverlay
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

/** The colour of each kind of work on the map — the same tones the web panel's map uses. */
fun crewTone(type: String): Tone = when (type) {
    "panel" -> Tone.INFO
    "pole" -> Tone.ACCENT
    "thermal" -> Tone.WARNING
    "crypto" -> Tone.DANGER
    else -> Tone.BRAND
}

/** A dot to draw: where, and in which tone. */
data class CrewDot(val id: String, val latitude: Double, val longitude: Double, val tone: Tone)

/**
 * A small map of where the crew worked, framed on its dots. One overlay of
 * circles rather than a marker each (as the cases map), so a month of poles
 * stays light; with the offline map installed it needs no network.
 */
@Composable
fun CrewDotsMap(dots: List<CrewDot>, modifier: Modifier = Modifier) {
    if (dots.isEmpty()) return
    val context = LocalContext.current
    val overlay = remember { CasePointsOverlay(context) { } }
    val map = remember {
        newMapView(context).apply {
            setMultiTouchControls(true)
            overlays.add(overlay)
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    val colors = Tone.entries.associateWith { Tavan.colors.of(it).strong.toArgb() }
    LaunchedEffect(dots) {
        overlay.points = dots.map { CasePoint(it.id, GeoPoint(it.latitude, it.longitude), colors.getValue(it.tone)) }
        val frame = {
            if (dots.size == 1) {
                map.controller.setZoom(16.0)
                map.controller.setCenter(GeoPoint(dots[0].latitude, dots[0].longitude))
            } else {
                map.zoomToBoundingBox(BoundingBox.fromGeoPoints(dots.map { GeoPoint(it.latitude, it.longitude) }).increaseByScale(1.3f), false)
            }
            map.invalidate()
        }
        if (map.width > 0) frame() else map.addOnFirstLayoutListener { _, _, _, _, _ -> frame() }
    }
    AndroidView(factory = { map }, modifier = modifier.fillMaxWidth().height(280.dp).clip(MaterialTheme.shapes.medium))
}
