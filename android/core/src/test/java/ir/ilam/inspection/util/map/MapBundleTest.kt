package ir.ilam.inspection.util.map

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MapBundleTest {

    private val geo = javaClass.getResourceAsStream("/geo/sample.geo")!!.use { it.readBytes() }
    private val map = "not really a map, but the bundle only checks its bytes".toByteArray()
    private val target: File = Files.createTempDirectory("bundle").toFile()

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun manifest(edit: JSONObject.() -> Unit = {}) = JSONObject()
        .put("format", 1).put("region", "ilam").put("version", "20261004")
        .put("map", "ilam.map").put("map_sha256", sha(map)).put("map_size", map.size)
        .put("geo", "ilam.geo").put("geo_sha256", sha(geo)).put("geo_size", geo.size)
        .apply(edit).toString().toByteArray()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (name, bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
        return out.toByteArray()
    }

    private fun good() = zip("manifest.json" to manifest(), "ilam.map" to map, "ilam.geo" to geo)

    private fun refused(bytes: ByteArray, reason: MapBundleException.Reason) {
        try {
            MapBundle.unpack(bytes.inputStream(), target)
            fail("accepted a bad bundle")
        } catch (e: MapBundleException) {
            assertEquals(reason, e.reason)
        }
    }

    @Test
    fun aGoodBundleUnpacks() {
        val m = MapBundle.unpack(good().inputStream(), target)
        assertEquals("20261004", m.version)
        assertTrue(File(target, "ilam.map").readBytes().contentEquals(map))
        assertEquals(m, MapBundle.verify(target))
    }

    @Test
    fun aChangedByteIsCaught() {
        val tampered = map.copyOf().also { it[0] = 'N'.code.toByte() }
        refused(zip("manifest.json" to manifest(), "ilam.map" to tampered, "ilam.geo" to geo), MapBundleException.Reason.CHECKSUM)
    }

    @Test
    fun onlyTheThreeNamesAreWritten() {
        refused(zip("manifest.json" to manifest(), "ilam.map" to map, "ilam.geo" to geo, "../evil.sh" to byteArrayOf(1)),
            MapBundleException.Reason.UNEXPECTED_FILE)
        refused(zip("maps/ilam.map" to map), MapBundleException.Reason.UNEXPECTED_FILE)
        assertFalse(File(target.parentFile, "evil.sh").exists())
    }

    @Test
    fun missingPartsAndBadManifestsAreRefused() {
        refused(zip("manifest.json" to manifest(), "ilam.map" to map), MapBundleException.Reason.MISSING_FILE)
        refused(zip("ilam.map" to map, "ilam.geo" to geo), MapBundleException.Reason.MISSING_FILE)
        refused(zip("manifest.json" to manifest { put("format", 2) }, "ilam.map" to map, "ilam.geo" to geo),
            MapBundleException.Reason.MANIFEST)
        refused(zip("manifest.json" to manifest { put("version", "../x") }, "ilam.map" to map, "ilam.geo" to geo),
            MapBundleException.Reason.MANIFEST)
        refused(zip("manifest.json" to "{".toByteArray(), "ilam.map" to map, "ilam.geo" to geo), MapBundleException.Reason.MANIFEST)
        refused("not a zip at all".toByteArray(), MapBundleException.Reason.NOT_A_BUNDLE)
    }

    @Test
    fun anIndexThatDoesNotReadIsRefused() {
        val broken = geo.copyOf(geo.size - 4)
        val manifest = manifest { put("geo_sha256", sha(broken)).put("geo_size", broken.size) }
        refused(zip("manifest.json" to manifest, "ilam.map" to map, "ilam.geo" to broken), MapBundleException.Reason.BAD_INDEX)
    }

    @Test
    fun serverAddressFormsTheUrl() {
        assertEquals("https://helth.ir/api/maps/tavankav-ilam-map.zip", MapDownload.serverUrl("https://helth.ir/api/"))
        assertEquals("https://helth.ir/api/maps/tavankav-ilam-map.zip", MapDownload.serverUrl(" https://helth.ir/api "))
    }
}
