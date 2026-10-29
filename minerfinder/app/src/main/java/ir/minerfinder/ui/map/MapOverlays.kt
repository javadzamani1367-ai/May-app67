package ir.minerfinder.ui.map

import android.graphics.Color
import android.location.Location
import ir.minerfinder.core.geo.BearingLine
import ir.minerfinder.core.geo.Intersection
import ir.minerfinder.core.geo.LatLon
import ir.minerfinder.core.geo.Triangulation
import ir.minerfinder.data.BearingEntity
import ir.minerfinder.data.TrackPointEntity
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Polygon

/** Rebuilds the map's overlays from one session's data. */
class MapOverlays(private val map: MapView, private val estimateTitle: String) {
    private val heatmap = HeatmapOverlay()
    private var dynamic = listOf<org.osmdroid.views.overlay.Overlay>()

    init {
        map.overlays.add(heatmap)
    }

    fun estimate(bearings: List<BearingEntity>): Intersection? = Triangulation.intersect(
        bearings.map { BearingLine(LatLon(it.lat, it.lon), it.bearingDeg, 0.3 + it.sharpness) },
    )

    fun update(points: List<TrackPointEntity>, bearings: List<BearingEntity>, me: Location?) {
        map.overlays.removeAll(dynamic)
        heatmap.points = points
        val added = ArrayList<org.osmdroid.views.overlay.Overlay>()

        if (points.size >= 2) {
            added += Polyline(map).apply {
                setPoints(points.map { GeoPoint(it.lat, it.lon) })
                outlinePaint.color = Color.argb(140, 14, 42, 71)
                outlinePaint.strokeWidth = 4f
            }
        }
        for (b in bearings) {
            val origin = LatLon(b.lat, b.lon)
            val end = Triangulation.destination(origin, b.bearingDeg, LINE_LENGTH_M)
            added += Polyline(map).apply {
                setPoints(listOf(GeoPoint(b.lat, b.lon), GeoPoint(end.lat, end.lon)))
                outlinePaint.color = Color.rgb(229, 57, 43)
                outlinePaint.strokeWidth = 6f
            }
        }
        estimate(bearings)?.let { hit ->
            val center = GeoPoint(hit.point.lat, hit.point.lon)
            added += Polygon(map).apply {
                setPoints(Polygon.pointsAsCircle(center, maxOf(hit.rmsMeters, 10.0)))
                fillPaint.color = Color.argb(60, 229, 57, 43)
                outlinePaint.color = Color.rgb(229, 57, 43)
                outlinePaint.strokeWidth = 3f
            }
            added += Marker(map).apply {
                position = center
                title = estimateTitle
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
        }
        me?.let { fix ->
            added += Polygon(map).apply {
                setPoints(Polygon.pointsAsCircle(GeoPoint(fix.latitude, fix.longitude), maxOf(fix.accuracy.toDouble(), 4.0)))
                fillPaint.color = Color.argb(70, 47, 111, 222)
                outlinePaint.color = Color.rgb(47, 111, 222)
                outlinePaint.strokeWidth = 3f
            }
        }
        map.overlays.addAll(added)
        dynamic = added
        map.invalidate()
    }

    companion object {
        const val LINE_LENGTH_M = 3000.0
    }
}
