package ir.ilam.inspection

import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.PhaseType
import ir.ilam.inspection.data.model.TechnicalInput
import ir.ilam.inspection.util.PowerCalc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerCalcTest {

    @Test
    fun `single phase multiplies its one reading`() {
        val result = PowerCalc.of(PhaseType.SINGLE, listOf(32.0, 99.0, 99.0), listOf(230.0, 1.0, 1.0))
        assertEquals(1, result.phases.size)
        assertEquals(7360.0, result.totalWatt!!, 0.001)
        assertEquals(32.0, result.totalAmperage!!, 0.001)
        assertEquals(7.36, result.totalKilowatt!!, 0.001)
    }

    @Test
    fun `three phase sums each phase against its own voltage`() {
        val result = PowerCalc.of(
            PhaseType.THREE,
            listOf(10.0, 20.0, 30.0),
            listOf(220.0, 225.0, 230.0)
        )
        assertEquals(2200.0, result.phases[0].watt!!, 0.001)
        assertEquals(4500.0, result.phases[1].watt!!, 0.001)
        assertEquals(6900.0, result.phases[2].watt!!, 0.001)
        assertEquals(13600.0, result.totalWatt!!, 0.001)
        assertEquals(60.0, result.totalAmperage!!, 0.001)
    }

    @Test
    fun `a half filled phase contributes its amps but no power`() {
        val result = PowerCalc.of(PhaseType.THREE, listOf(10.0, 20.0, null), listOf(220.0, null, 230.0))
        assertEquals(2200.0, result.totalWatt!!, 0.001)
        assertEquals(30.0, result.totalAmperage!!, 0.001)
        assertNull(result.phases[1].watt)
        assertTrue(result.hasReading)
    }

    @Test
    fun `nothing measured reports nothing rather than zero`() {
        val result = PowerCalc.of(PhaseType.THREE, listOf(null, null, null), listOf(null, null, null))
        assertNull(result.totalWatt)
        assertNull(result.totalAmperage)
        assertNull(result.totalKilowatt)
        assertFalse(result.hasReading)
    }

    @Test
    fun `switching to single phase clears the phases that no longer exist`() {
        val blank = ReportEntity(reportType = 3, status = 0, reportDate = 0L)
        val three = TechnicalInput(
            phaseType = PhaseType.THREE,
            amperage = listOf("10", "20", "30"),
            voltage = listOf("220", "220", "220")
        ).applyTo(blank)
        assertEquals(20.0, three.amperageS!!, 0.001)

        val corrected = TechnicalInput.from(three)
            .copy(phaseType = PhaseType.SINGLE)
            .applyTo(three)
        assertEquals(10.0, corrected.amperageR!!, 0.001)
        assertNull(corrected.amperageS)
        assertNull(corrected.amperageT)
        assertEquals(2200.0, corrected.totalWatt!!, 0.001)
    }

    @Test
    fun `a saved case reads back as the form that produced it`() {
        val blank = ReportEntity(reportType = 3, status = 0, reportDate = 0L)
        val input = TechnicalInput(
            phaseType = PhaseType.THREE,
            amperage = listOf("12.5", "13", "14"),
            voltage = listOf("220", "221", "222"),
            sealExternal = true,
            sealExternalSerial = "A-۱۲۳",
            tampered = false
        )
        val saved = input.applyTo(blank)
        val reloaded = TechnicalInput.from(saved)
        assertEquals(input.phaseType, reloaded.phaseType)
        assertEquals(true, reloaded.sealExternal)
        assertEquals(false, reloaded.tampered)
        assertNull(reloaded.appearanceOk)
        assertEquals(saved.totalWatt!!, reloaded.power().totalWatt!!, 0.001)
    }
}
