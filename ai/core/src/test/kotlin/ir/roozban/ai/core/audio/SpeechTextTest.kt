package ir.roozban.ai.core.audio

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SpeechTextTest {
    @Test
    fun `sentences become chunks and digits become ascii`() {
        val chunks = SpeechText.chunks("فردا ساعت ۹ جلسه داری. سه کار مانده!\nخرید نان؟ **مهم**")
        assertThat(chunks).containsExactly("فردا ساعت نه جلسه داری.", "سه کار مانده!", "خرید نان؟", "مهم").inOrder()
    }

    @Test
    fun `a page without punctuation is cut near the limit at commas or spaces`() {
        val long = (1..200).joinToString(" ") { "کلمه$it" } + "، پایان"
        val chunks = SpeechText.chunks(long, max = 100)
        assertThat(chunks.size).isGreaterThan(5)
        chunks.forEach { assertThat(it.length).isAtMost(100) }
        assertThat(chunks.joinToString(" ").replace(" ", "")).isEqualTo(SpeechText.spellNumbers(SpeechText.normalize(long)).replace(" ", ""))
    }

    @Test
    fun `blank and symbol-only text says nothing`() {
        assertThat(SpeechText.chunks("  \n **  ")).isEmpty()
        assertThat(SpeechText.normalize("سلام 👋 دنیا")).isEqualTo("سلام دنیا")
    }

    @Test
    fun `numbers, times and percentages are read as words`() {
        assertThat(SpeechText.words(0)).isEqualTo("صفر")
        assertThat(SpeechText.words(17)).isEqualTo("هفده")
        assertThat(SpeechText.words(120)).isEqualTo("صد و بیست")
        assertThat(SpeechText.words(1405)).isEqualTo("هزار و چهارصد و پنج")
        assertThat(SpeechText.words(2_500_000)).isEqualTo("دو میلیون و پانصد هزار")
        assertThat(SpeechText.spellNumbers(SpeechText.normalize("ساعت ۰۹:۳۰ و ۱۷:۰۰"))).isEqualTo("ساعت نه و سی دقیقه و هفده")
        assertThat(SpeechText.spellNumbers(SpeechText.normalize("۲۵٪ پیشرفت"))).isEqualTo("بیست و پنج درصد پیشرفت")
        assertThat(SpeechText.spellNumbers("3.5 کیلو")).isEqualTo("سه ممیز پنج کیلو")
    }
}
