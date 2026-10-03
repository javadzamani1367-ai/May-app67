package ir.ilam.inspection.field

import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.data.ReportDetails
import ir.ilam.inspection.field.data.ReportKeys
import ir.ilam.inspection.field.ui.report.ReportLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportDetailsTest {

    @Test
    fun cryptoDetailsRoundTrip() {
        val details = ReportDetails(
            amperage = "45.5", minerCount = "12", minerType = "S19",
            signs = setOf("fan_noise", "night_activity"),
            answers = mapOf("needs_police" to true, "active_now" to false),
            bestTime = "night", entrances = "2", entranceNote = "در پشتی"
        )
        val json = details.toJson(FieldKind.CRYPTO)
        assertEquals(45.5, json.getDouble("amperage"), 1e-9)
        assertEquals(12L, json.getLong("miner_count"))
        assertFalse("illegal-power fields stay out of a crypto report", json.has("violations"))
        assertTrue(json.getJSONObject("team").getBoolean("needs_police"))
        val back = ReportDetails.fromJson(json.toString())
        assertEquals(details.signs, back.signs)
        assertEquals(details.answers, back.answers)
        assertEquals("night", back.bestTime)
        assertEquals("45.5", back.amperage)
        assertEquals("12", back.minerCount)
    }

    @Test
    fun illegalDetailsKeepCountsAndDropZeros() {
        val details = ReportDetails(
            consumers = mapOf("heater" to 3, "welder" to 0),
            usage = "agri_well",
            violations = setOf("no_meter"),
            meterOrBill = "12345"
        )
        val json = details.toJson(FieldKind.ILLEGAL)
        assertEquals(3, json.getJSONObject("consumers").getInt("heater"))
        assertFalse(json.getJSONObject("consumers").has("welder"))
        val back = ReportDetails.fromJson(json.toString())
        assertEquals(mapOf("heater" to 3), back.consumers)
        assertEquals("agri_well", back.usage)
    }

    @Test
    fun brokenOrMissingPayloadIsEmptyNotACrash() {
        assertEquals(ReportDetails(), ReportDetails.fromJson(null))
        assertEquals(ReportDetails(), ReportDetails.fromJson("not json"))
    }

    @Test
    fun everyChecklistKeyHasALabel() {
        assertEquals(ReportKeys.SIGNS.toSet(), ReportLabels.SIGNS.keys)
        assertEquals(ReportKeys.CONSUMERS.toSet(), ReportLabels.CONSUMERS.keys)
        assertEquals(ReportKeys.USAGES.toSet(), ReportLabels.USAGES.keys)
        assertEquals(ReportKeys.VIOLATIONS.toSet(), ReportLabels.VIOLATIONS.keys)
        assertEquals(ReportKeys.TEAM_QUESTIONS.toSet(), ReportLabels.TEAM_QUESTIONS.keys)
        assertEquals(ReportKeys.BEST_TIMES.toSet(), ReportLabels.BEST_TIMES.keys)
    }
}
