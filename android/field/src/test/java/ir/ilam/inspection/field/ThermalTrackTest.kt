package ir.ilam.inspection.field

import ir.ilam.inspection.field.thermal.HikmicroNames
import ir.ilam.inspection.field.thermal.TrackMatcher
import ir.ilam.inspection.field.thermal.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ThermalTrackTest {

    private fun point(t: Long, lat: Double, lon: Double, acc: Double = 4.0, skew: Long = 0) =
        TrackPoint(t, t - skew, lat, lon, acc, null)

    private val route = listOf(
        point(1_000_000, 33.60, 46.40, 3.0),
        point(1_010_000, 33.61, 46.42, 5.0),
        point(1_020_000, 33.62, 46.44, 4.0)
    )

    @Test
    fun interpolatesBetweenPoints() {
        val at = TrackMatcher.locate(route, 1_005_000)!!
        assertEquals(33.605, at.latitude, 1e-9)
        assertEquals(46.41, at.longitude, 1e-9)
        // The worse of the two neighbours: the estimate is no better than either.
        assertEquals(5.0, at.accuracy, 1e-9)
        assertEquals(5_000, at.gapMillis)
        assertFalse(at.uncertain)
    }

    @Test
    fun exactPointIsThatPoint() {
        val at = TrackMatcher.locate(route, 1_010_000)!!
        assertEquals(33.61, at.latitude, 1e-9)
        assertEquals(0, at.gapMillis)
    }

    @Test
    fun outsideTheRouteTakesTheNearestEnd() {
        val before = TrackMatcher.locate(route, 990_000)!!
        assertEquals(33.60, before.latitude, 1e-9)
        assertFalse(before.uncertain)
        val after = TrackMatcher.locate(route, 1_030_000)!!
        assertEquals(33.62, after.latitude, 1e-9)
    }

    @Test
    fun farFromAnyPointIsUncertainButKept() {
        val at = TrackMatcher.locate(route, 1_020_000 + TrackMatcher.MAX_GAP_MILLIS + 1)
        assertNotNull(at)
        assertTrue(at!!.uncertain)
        val gapInside = listOf(point(0, 33.0, 46.0), point(10 * 60_000, 34.0, 47.0))
        assertTrue(TrackMatcher.locate(gapInside, 5 * 60_000)!!.uncertain)
    }

    @Test
    fun unsortedPointsAreSortedFirst() {
        val at = TrackMatcher.locate(route.reversed(), 1_015_000)!!
        assertEquals(33.615, at.latitude, 1e-9)
    }

    @Test
    fun noRouteNoPosition() {
        assertNull(TrackMatcher.locate(emptyList(), 1_000))
        assertNull(TrackMatcher.clockSkewSeconds(emptyList()))
    }

    @Test
    fun clockSkewIsTheMedian() {
        val points = listOf(point(1, 0.0, 0.0, skew = 30_000), point(2, 0.0, 0.0, skew = 31_000),
            point(3, 0.0, 0.0, skew = 900_000))
        assertEquals(31L, TrackMatcher.clockSkewSeconds(points))
    }

    @Test
    fun csvLineRoundTrips() {
        val p = TrackPoint(1_700_000_000_123, 1_700_000_000_000, 33.6374512, 46.4227781, 3.5, 1312.4)
        assertEquals(p, TrackPoint.parse(p.toLine()))
        val noAltitude = p.copy(altitude = null)
        assertEquals(noAltitude, TrackPoint.parse(noAltitude.toLine()))
        assertNull(TrackPoint.parse(TrackPoint.HEADER))
        assertNull(TrackPoint.parse("garbage"))
    }

    @Test
    fun hikmicroNameIsTheCaptureTime() {
        val utc = TimeZone.getTimeZone("UTC")
        // 2026-09-28 11:27:24.235 UTC
        assertEquals(1_790_594_844_235L, HikmicroNames.capturedAt("20260928112724235.mp4", utc))
        assertEquals(1_790_594_844_235L, HikmicroNames.capturedAt("20260928112724235", utc))
        val tehran = TimeZone.getTimeZone("Asia/Tehran")
        // The same wall-clock time in Tehran (UTC+3:30, no daylight saving since 2022) is 3.5 hours earlier.
        assertEquals(1_790_594_844_235L - 12_600_000L, HikmicroNames.capturedAt("20260928112724235.jpg", tehran))
    }

    @Test
    fun otherNamesAreRejected() {
        assertNull(HikmicroNames.capturedAt("IMG_20260928_112724.jpg"))
        assertNull(HikmicroNames.capturedAt("20261328112724235.jpg"))
        assertNull(HikmicroNames.capturedAt("20260928256024235.jpg"))
        assertNull(HikmicroNames.capturedAt("2026092811.jpg"))
    }
}
