package ir.ilam.inspection.field

import ir.ilam.inspection.field.thermal.ExportFile
import ir.ilam.inspection.field.thermal.JpegSegments
import ir.ilam.inspection.field.thermal.Temperatures
import ir.ilam.inspection.field.thermal.ThermalExport
import ir.ilam.inspection.field.thermal.ThermalFileInfo
import ir.ilam.inspection.field.thermal.ThermalPayload
import ir.ilam.inspection.field.thermal.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalFilesTest {

    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    }

    private val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val jfif = segment(0xE0, "JFIF\u0000rest".toByteArray())
    private val radiometric = segment(0xEB, "HIKMICRO thermal data".toByteArray())
    private fun exif(text: String) = segment(0xE1, "Exif\u0000\u0000".toByteArray() + text.toByteArray())
    private val scan = byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0, 4, 1, 2, 9, 9, 0xFF.toByte(), 0xD9.toByte())
    private val trailer = "after the end".toByteArray()

    private fun jpeg(vararg parts: ByteArray) = parts.fold(soi) { acc, p -> acc + p }

    @Test
    fun newExifAloneIsAccepted() {
        val original = jpeg(jfif, exif("old"), radiometric, scan, trailer)
        val copy = jpeg(jfif, exif("with gps, longer"), radiometric, scan, trailer)
        assertTrue(JpegSegments.onlyExifChanged(original, copy))
        // Adding EXIF where there was none is also only an EXIF change.
        assertTrue(JpegSegments.onlyExifChanged(jpeg(jfif, radiometric, scan), jpeg(jfif, exif("gps"), radiometric, scan)))
    }

    @Test
    fun anyOtherChangeIsRefused() {
        val original = jpeg(jfif, exif("old"), radiometric, scan, trailer)
        val touchedThermal = jpeg(jfif, exif("new"), segment(0xEB, "HIKMICRO thermal dat4".toByteArray()), scan, trailer)
        val lostTrailer = jpeg(jfif, exif("new"), radiometric, scan)
        val reencoded = jpeg(jfif, exif("new"), radiometric, scan.copyOf().also { it[6] = 7 }, trailer)
        val reordered = jpeg(radiometric, exif("new"), jfif, scan, trailer)
        listOf(touchedThermal, lostTrailer, reencoded, reordered).forEach {
            assertFalse(JpegSegments.onlyExifChanged(original, it))
        }
    }

    @Test
    fun notAJpegIsRefused() {
        assertNull(JpegSegments.withoutExif("plain text".toByteArray()))
        assertNull(JpegSegments.withoutExif(soi + byteArrayOf(0xFF.toByte(), 0xE0.toByte(), 0x7F, 0x7F)))
        assertFalse(JpegSegments.onlyExifChanged(byteArrayOf(1, 2, 3), byteArrayOf(1, 2, 3)))
    }

    @Test
    fun payloadRoundTrips() {
        val payload = ThermalPayload(
            startedAt = 1_000, endedAt = 2_000, points = 42, clockSkewSeconds = -3,
            files = mapOf("f1" to ThermalFileInfo("20260618011917372.jpg", Temperatures("85.2", "21", "78.5", "40", "60.3")))
        )
        assertEquals(payload, ThermalPayload.fromJson(payload.toJson().toString()))
        assertEquals(ThermalPayload(), ThermalPayload.fromJson(null))
        assertEquals(ThermalPayload(), ThermalPayload.fromJson("not json"))
    }

    @Test
    fun temperaturesUseTheServerKeysAndSkipBlanks() {
        val json = Temperatures(max = "85.2", boxAvg = "-4").toJson()
        assertEquals(85.2, json.getDouble("max"), 1e-9)
        assertEquals(-4.0, json.getDouble("r1_avg"), 1e-9)
        assertFalse(json.has("min"))
        assertTrue(Temperatures().isEmpty())
    }

    @Test
    fun temperatureInputIsCleaned() {
        assertEquals("-4.5", Temperatures.clean("-4.5"))
        assertEquals("-4.5", Temperatures.clean("−4٫5"))
        assertEquals("45", Temperatures.clean("4-5"))
        assertEquals("4.55", Temperatures.clean("4.5.5"))
        assertEquals("12", Temperatures.clean(" 12°C "))
    }

    private val file = ExportFile(
        id = "f1", name = "20260618011917372.jpg", video = false, takenAt = 1_781_745_557_372,
        latitude = 33.6374, longitude = 46.4227, accuracy = 3.0, uncertain = false,
        assetLabel = "تابلو", plate = "P-12", note = "اتصال, \"داغ\"", sha256 = "abc",
        temperatures = Temperatures("85.2", "21", "78.5", "40", "60.3")
    )

    @Test
    fun kmlHasTheRouteAndAPinPerLocatedFile() {
        val track = listOf(TrackPoint(1, 1, 33.6, 46.4, 3.0, null), TrackPoint(2, 2, 33.7, 46.5, 3.0, null))
        val kml = ThermalExport.kml("TV-1405-000001 <x>", track, listOf(file, file.copy(id = "f2", latitude = null, longitude = null)))
        assertTrue(kml.contains("46.4,33.6,0 46.5,33.7,0"))
        assertTrue(kml.contains("<coordinates>46.4227,33.6374,0</coordinates>"))
        assertEquals(1, Regex("<Point>").findAll(kml).count())
        assertTrue(kml.contains("TV-1405-000001 &lt;x&gt;"))
        assertTrue(kml.contains("2026-06-18T"))
    }

    @Test
    fun csvQuotesWhatNeedsQuoting() {
        val lines = ThermalExport.csv(listOf(file)).removePrefix("﻿").trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("file,kind,taken_at_utc"))
        assertTrue(lines[1].contains("\"اتصال, \"\"داغ\"\"\""))
        assertTrue(lines[1].endsWith(",85.2,21,78.5,40,60.3,abc"))
    }
}
