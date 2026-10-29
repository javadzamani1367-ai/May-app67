package ir.minerfinder.core

import ir.minerfinder.core.direction.SweepAnalyzer
import ir.minerfinder.core.feedback.ProximityMapper
import ir.minerfinder.core.geo.BearingLine
import ir.minerfinder.core.geo.LatLon
import ir.minerfinder.core.geo.Triangulation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class SpatialTest {

    @Test
    fun sweepFindsTheLoudDirection() {
        val sweep = SweepAnalyzer()
        // Loudest towards 120°, wrapping smoothly around the circle.
        for (h in 0 until 360 step 3) {
            val diff = (h - 120.0) * PI / 180.0
            sweep.add(h.toDouble(), 40 + 40 * cos(diff))
        }
        val estimate = sweep.estimate()
        assertNotNull(estimate)
        assertEquals(120.0, estimate!!.bearingDeg, 6.0)
        assertTrue(estimate.sharpness > 0.3)
    }

    @Test
    fun sweepWrapsAroundNorth() {
        val sweep = SweepAnalyzer()
        for (h in 0 until 360 step 2) {
            val diff = (h - 355.0) * PI / 180.0
            sweep.add(h.toDouble(), 50 + 40 * cos(diff))
        }
        val bearing = sweep.estimate()!!.bearingDeg
        val error = minOf(kotlin.math.abs(bearing - 355.0), 360 - kotlin.math.abs(bearing - 355.0))
        assertTrue("bearing $bearing", error < 6.0)
    }

    @Test
    fun aPartialSweepGivesNoEstimate() {
        val sweep = SweepAnalyzer()
        for (h in 0 until 90) sweep.add(h.toDouble(), 50.0)
        assertNull(sweep.estimate())
    }

    @Test
    fun twoBearingsMeetAtTheSource() {
        val source = LatLon(33.6374, 46.4227)
        // Observers 300 m south and 300 m west of the source.
        val south = LatLon(source.lat - 300 / 111_195.0, source.lon)
        val west = LatLon(source.lat, source.lon - 300 / (111_195.0 * cos(source.lat * PI / 180)))
        val hit = Triangulation.intersect(listOf(BearingLine(south, 0.0), BearingLine(west, 90.0)))
        assertNotNull(hit)
        assertTrue(Triangulation.distanceMeters(hit!!.point, source) < 2.0)
        assertTrue(hit.rmsMeters < 1.0)
    }

    @Test
    fun parallelOrDivergingBearingsDoNotMeet() {
        val a = LatLon(33.6, 46.4)
        val b = LatLon(33.6, 46.41)
        assertNull(Triangulation.intersect(listOf(BearingLine(a, 0.0), BearingLine(b, 0.0))))
        // Pointing away from each other: they cross behind both observers.
        assertNull(Triangulation.intersect(listOf(BearingLine(a, 300.0), BearingLine(b, 60.0))))
    }

    @Test
    fun beepsGetFasterWithScore() {
        assertNull(ProximityMapper.beepIntervalMs(2f))
        val slow = ProximityMapper.beepIntervalMs(20f)!!
        val fast = ProximityMapper.beepIntervalMs(90f)!!
        assertTrue(fast < slow)
        assertEquals(60L, ProximityMapper.beepIntervalMs(100f))
    }
}
