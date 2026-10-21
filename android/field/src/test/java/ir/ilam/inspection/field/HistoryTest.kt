package ir.ilam.inspection.field

import ir.ilam.inspection.field.data.History
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The comparison box marks the same rises the server does (FieldHistory::isRise). */
class HistoryTest {

    @Test
    fun plateIsTheServersKey() {
        assertEquals("77100", History.plateKey("۷۷-۱۰۰"))
        assertEquals("77100", History.plateKey(" 77 100 "))
        assertEquals("P5521", History.plateKey("p-5521"))
    }

    @Test
    fun riseAgainstLastOrAverage() {
        assertTrue(History.isRise(160.0, listOf(105.0, 100.0), 30, 5))
        assertFalse(History.isRise(105.0, listOf(100.0, 100.0), 30, 5))
        // Average of earlier visits counts too, even if the last one was already high.
        assertTrue(History.isRise(150.0, listOf(145.0, 100.0, 100.0, 100.0, 100.0), 30, 5))
    }

    @Test
    fun lowLoadNeedsTheAmpFloor() {
        assertFalse(History.isRise(4.0, listOf(2.0), 30, 5))
        assertFalse(History.isRise(150.0, emptyList(), 30, 5))
    }

    @Test
    fun changeNeedsBothValues() {
        assertEquals(50.0, History.changePct(150.0, 100.0)!!, 0.001)
        assertNull(History.changePct(150.0, null))
        assertNull(History.changePct(150.0, 0.0))
    }
}
