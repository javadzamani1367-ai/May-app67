package ir.ilam.inspection

import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.LocationRefiner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRefinerTest {

    private val site = Fix(33.636921, 46.421709, 4.0)

    /** A point [north] and [east] metres from the site, with the given accuracy. */
    private fun near(north: Double, east: Double, accuracy: Double): Fix {
        val metresPerDegreeLat = 111_195.0
        val metresPerDegreeLon = metresPerDegreeLat * kotlin.math.cos(Math.toRadians(site.latitude))
        return Fix(
            site.latitude + north / metresPerDegreeLat,
            site.longitude + east / metresPerDegreeLon,
            accuracy
        )
    }

    @Test
    fun `nothing yet means no estimate`() {
        val refiner = LocationRefiner()
        assertNull(refiner.estimate())
        assertFalse(refiner.settled)
    }

    @Test
    fun `a coarse first fix is kept only until a better one arrives`() {
        val refiner = LocationRefiner()
        refiner.add(near(30.0, 10.0, 45.0))
        assertEquals(45.0, refiner.best!!.accuracy, 0.0)
        refiner.add(near(1.0, -1.0, 6.0))
        assertEquals(6.0, refiner.best!!.accuracy, 0.0)
    }

    @Test
    fun `the reported accuracy is never better than the best single fix`() {
        val refiner = LocationRefiner()
        listOf(4.0, 4.5, 5.0, 4.2).forEachIndexed { i, acc -> refiner.add(near(i * 0.5, -i * 0.5, acc)) }
        assertEquals(4.0, refiner.estimate()!!.accuracy, 0.0)
    }

    @Test
    fun `averaging pulls a wandering fix back towards the true point`() {
        val refiner = LocationRefiner()
        // Scattered symmetrically around the site, as a stationary receiver's error is.
        refiner.add(near(3.0, 0.0, 4.0))
        refiner.add(near(-3.0, 0.0, 4.0))
        refiner.add(near(0.0, 3.0, 4.0))
        refiner.add(near(0.0, -3.0, 4.0))
        val estimate = refiner.estimate()!!
        assertTrue(LocationRefiner.distanceMeters(estimate, site) < 0.5)
    }

    @Test
    fun `a far away cell tower fix does not drag the average`() {
        val refiner = LocationRefiner()
        refiner.add(near(0.5, 0.5, 4.0))
        refiner.add(near(-0.5, -0.5, 4.0))
        refiner.add(near(300.0, 0.0, 5.0)) // accurate-looking but nowhere near
        val estimate = refiner.estimate()!!
        assertTrue(LocationRefiner.distanceMeters(estimate, site) < 2.0)
    }

    @Test
    fun `settles only when the best is within target and others agree`() {
        val refiner = LocationRefiner(targetMeters = 5.0)
        refiner.add(near(0.0, 0.0, 3.0))
        assertFalse("one lucky fix is not enough", refiner.settled)
        refiner.add(near(0.5, 0.0, 3.5))
        refiner.add(near(0.0, 0.5, 4.0))
        assertTrue(refiner.settled)
    }

    @Test
    fun `never settles while the fixes stay coarse`() {
        val refiner = LocationRefiner(targetMeters = 5.0)
        repeat(20) { refiner.add(near(it * 0.2, 0.0, 14.0)) }
        assertFalse(refiner.settled)
        assertEquals(14.0, refiner.estimate()!!.accuracy, 0.0)
    }

    @Test
    fun `nonsense fixes are ignored`() {
        val refiner = LocationRefiner()
        refiner.add(Fix(33.6, 46.4, 0.0))
        refiner.add(Fix(33.6, 46.4, Double.NaN))
        refiner.add(Fix(33.6, 46.4, 5_000.0))
        refiner.add(Fix(133.6, 46.4, 5.0))
        assertEquals(0, refiner.count)
    }

    @Test
    fun `distance is right to within a metre over a hundred`() {
        val hundredNorth = near(100.0, 0.0, 4.0)
        assertEquals(100.0, LocationRefiner.distanceMeters(site, hundredNorth), 1.0)
    }
}
