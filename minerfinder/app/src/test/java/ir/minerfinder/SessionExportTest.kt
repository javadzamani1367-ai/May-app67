package ir.minerfinder

import ir.minerfinder.data.BearingEntity
import ir.minerfinder.data.SessionEntity
import ir.minerfinder.data.TrackPointEntity
import ir.minerfinder.export.SessionExport
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExportTest {
    private val points = listOf(
        TrackPointEntity(1, 7, 1_700_000_000_000, 33.6, 46.4, 5f, 72.5f, 0.8f, """{"audio":80.1,"magnetometer":12.0}"""),
        TrackPointEntity(2, 7, 1_700_000_003_000, 33.6001, 46.4001, 6f, 40f, 0.5f, """{"audio":40.0}"""),
    )
    private val bearings = listOf(BearingEntity(1, 7, 1_700_000_005_000, 33.6, 46.4, 123.4, 0.4, 50.0))

    @Test
    fun csvHasOneColumnPerSourceAndEqualWidthRows() {
        val lines = SessionExport.csv(points, bearings).trim().lines()
        assertEquals(4, lines.size)
        assertTrue(lines[0].endsWith(",audio,magnetometer"))
        val width = lines[0].split(',').size
        assertTrue(lines.all { it.split(',').size == width })
        assertTrue(lines[1].startsWith("point,2023-11-14T22:13:20Z,"))
        assertTrue(lines[2].endsWith(",40.0,"))
        assertTrue(lines[3].startsWith("bearing,"))
        assertTrue(lines[3].contains(",123.4,0.400,"))
    }

    @Test
    fun jsonCarriesEverything() {
        val json = JSONObject(SessionExport.json(SessionEntity(7, 1_700_000_000_000), points, bearings, null, "0.1.1"))
        assertEquals("minerfinder-session", json.getString("format"))
        assertEquals(2, json.getJSONArray("points").length())
        assertEquals(80.1, json.getJSONArray("points").getJSONObject(0).getJSONObject("sources").getDouble("audio"), 1e-9)
        assertEquals(123.4, json.getJSONArray("bearings").getJSONObject(0).getDouble("bearingDeg"), 1e-9)
        assertTrue(!json.has("estimate"))
    }
}
