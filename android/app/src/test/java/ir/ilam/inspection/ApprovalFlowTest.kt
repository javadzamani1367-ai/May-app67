package ir.ilam.inspection

import ir.ilam.inspection.data.model.ApprovalState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The approval cycle's rules. These decide whether a case can be edited and
 * whether it can be filed for good, so they are worth pinning down: a wrong
 * answer here either freezes an expert out of their own work or lets a case
 * be archived that nobody approved.
 */
class ApprovalFlowTest {

    @Test
    fun `codes are stable, because they are stored`() {
        assertEquals(0, ApprovalState.DRAFT.code)
        assertEquals(1, ApprovalState.PENDING.code)
        assertEquals(2, ApprovalState.APPROVED.code)
        assertEquals(3, ApprovalState.RETURNED.code)
    }

    @Test
    fun `an unknown or missing code reads as a draft, never as approved`() {
        assertEquals(ApprovalState.DRAFT, ApprovalState.of(null))
        assertEquals(ApprovalState.DRAFT, ApprovalState.of(99))
        assertEquals(ApprovalState.DRAFT, ApprovalState.of(-1))
    }

    @Test
    fun `a case under review is frozen`() {
        assertFalse(ApprovalState.PENDING.editable)
    }

    @Test
    fun `an approved case is closed to further editing`() {
        assertFalse(ApprovalState.APPROVED.editable)
    }

    @Test
    fun `a draft and a returned case are both open for work`() {
        assertTrue(ApprovalState.DRAFT.editable)
        assertTrue(ApprovalState.RETURNED.editable)
    }
}
