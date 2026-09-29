package ir.roozban.ai.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class SpellCorrectorTest {
    private fun gz(text: String): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(text.toByteArray()) } }.toByteArray()

    private val lexicon = PersianLexicon.read(
        gz(
            """
            قبض 20000
            برق 800
            را 90000
            تا 50000
            آخر 3000
            هفته 2000
            پرداخت 60000
            کنید 7000
            خواست 900
            خاست 300
            از 90000
            جا 1200
            برخاست 200
            او 20000
            می‌روم 400
            استعمارگران 50
            انگلیسی 900
            """.trimIndent(),
        ).inputStream(),
        gz(
            """
            قبض برق 120
            برق را 60
            از جا 30
            جا برخاست 20
            او خواست 40
            استعمارگران انگلیسی 10
            """.trimIndent(),
        ).inputStream(),
    )

    @Test
    fun `a word that does not exist becomes the nearest likely word`() {
        val c = SpellCorrector(lexicon)
        assertThat(c.correct("قبس برق را تا آخر هفته پرداخ کنید")).isEqualTo("قبض برق را تا آخر هفته پرداخت کنید")
        assertThat(c.correct("استعمارگرن انگلیسی")).isEqualTo("استعمارگران انگلیسی")
    }

    @Test
    fun `real words are left alone unless the context clearly wants a same-sounding one`() {
        val c = SpellCorrector(lexicon)
        assertThat(c.correct("او خواست")).isEqualTo("او خواست")
        assertThat(c.correct("غبض برق")).isEqualTo("قبض برق")
    }

    @Test
    fun `half-spaces follow the usual spelling`() {
        assertThat(SpellCorrector(lexicon).correct("میروم")).isEqualTo("می‌روم")
    }

    @Test
    fun `the user's own words and corrections win`() {
        val c = SpellCorrector(lexicon, personal = setOf("فبس"), learned = mapOf("پرداخ" to "پرداخت"))
        assertThat(c.correct("فبس پرداخ")).isEqualTo("فبس پرداخت")
    }

    @Test
    fun `an unknown word with nothing close and likely stays (a name, a rare word)`() {
        assertThat(SpellCorrector(lexicon).correct("پرستو آمد")).isEqualTo("پرستو آمد")
    }

    @Test
    fun `digits, punctuation and Latin stay as they are`() {
        assertThat(SpellCorrector(lexicon).correct("۹:۳۰، PDF را")).isEqualTo("۹:۳۰، PDF را")
    }
}
