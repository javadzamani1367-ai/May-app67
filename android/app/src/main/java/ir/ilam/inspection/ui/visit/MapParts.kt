package ir.ilam.inspection.ui.visit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import ir.ilam.inspection.util.MapConfig
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * A point on the map, with the circle its accuracy covers. Drawn by hand
 * rather than with osmdroid's marker: the stock marker is a large pin whose
 * tip is hard to place exactly, where a dot is where it is.
 */
class DotOverlay(context: Context, fill: Color) : Overlay() {

    var point: GeoPoint? = null
    var accuracyMeters: Float = 0f

    private val density = context.resources.displayMetrics.density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill.toArgb()
        style = Paint.Style.FILL
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill.copy(alpha = 0.16f).toArgb()
        style = Paint.Style.FILL
    }
    private val haloEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fill.copy(alpha = 0.55f).toArgb()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val at = point ?: return
        val pixel = mapView.projection.toPixels(at, null)
        val x = pixel.x.toFloat()
        val y = pixel.y.toFloat()
        if (accuracyMeters > 0f) {
            // Measured by projecting a second point, not with a metres-to-pixels
            // helper: this is exact at any latitude and any zoom, and uses only
            // the one projection call every osmdroid version has.
            val north = mapView.projection.toPixels(
                GeoPoint(at.latitude + accuracyMeters / METRES_PER_DEGREE, at.longitude),
                null
            )
            val radius = kotlin.math.abs(pixel.y - north.y).toFloat()
            if (radius > DOT_RADIUS * density) {
                canvas.drawCircle(x, y, radius, haloPaint)
                canvas.drawCircle(x, y, radius, haloEdge)
            }
        }
        canvas.drawCircle(x, y, DOT_RADIUS * density, fillPaint)
        canvas.drawCircle(x, y, DOT_RADIUS * density, ringPaint)
    }

    private companion object {
        const val DOT_RADIUS = 8f
        const val METRES_PER_DEGREE = 111_320.0
    }
}

/** An OpenStreetMap view set up the way every map in the app wants it. */
fun newMapView(context: Context): MapView {
    MapConfig.ensure(context)
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setUseDataConnection(true)
        isTilesScaledToDpi = true
        // The app draws its own large zoom buttons; the stock ones are small
        // and appear and disappear on their own.
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    }
}

/**
 * A small, still map of the recorded point inside the location card. It does
 * not scroll or zoom — inside a scrolling form that would fight the finger —
 * and a tap opens the full map instead.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun MiniMap(
    latitude: Double,
    longitude: Double,
    accuracy: Double?,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dot = remember { DotOverlay(context, color) }
    val map = remember {
        newMapView(context).apply {
            setMultiTouchControls(false)
            overlays.add(dot)
        }
    }
    DisposableEffect(map) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    AndroidView(
        factory = {
            map.apply {
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_UP) onClick()
                    true
                }
            }
        },
        update = { view ->
            val point = GeoPoint(latitude, longitude)
            dot.point = point
            dot.accuracyMeters = (accuracy ?: 0.0).toFloat()
            view.controller.setZoom(MINI_ZOOM)
            view.controller.setCenter(point)
            view.invalidate()
        },
        modifier = modifier.fillMaxSize()
    )
}

private const val MINI_ZOOM = 17.0
