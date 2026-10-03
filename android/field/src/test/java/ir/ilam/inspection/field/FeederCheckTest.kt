package ir.ilam.inspection.field

import ir.ilam.inspection.field.data.FeederCheck
import ir.ilam.inspection.field.data.FeederPayload
import ir.ilam.inspection.field.data.FeederReading
import ir.ilam.inspection.field.data.FeederState
import ir.ilam.inspection.field.data.PhaseReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeederCheckTest {

    private fun measured(r: Int, s: Int, t: Int) =
        FeederReading(FeederState.MEASURED, PhaseReading(r.toString(), s.toString(), t.toString()))
    private val absent = FeederReading(FeederState.ABSENT)
    private val main = PhaseReading("100", "120", "80")

    @Test
    fun matchingFeedersPass() {
        val feeders = listOf(measured(60, 70, 40), measured(38, 50, 41), absent, absent)
        val verdict = FeederCheck.verdict(main, feeders, 10, 5)
        assertTrue(verdict.possible)
        assertFalse(verdict.mismatched)
        assertEquals(4, verdict.phases.size)
        assertEquals(-2.0, verdict.phases[0].difference, 1e-9)
    }

    @Test
    fun aPhaseOutsideTheToleranceIsNamed() {
        // R: 100 vs 70 — 30 A short against a 10 A tolerance.
        val feeders = listOf(measured(40, 70, 40), measured(30, 50, 40), absent, absent)
        val verdict = FeederCheck.verdict(main, feeders, 10, 5)
        assertTrue(verdict.mismatched)
        assertFalse(verdict.phases[0].ok)
        assertTrue(verdict.phases[1].ok)
        assertEquals(-30.0, verdict.phases[0].difference, 1e-9)
    }

    @Test
    fun theAbsoluteFloorProtectsLightLoads() {
        // 10% of 8 A is 0.8 A; the 5 A floor lets a 3 A difference through.
        val light = PhaseReading("8", "8", "8")
        val verdict = FeederCheck.verdict(light, listOf(measured(5, 8, 8), absent, absent, absent), 10, 5)
        assertFalse(verdict.mismatched)
        assertEquals(5.0, verdict.phases[0].tolerance, 1e-9)
    }

    @Test
    fun theTotalIsCheckedToo() {
        // Light load: each phase 4 A short is inside its 5 A floor, but 12 A
        // missing in total is outside the total's own 5 A floor.
        val light = PhaseReading("8", "8", "8")
        val verdict = FeederCheck.verdict(light, listOf(measured(4, 4, 4), absent, absent, absent), 10, 5)
        assertTrue(verdict.phases.take(3).all { it.ok })
        assertFalse(verdict.phases[3].ok)
        assertTrue(verdict.mismatched)
    }

    @Test
    fun aFeederNotMeasuredMakesTheCheckImpossibleNotBlocking() {
        val feeders = listOf(measured(10, 10, 10), FeederReading(FeederState.NOT_MEASURED, reason = "قفل بود"), absent, absent)
        val verdict = FeederCheck.verdict(main, feeders, 10, 5)
        assertFalse(verdict.possible)
        assertFalse("an impossible check never blocks saving", verdict.mismatched)
    }

    @Test
    fun allFeedersAbsentWithLoadOnTheMainSwitchIsAMismatch() {
        val verdict = FeederCheck.verdict(main, List(4) { absent }, 10, 5)
        assertTrue("load going nowhere is exactly what the check is for", verdict.mismatched)
    }

    @Test
    fun missingSaysWhatAndWhere() {
        val feeders = listOf(
            FeederReading(),
            FeederReading(FeederState.MEASURED, PhaseReading("1", "", "3")),
            FeederReading(FeederState.NOT_MEASURED),
            FeederReading(FeederState.MEASURED, PhaseReading("5000", "1", "1"))
        )
        assertEquals(
            listOf("main", "state:A", "values:B", "reason:C", "range:D"),
            FeederCheck.missing(PhaseReading("1", "", "1"), feeders)
        )
        assertEquals(emptyList<String>(), FeederCheck.missing(main, listOf(measured(1, 1, 1), absent, absent, absent)))
    }

    @Test
    fun imbalanceIsLargestMinusSmallestOverMean() {
        assertEquals(40.0, FeederCheck.imbalancePct(listOf(100.0, 120.0, 80.0))!!, 1e-9)
        assertNull(FeederCheck.imbalancePct(listOf(0.0, 0.0, 0.0)))
        assertNull(FeederCheck.imbalancePct(listOf(1.0, null, 1.0)))
    }

    @Test
    fun payloadRoundTripsAndCarriesTheVerdict() {
        val payload = FeederPayload(main, listOf(measured(60, 70, 40), absent,
            FeederReading(FeederState.NOT_MEASURED, reason = "قفل بود"), FeederReading()), note = "تابلو زنگ‌زده")
        val verdict = FeederCheck.verdict(payload.main, payload.feeders, 10, 5)
        val json = payload.toJson(verdict, 10, 5)
        assertEquals(100.0, json.getJSONObject("main").getDouble("r"), 1e-9)
        assertEquals("absent", json.getJSONObject("feeders").getJSONObject("B").getString("state"))
        assertFalse(json.getJSONObject("check").getBoolean("possible"))
        assertEquals(10, json.getJSONObject("check").getInt("tolerance_pct"))
        assertEquals(payload, FeederPayload.fromJson(json.toString()))
    }
}
