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
            "M-401-050614-482917",
            TrackingCode.generate(ReportType.PUBLIC, "401", reportDate, "1234482917")
        )
    }

    /**
     * The area codes are fixed precisely so two experts cannot produce codes
     * that disagree. Truncating the segment to two digits put nine counties on
     * `40` and eight on `41`, which reintroduced the collision by the back
     * door: Ilam, Malekshahi and Chavar all issued the same code.
     */
    @Test
    fun `every official area code survives into the code`() {
        val official = listOf(
            "401", "402", "403", "404", "405", "406", "407", "408", "409",
            "410", "411", "412", "413", "414", "415", "416", "419"
        )
        val segments = official.map { code ->
            TrackingCode.generate(ReportType.PUBLIC, code, reportDate, "482917")!!
                .split(TrackingCode.SEPARATOR)[1]
        }
        assertEquals(official, segments)
        assertEquals(official.size, segments.toSet().size)
    }

    @Test
    fun `pads short subscription numbers`() {
        assertEquals(
            "N-07-050614-004829",
            TrackingCode.generate(ReportType.SYSTEM_121, "7", reportDate, "4829")
        )
    }

    /** A daily sequence keeps codes apart until the subscription arrives. */
    @Test
    fun `temporary codes count up within the day`() {
        val first = TrackingCode.temporary(ReportType.FIELD, "401", reportDate, 1)
        val second = TrackingCode.temporary(ReportType.FIELD, "401", reportDate, 2)
        assertEquals("F-401-050614-T0001", first)
        assertEquals("F-401-050614-T0002", second)
        assertTrue(TrackingCode.isTemporary(first))

        // Four digits, so a day of intake cannot run out of them, and the
        // sequence never spills into the segment beside it.
        assertEquals("F-401-050614-T9999", TrackingCode.temporary(ReportType.FIELD, "401", reportDate, 9999))
        assertEquals("F-401-050614-T9999", TrackingCode.temporary(ReportType.FIELD, "401", reportDate, 12345))
    }

    @Test
    fun `refuses to generate for the external system`() {
        assertNull(TrackingCode.generate(ReportType.SORAGH, "01", reportDate, "1234482917"))
    }

    @Test
    fun `falls back to a temporary code without a subscription number`() {
        assertNull(TrackingCode.generate(ReportType.PUBLIC, "401", reportDate, null))
        val temporary = TrackingCode.temporary(ReportType.PUBLIC, "401", reportDate, 3)
        assertEquals("M-401-050614-T0003", temporary)
        assertTrue(TrackingCode.isTemporary(temporary))
        assertFalse(TrackingCode.isTemporary("M-401-050614-482917"))
    }

    @Test
    fun `accepts persian digits typed by the expert`() {
        assertEquals(
            "M-401-050614-482917",
            TrackingCode.generate(ReportType.PUBLIC, "۴۰۱", reportDate, "۱۲۳۴۴۸۲۹۱۷")
        )
    }

    @Test
    fun `shapes codes for display only`() {
        assertEquals("M-۴۰۱-۰۵۰۶۱۴-۴۸۲۹۱۷", TrackingCode.forDisplay("M-401-050614-482917"))
        assertEquals("482917", PersianNumbers.toLatin("۴۸۲۹۱۷"))
    }
}
