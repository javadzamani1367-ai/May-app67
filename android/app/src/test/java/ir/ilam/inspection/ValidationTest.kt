package ir.ilam.inspection

import ir.ilam.inspection.data.model.Completion
import ir.ilam.inspection.util.PersianNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {

    @Test
    fun `validates national id checksums`() {
        assertTrue(Completion.isValidNationalId("0499370899"))
        assertTrue(Completion.isValidNationalId("0084575948"))
        assertFalse(Completion.isValidNationalId("0499370898"))
        assertFalse(Completion.isValidNationalId("1111111111"))
        assertFalse(Completion.isValidNationalId("12345"))
        assertFalse(Completion.isValidNationalId(null))
    }

    @Test
    fun `numbers survive the round trip through persian shaping`() {
        assertEquals("۱۲۳۴", PersianNumbers.toPersian("1234"))
        assertEquals("1234", PersianNumbers.toLatin("۱۲۳۴"))
        assertEquals("1234", PersianNumbers.toLatin("١٢٣٤"))
        assertEquals(63.0, PersianNumbers.parseDoubleOrNull("۶۳")!!, 0.001)
        assertEquals("۶۳", PersianNumbers.toPersian(63.0))
        assertEquals("۱۲٬۵۰۰", PersianNumbers.grouped(12_500.0))
    }
}
