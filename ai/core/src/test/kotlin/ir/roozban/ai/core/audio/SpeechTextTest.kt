package ir.roozban.ai.core.audio

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SpeechTextTest {
    @Test
    fun `sentences become chunks and digits become ascii`() {
        val chunks = SpeechText.chunks("فردا ساعت ۹ جلسه داری. سه کار مانده!\nخرید نان؟ **مهم**")
        assertThat(chunks).containsExactly("فردا ساعت 9 جلسه داری.", "سه کار مانده!", "خرید نان؟", "مهم").inOrder()
    }

    @Test
    fun `a page without punctuation is cut near the limit at commas or spaces`() {
        val long = (1..200).joinToString(" ") { "کلمه$it" } + "، پایان"
        val chunks = SpeechText.chunks(long, max = 100)
        assertThat(chunks.size).isGreaterThan(5)
        chunks.forEach { assertThat(it.length).isAtMost(100) }
        assertThat(chunks.joinToString(" ").replace(" ", "")).isEqualTo(SpeechText.normalize(long).replace(" ", ""))
    }

    @Test
    fun `blank and symbol-only text says nothing`() {
        assertThat(SpeechText.chunks("  \n **  ")).isEmpty()
        assertThat(SpeechText.normalize("سلام 👋 دنیا")).isEqualTo("سلام دنیا")
    }
}
