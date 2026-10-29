package ir.minerfinder.core

import ir.minerfinder.core.geo.LatLon
import ir.minerfinder.core.geo.Triangulation
import ir.minerfinder.core.util.Jalali
import ir.minerfinder.core.util.JalaliDate
import org.junit.Assert.assertEquals
import org.junit.Test

class UtilTest {
    @Test
    fun jalaliKnownDates() {
        assertEquals(JalaliDate(1403, 1, 1), Jalali.fromGregorian(2024, 3, 20))
        assertEquals(JalaliDate(1405, 1, 1), Jalali.fromGregorian(2026, 3, 21))
        assertEquals(JalaliDate(1405, 8, 7), Jalali.fromGregorian(2026, 10, 29))
        assertEquals(JalaliDate(1403, 12, 30), Jalali.fromGregorian(2025, 3, 20))
    }

    @Test
    fun destinationTravelsTheDistance() {
        val a = LatLon(33.6374, 46.4227)
        val b = Triangulation.destination(a, 45.0, 1000.0)
        assertEquals(1000.0, Triangulation.distanceMeters(a, b), 2.0)
        val north = Triangulation.destination(a, 0.0, 500.0)
        assertEquals(a.lon, north.lon, 1e-9)
    }
}
