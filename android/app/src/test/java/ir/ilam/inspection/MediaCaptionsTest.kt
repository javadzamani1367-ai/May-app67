package ir.ilam.inspection

import ir.ilam.inspection.data.model.MediaCaptions
import ir.ilam.inspection.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The standard photo and video captions. An expert should be offered the
 * slots nobody has used yet, and should never be offered one twice — two
 * photos labelled "تصویر ترموویژن" in one case make the report ambiguous.
 */
class MediaCaptionsTest {

    private val standard = listOf("تصویر کلی ملک", "تصویر پلمب کنتور", "تصویر ترموویژن")

    @Test
    fun `an unused list offers everything`() {
        assertEquals(standard, MediaCaptions.available(standard, emptyList()))
    }

    @Test
    fun `a caption already used is not offered again`() {
        val left = MediaCaptions.available(standard, listOf("تصویر ترموویژن"))
        assertFalse(left.contains("تصویر ترموویژن"))
        assertEquals(2, left.size)
    }

    @Test
    fun `free text the expert typed does not remove a standard slot`() {
        val left = MediaCaptions.available(standard, listOf("عکس از سمت کوچه"))
        assertEquals(standard, left)
    }

    @Test
    fun `nulls among the used captions are ignored rather than matching`() {
        val left = MediaCaptions.available(standard, listOf(null, null))
        assertEquals(standard, left)
    }

    @Test
    fun `the limits are the ones the field work was specified with`() {
        assertEquals(30, MediaCaptions.MAX_PHOTOS)
        assertEquals(10, MediaCaptions.MAX_VIDEOS)
        assertEquals(30, MediaCaptions.limitFor(MediaType.IMAGE))
        assertEquals(10, MediaCaptions.limitFor(MediaType.VIDEO))
    }

    @Test
    fun `when every standard caption is used the list empties rather than repeating`() {
        assertTrue(MediaCaptions.available(standard, standard).isEmpty())
    }
}
