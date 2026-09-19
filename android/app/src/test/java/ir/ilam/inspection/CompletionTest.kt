package ir.ilam.inspection

import ir.ilam.inspection.data.db.MediaEntity
import ir.ilam.inspection.data.db.ReportEntity
import ir.ilam.inspection.data.model.Completion
import ir.ilam.inspection.data.model.MediaType
import ir.ilam.inspection.data.model.PhaseType
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.data.model.TechnicalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a visit must contain before it can leave the pending queue. This is
 * the gate that stops a half-finished case being filed, and it has to name
 * exactly what is missing rather than simply refusing.
 */
class CompletionTest {

    private val blank = ReportEntity(reportType = 3, status = 0, reportDate = 0L)

    private fun photo() = MediaEntity(
        reportId = blank.id,
        type = MediaType.IMAGE.code,
        filePath = "media/x.jpg",
        capturedAt = 0L
    )

    private fun complete(): ReportDetail {
        val measured = TechnicalInput(
            phaseType = PhaseType.SINGLE,
            amperage = listOf("32", "", ""),
            voltage = listOf("230", "", "")
        ).applyTo(blank)
        return ReportDetail(
            report = measured.copy(
                latitude = 33.6,
                longitude = 46.4,
                description = "شرح بازدید"
            ),
            media = listOf(photo())
        )
    }

    @Test
    fun `a finished visit has nothing missing`() {
        assertTrue(Completion.isComplete(complete()))
        assertEquals(emptyList<Int>(), Completion.missing(complete()))
    }

    @Test
    fun `missing coordinates are reported`() {
        val detail = complete().let {
            it.copy(report = it.report.copy(latitude = null, longitude = null))
        }
        assertFalse(Completion.isComplete(detail))
        assertTrue(Completion.missing(detail).contains(R.string.missing_gps))
    }

    @Test
    fun `a visit with no photo is not finished`() {
        val detail = complete().copy(media = emptyList())
        assertTrue(Completion.missing(detail).contains(R.string.missing_photo))
    }

    @Test
    fun `a visit with no measurement is not finished`() {
        val detail = complete().let {
            it.copy(report = it.report.copy(amperageR = null, voltageR = null, totalWatt = null))
        }
        assertTrue(Completion.missing(detail).contains(R.string.missing_measurement))
    }

    @Test
    fun `an empty description counts as missing, not as an empty description`() {
        val detail = complete().let { it.copy(report = it.report.copy(description = "   ")) }
        assertTrue(Completion.missing(detail).contains(R.string.missing_description))
    }

    @Test
    fun `everything missing is listed at once, so one round of fixes is enough`() {
        val detail = ReportDetail(report = blank)
        assertEquals(4, Completion.missing(detail).size)
    }
}
