package ir.ilam.inspection

import ir.ilam.inspection.data.model.DispatchChannel
import ir.ilam.inspection.data.model.DispatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a dispatch is late. This is computed rather than stored, so it is
 * the arithmetic here — not a column somebody has to remember to update —
 * that the manager's overdue counts depend on.
 */
class DispatchStatusTest {

    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun `a deadline in the past with no answer is overdue`() {
        assertTrue(DispatchStatus.SENT.isOverdue(now - hour, now))
        assertTrue(DispatchStatus.SEEN.isOverdue(now - hour, now))
    }

    @Test
    fun `an answered dispatch is never overdue, however late the answer was`() {
        assertFalse(DispatchStatus.ANSWERED.isOverdue(now - 10_000 * hour, now))
    }

    @Test
    fun `no deadline means nothing to be late for`() {
        assertFalse(DispatchStatus.SENT.isOverdue(null, now))
    }

    @Test
    fun `a deadline still ahead is not overdue`() {
        assertFalse(DispatchStatus.SENT.isOverdue(now + hour, now))
    }

    @Test
    fun `the deadline instant itself has not yet passed`() {
        // Strictly less than: a unit answering on the last second is in time.
        assertFalse(DispatchStatus.SENT.isOverdue(now, now))
    }

    @Test
    fun `stored codes stay put on both enums`() {
        assertEquals(0, DispatchStatus.SENT.code)
        assertEquals(1, DispatchStatus.SEEN.code)
        assertEquals(2, DispatchStatus.ANSWERED.code)
        assertEquals(0, DispatchChannel.SYSTEM.code)
        assertEquals(1, DispatchChannel.SOCIAL.code)
        assertEquals(2, DispatchChannel.OFFLINE_PACKAGE.code)
        assertEquals(DispatchStatus.SENT, DispatchStatus.of(null))
        assertEquals(DispatchChannel.SYSTEM, DispatchChannel.of(42))
    }
}
