package ir.ilam.inspection.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/** One case on the map: where it is, and the colour of its state. */
data class CasePoint(val id: String, val point: GeoPoint, val color: Int)

/**
 * Every located case as a dot, in one overlay rather than one marker each: a
 * province's worth of markers is hundreds of views, where this is one pass of
 * circles. A tap picks the nearest dot within a finger's width.
 */
class CasePointsOverlay(context: Context, private val onPick: (String?) -> Unit) : Overlay() {

    var points: List<CasePoint> = emptyList()
    var selected: String? = null

    private val density = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.WHITE
        strokeWidth = 2.5f * density
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        points.forEach { case ->
            val pixel = projection.toPixels(case.point, null)
            val isSelected = case.id == selected
            val radius = (if (isSelected) 11f else 8f) * density
            if (isSelected) {
                halo.color = case.color
                halo.alpha = 60
                canvas.drawCircle(pixel.x.toFloat(), pixel.y.toFloat(), radius * 2.2f, halo)
            }
            fill.color = case.color
            canvas.drawCircle(pixel.x.toFloat(), pixel.y.toFloat(), radius, fill)
            canvas.drawCircle(pixel.x.toFloat(), pixel.y.toFloat(), radius, ring)
        }
    }

    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        val reach = TOUCH_RADIUS * density
        val nearest = points
            .map { case ->
                val pixel = projection.toPixels(case.point, null)
                case to hypot((pixel.x - event.x).toDouble(), (pixel.y - event.y).toDouble())
            }
            .filter { it.second <= reach }
            .minByOrNull { it.second }
            ?.first
        selected = nearest?.id
        onPick(nearest?.id)
        mapView.invalidate()
        return nearest != null
    }

    private companion object {
        const val TOUCH_RADIUS = 28f
    }
}
