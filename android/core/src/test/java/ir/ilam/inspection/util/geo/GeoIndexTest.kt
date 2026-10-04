package ir.ilam.inspection.util.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Against the index tools/map/build_geo.py wrote from tools/map/sample.osm, so
 * the Python writer and this reader are checked against each other. If the
 * sample changes, rebuild the resource:
 *   python3 tools/map/build_geo.py tools/map/sample.osm android/core/src/test/resources/geo/sample.geo
 */
class GeoIndexTest {

    private val index = javaClass.getResourceAsStream("/geo/sample.geo")!!.use { GeoIndex.read(it) }

    private val words = AddressWords(
        county = "شهرستان %1\$s",
        district = "بخش %1\$s",
        village = "روستای %1\$s",
        nearPlace = "حوالی %1\$s",
        nearStreet = "نزدیک %1\$s (%2\$s متر)",
        villagePrefixes = listOf("روستا"),
        countyPrefix = "شهرستان",
        districtPrefix = "بخش",
        separator = "، ",
        digits = { it }
    )

    @Test
    fun readsWhatTheBuilderWrote() {
        assertEquals(4, index.places.size)
        assertEquals(2, index.streetCount)
        assertEquals(2, index.areas.size)
        // The Persian name wins over the plain one.
        assertTrue(index.places.any { it.name == "بان آزمون" })
    }

    @Test
    fun pointOnACityStreet() {
        val m = index.lookup(33.6380, 46.4225)
        assertEquals("بلوار آزمون", m.street)
        assertTrue(m.streetMetres!! < 5)
        assertEquals("ایلام", m.settlement?.name)
        assertTrue(m.settlement!!.inside)
        assertEquals("ایلام", m.county)
        assertEquals("بخش مرکزی", m.district)
        // County and city share a name: said once.
        assertEquals("ایلام، بلوار آزمون", AddressText.compose(m, words))
    }

    @Test
    fun nearAStreetGivesTheDistance() {
        val m = index.lookup(33.63827, 46.4225)
        assertEquals("بلوار آزمون", m.street)
        assertEquals("ایلام، نزدیک بلوار آزمون (30 متر)", AddressText.compose(m, words))
    }

    @Test
    fun neighbourhoodButNoStreetInReach() {
        val m = index.lookup(33.6415, 46.4290)
        assertEquals("کوی نمونه", m.neighbourhood?.name)
        assertNull(m.street)
        assertEquals("ایلام، کوی نمونه", AddressText.compose(m, words))
    }

    @Test
    fun villageNamesAreNotDoubled() {
        val m = index.lookup(33.7005, 46.3005)
        assertEquals("روستای آزمون", m.settlement?.name)
        assertNull(m.neighbourhood)
        assertNull(m.district)
        assertEquals("شهرستان ایلام، روستای آزمون", AddressText.compose(m, words))
    }

    @Test
    fun aVillageNamedPlainlyGetsTheWord() {
        val m = index.lookup(33.6305, 46.4105)
        assertEquals("بان آزمون", m.settlement?.name)
        assertEquals("شهرستان ایلام، روستای بان آزمون", AddressText.compose(m, words))
    }

    @Test
    fun nearButNotInAPlace() {
        // About 1.3 km from the village: within its reach, not inside it.
        val m = index.lookup(33.7120, 46.3000)
        assertEquals("روستای آزمون", m.settlement?.name)
        assertFalse(m.settlement!!.inside)
        assertEquals("شهرستان ایلام، حوالی روستای آزمون", AddressText.compose(m, words))
    }

    @Test
    fun outsideEverythingSaysNothing() {
        val m = index.lookup(33.95, 46.0)
        assertTrue(m.isEmpty)
        assertEquals("", AddressText.compose(m, words))
    }

    @Test
    fun namesThatAlreadySayCountyAreNotDoubled() {
        val m = GeoMatch(county = "شهرستان ایوان", settlement = NearPlace("ایوان", PlaceKind.CITY, 100.0, true))
        assertEquals("ایوان", AddressText.compose(m, words))
        val other = GeoMatch(county = "شهرستان چرداول", settlement = NearPlace("سرابله", PlaceKind.CITY, 100.0, true))
        assertEquals("شهرستان چرداول، سرابله", AddressText.compose(other, words))
    }

    @Test
    fun outInTheCountryTheDistrictIsGiven() {
        val m = GeoMatch(county = "شهرستان ایلام", district = "بخش سیوان")
        assertEquals("شهرستان ایلام، بخش سیوان", AddressText.compose(m, words))
        // Not when there is a village to name instead.
        val near = m.copy(settlement = NearPlace("روستای آزمون", PlaceKind.VILLAGE, 300.0, true))
        assertEquals("شهرستان ایلام، روستای آزمون", AddressText.compose(near, words))
    }

    @Test
    fun brokenFilesAreRefused() {
        val good = javaClass.getResourceAsStream("/geo/sample.geo")!!.use { it.readBytes() }
        listOf("hello".toByteArray(), good.copyOf(good.size - 3), good + byteArrayOf(1),
            good.copyOf().also { it[5] = 9 }).forEach { bytes ->
            try {
                GeoIndex.read(bytes.inputStream())
                fail("accepted a broken index")
            } catch (expected: GeoFormatException) {
            }
        }
    }
}
