package ir.ilam.inspection.field

import ir.ilam.inspection.field.data.LineAnalysis
import ir.ilam.inspection.field.data.LinePayload
import ir.ilam.inspection.field.data.PhaseReading
import ir.ilam.inspection.field.data.PoleReading
import ir.ilam.inspection.field.data.PoleState
import ir.ilam.inspection.field.data.SegmentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineAnalysisTest {

    private fun pole(r: String, s: String, t: String, lat: Double? = 33.6, lon: Double? = 46.4) =
        PoleReading(latitude = lat, longitude = lon, accuracy = 4.0, phases = PhaseReading(r, s, t))

    @Test
    fun heavyStretchIsTheOneThatLostTheLoad() {
        val payload = LinePayload(
            feeder = "A",
            start = PhaseReading("100", "100", "100"),
            poles = listOf(
                pole("95", "96", "94", 33.6000, 46.4000),
                pole("90", "92", "91", 33.6005, 46.4000),
                pole("40", "45", "38", 33.6010, 46.4000), // the farm is between pole 2 and pole 3
                pole("36", "41", "35", 33.6015, 46.4000)
            )
        )
        val verdict = LineAnalysis.verdict(payload)
        assertEquals("panel plus four poles", 5, verdict.points.size)
        assertEquals(60.0, verdict.heavyA, 1e-9) // 20% of 300 A at the panel
        val heaviest = verdict.heaviest!!
        assertEquals(2, heaviest.from.pole)
        assertEquals(3, heaviest.to.pole)
        assertEquals(150.0, heaviest.drop, 1e-9)
        assertEquals(listOf(heaviest), verdict.heavy)
        assertTrue(verdict.rises.isEmpty())
        assertEquals(112.0, verdict.tail!!, 1e-9)
        assertEquals("about 55 m between poles", 55.0, heaviest.metres!!, 1.0)
        val json = verdict.toJson()
        assertEquals(2, json.getJSONObject("heaviest").getInt("from"))
        assertEquals(1, json.getJSONArray("heavy").length())
    }

    @Test
    fun currentThatRisesDownstreamIsFlagged() {
        val payload = LinePayload(feeder = "B", poles = listOf(pole("50", "50", "50"), pole("60", "58", "57")))
        val verdict = LineAnalysis.verdict(payload)
        assertEquals("no panel reading: the first pole starts the line", 1, verdict.points.first().pole)
        assertEquals(SegmentKind.RISE, verdict.kindOf(verdict.segments.single()))
        assertNull("nothing was lost, so nothing is heaviest", verdict.heaviest)
    }

    @Test
    fun notMeasuredPolesAreSkippedNotZero() {
        val payload = LinePayload(feeder = "C", poles = listOf(
            pole("80", "80", "80"),
            PoleReading(latitude = 33.6, longitude = 46.4, state = PoleState.NOT_MEASURED, reason = "ارتفاع زیاد"),
            pole("70", "70", "70")
        ))
        val verdict = LineAnalysis.verdict(payload)
        assertEquals(listOf(1, 3), verdict.points.map { it.pole })
        assertEquals(30.0, verdict.segments.single().drop, 1e-9)
        assertTrue(LineAnalysis.missing(payload).isEmpty())
    }

    @Test
    fun missingSaysWhatEachPoleLacks() {
        val payload = LinePayload(poles = listOf(
            pole("10", "", "12", lat = null, lon = null),
            PoleReading(latitude = 33.6, longitude = 46.4, state = PoleState.NOT_MEASURED)
        ))
        val missing = LineAnalysis.missing(payload)
        assertTrue("feeder" in missing)
        assertTrue("poles" in missing)
        assertTrue("pole_position:1" in missing)
        assertTrue("pole_values:1" in missing)
        assertTrue("pole_reason:2" in missing)
        assertFalse("pole_position:2" in missing)
        assertTrue("other" + " needs a name", "feeder" in LineAnalysis.missing(LinePayload(feeder = "other")))
    }

    @Test
    fun payloadRoundTrip() {
        val payload = LinePayload(
            feeder = "other", feederName = "فیدر روستا", network = "abc",
            start = PhaseReading("120", "110.5", "130"),
            poles = listOf(
                pole("100", "95", "90").copy(plate = "T-12", branches = 3, neutral = "8", photos = listOf(1700000000000L)),
                PoleReading(state = PoleState.NOT_MEASURED, reason = "باران")
            ),
            note = "مسیر کوهستانی"
        )
        val json = payload.toJson(LineAnalysis.verdict(payload))
        assertEquals(2, json.getJSONArray("poles").getJSONObject(1).getInt("n"))
        assertFalse(json.getJSONArray("poles").getJSONObject(1).has("phases"))
        val back = LinePayload.fromJson(json.toString())
        assertEquals(payload.copy(poles = payload.poles.map { it.copy(positionAt = null) }),
            back.copy(poles = back.poles.map { it.copy(positionAt = null) }))
    }
}
