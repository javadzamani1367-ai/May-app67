package ir.ilam.inspection

import ir.ilam.inspection.data.model.ReportType
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.TrackingCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingCodeTest {

    // The date segment of the documented example code, 050614.
    private val reportDate = PersianDate.toEpochMillis(1405, 6, 14)

    @Test
    fun `builds the documented code shape`() {
        assertEquals(
            "M-01-050614-482917",
            TrackingCode.generate(ReportType.PUBLIC, "01", reportDate, "1234482917")
        )
    }

    @Test
    fun `pads short subscription numbers`() {
        assertEquals(
            "N-07-050614-004829",
            TrackingCode.generate(ReportType.SYSTEM_121, "7", reportDate, "4829")
        )
    }

    @Test
    fun `refuses to generate for the external system`() {
        assertNull(TrackingCode.generate(ReportType.SORAGH, "01", reportDate, "1234482917"))
    }

    @Test
    fun `falls back to a temporary code without a subscription number`() {
        assertNull(TrackingCode.generate(ReportType.PUBLIC, "01", reportDate, null))
        val temporary = TrackingCode.temporary(ReportType.PUBLIC, "01", reportDate, 3)
        assertEquals("M-01-050614-T0003", temporary)
        assertTrue(TrackingCode.isTemporary(temporary))
        assertFalse(TrackingCode.isTemporary("M-01-050614-482917"))
    }

    @Test
    fun `accepts persian digits typed by the expert`() {
        assertEquals(
            "M-01-050614-482917",
            TrackingCode.generate(ReportType.PUBLIC, "۰۱", reportDate, "۱۲۳۴۴۸۲۹۱۷")
        )
    }

    @Test
    fun `shapes codes for display only`() {
        assertEquals("M-۰۱-۰۵۰۶۱۴-۴۸۲۹۱۷", TrackingCode.forDisplay("M-01-050614-482917"))
        assertEquals("482917", PersianNumbers.toLatin("۴۸۲۹۱۷"))
    }
}
