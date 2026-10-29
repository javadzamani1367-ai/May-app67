package ir.minerfinder.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import ir.minerfinder.data.TrackPointEntity
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Heatmap of the logged points: each point a soft disc about [radiusM] wide,
 * blue (cold) to red (hot) by probability. Hot points are drawn last, so they
 * stay visible where the track crosses itself.
 */
class HeatmapOverlay(private val radiusM: Float = 20f) : Overlay() {
    var points: List<TrackPointEntity> = emptyList()
        set(value) {
            field = value.sortedBy { it.probability }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screen = Point()

    override fun draw(canvas: Canvas, map: MapView, shadow: Boolean) {
        if (shadow || points.isEmpty()) return
        val projection = map.projection
        val radius = maxOf(projection.metersToPixels(radiusM), 14f)
        for (p in points) {
            projection.toPixels(GeoPoint(p.lat, p.lon), screen)
            val color = heatColor(p.probability)
            paint.shader = RadialGradient(
                screen.x.toFloat(), screen.y.toFloat(), radius,
                intArrayOf(color or (0xC0 shl 24), color or (0x00 shl 24)), null, Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(screen.x.toFloat(), screen.y.toFloat(), radius, paint)
        }
    }

    companion object {
        /** RGB without alpha: blue → green → amber → red. */
        fun heatColor(probability: Float): Int {
            val t = (probability / 100f).coerceIn(0f, 1f)
            val stops = arrayOf(intArrayOf(0x2F, 0x6F, 0xDE), intArrayOf(0x9B, 0xC5, 0x3D), intArrayOf(0xF2, 0xA5, 0x16), intArrayOf(0xE5, 0x39, 0x2B))
            val pos = t * (stops.size - 1)
            val k = pos.toInt().coerceAtMost(stops.size - 2)
            val f = pos - k
            val c = IntArray(3) { (stops[k][it] + (stops[k + 1][it] - stops[k][it]) * f).toInt() }
            return (c[0] shl 16) or (c[1] shl 8) or c[2]
        }
    }
}
