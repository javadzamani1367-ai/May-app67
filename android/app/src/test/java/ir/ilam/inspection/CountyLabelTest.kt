package ir.ilam.inspection

import ir.ilam.inspection.util.CountyLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How a county and its area read together.
 *
 * The rule lives in one place because the screens and the exporters both use
 * it: a case whose PDF said a different thing from the screen it was made
 * from would be the kind of disagreement nobody notices until a unit asks.
 */
class CountyLabelTest {

    private val format: (String, String) -> String = { name, area -> "$name — ناحیه $area" }

    private fun label(county: String?, area: String?) = CountyLabel.of(county, area, format)

    @Test
    fun `names the area beside the county`() {
        assertEquals("ایلام — ناحیه ۴۰۱", label("ایلام", "401"))
        assertEquals("ایلام — ناحیه ۴۰۲", label("ایلام", "402"))
    }

    /** The two areas of Ilam are the whole reason this exists. */
    @Test
    fun `tells the two areas of Ilam apart`() {
        assertEquals(2, setOf(label("ایلام", "401"), label("ایلام", "402")).size)
    }

    /**
     * A case filed before the area was recorded has none. It is written as the
     * plain county name — an official form must not carry an empty «ناحیه».
     */
    @Test
    fun `a case with no area is written as the county alone`() {
        assertEquals("مهران", label("مهران", null))
        assertEquals("مهران", label("مهران", ""))
        assertEquals("مهران", label("مهران", "   "))
    }

    @Test
    fun `no county means no label at all`() {
        assertNull(label(null, "401"))
        assertNull(label("", "401"))
        assertNull(label("   ", "401"))
    }

    /** Area codes are stored in latin digits but may arrive shaped for display. */
    @Test
    fun `accepts persian digits in the area code`() {
        assertEquals("ایلام — ناحیه ۴۰۱", label("ایلام", "۴۰۱"))
    }

    @Test
    fun `trims the county and ignores stray characters in the area`() {
        assertEquals("ایلام — ناحیه ۴۰۱", label("  ایلام  ", "ناحیه 401"))
    }
}
