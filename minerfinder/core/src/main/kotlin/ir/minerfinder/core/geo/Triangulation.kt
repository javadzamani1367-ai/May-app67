package ir.minerfinder.core.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLon(val lat: Double, val lon: Double)

/** A direction taken at a place: [bearingDeg] clockwise from true north. */
data class BearingLine(val origin: LatLon, val bearingDeg: Double, val weight: Double = 1.0)

/** @param rmsMeters root-mean-square distance of the point from the lines: small means they agree. */
data class Intersection(val point: LatLon, val rmsMeters: Double, val linesUsed: Int)

/**
 * Where several bearing lines meet: the least-squares point closest to all of
 * them, in a flat east/north frame around the lines (fine at search scale,
 * a few kilometres).
 */
object Triangulation {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun intersect(lines: List<BearingLine>): Intersection? {
        if (lines.size < 2) return null
        val lat0 = lines.map { it.origin.lat }.average()
        val lon0 = lines.map { it.origin.lon }.average()
        val k = cos(lat0 * PI / 180.0)

        var a11 = 0.0; var a12 = 0.0; var a22 = 0.0
        var b1 = 0.0; var b2 = 0.0
        val local = lines.map { line ->
            val (px, py) = toLocal(line.origin, lat0, lon0, k)
            val t = line.bearingDeg * PI / 180.0
            val dx = sin(t); val dy = cos(t)
            // Projector onto the line's normal: I − d·dᵀ.
            val m11 = 1 - dx * dx; val m12 = -dx * dy; val m22 = 1 - dy * dy
            val w = line.weight
            a11 += w * m11; a12 += w * m12; a22 += w * m22
            b1 += w * (m11 * px + m12 * py); b2 += w * (m12 * px + m22 * py)
            doubleArrayOf(px, py, dx, dy)
        }
        val det = a11 * a22 - a12 * a12
        // Nearly parallel lines meet nowhere useful.
        if (abs(det) < 1e-6 * lines.sumOf { it.weight }.let { it * it }) return null
        val x = (a22 * b1 - a12 * b2) / det
        val y = (a11 * b2 - a12 * b1) / det

        // The source lies ahead of each observer, never behind.
        val ahead = local.count { (px, py, dx, dy) -> (x - px) * dx + (y - py) * dy > 0 }
        if (ahead < 2) return null

        val rms = sqrt(local.sumOf { (px, py, dx, dy) ->
            val cross = (x - px) * dy - (y - py) * dx
            cross * cross
        } / local.size)
        return Intersection(toLatLon(x, y, lat0, lon0, k), rms, lines.size)
    }

    /** The point [meters] away from [origin] along [bearingDeg] (great circle). */
    fun destination(origin: LatLon, bearingDeg: Double, meters: Double): LatLon {
        val d = meters / EARTH_RADIUS_M
        val t = bearingDeg * PI / 180.0
        val lat1 = origin.lat * PI / 180.0
        val lon1 = origin.lon * PI / 180.0
        val lat2 = kotlin.math.asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(t))
        val lon2 = lon1 + kotlin.math.atan2(sin(t) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return LatLon(lat2 * 180.0 / PI, lon2 * 180.0 / PI)
    }

    fun distanceMeters(a: LatLon, b: LatLon): Double {
        val k = cos((a.lat + b.lat) / 2 * PI / 180.0)
        val (x, y) = toLocal(b, a.lat, a.lon, k)
        return sqrt(x * x + y * y)
    }

    private fun toLocal(p: LatLon, lat0: Double, lon0: Double, k: Double): Pair<Double, Double> =
        Pair((p.lon - lon0) * PI / 180.0 * EARTH_RADIUS_M * k, (p.lat - lat0) * PI / 180.0 * EARTH_RADIUS_M)

    private fun toLatLon(x: Double, y: Double, lat0: Double, lon0: Double, k: Double) =
        LatLon(lat0 + y / EARTH_RADIUS_M * 180.0 / PI, lon0 + x / (EARTH_RADIUS_M * k) * 180.0 / PI)
}
