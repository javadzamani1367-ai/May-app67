package ir.roozban.ai.core.audio

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PieceTranscriberTest {
    private val rate = 16_000

    /** Loud first half, a quiet gap at 60 %, loud rest. */
    private fun piece(seconds: Int): ShortArray = ShortArray(rate * seconds) { i ->
        val t = i / rate.toDouble()
        if (t in seconds * 0.58..seconds * 0.62) 0 else ((i % 40) * 500 - 10_000).toShort()
    }

    @Test
    fun `a failed attempt is tried again`() = runTest {
        var calls = 0
        val t = PieceTranscriber({ if (++calls == 1) error("busy") else "سلام دنیا" })
        assertThat(t(piece(2))).isEqualTo("سلام دنیا")
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun `too little text for a long piece is redone in two halves, split at the pause`() = runTest {
        val sizes = mutableListOf<Int>()
        val t = PieceTranscriber({ s ->
            sizes += s.size
            if (s.size == rate * 10) "فقط اول" else "بخش کامل با کلمه‌های بیشتر از قبل"
        })
        val text = t(piece(10))
        assertThat(text).isEqualTo("بخش کامل با کلمه‌های بیشتر از قبل بخش کامل با کلمه‌های بیشتر از قبل")
        // Split inside the quiet gap (58–62 %).
        assertThat(sizes[1] / rate.toDouble()).isWithin(0.3).of(6.0)
    }

    @Test
    fun `enough text is kept as it is`() = runTest {
        var calls = 0
        val long = "این یک جملهٔ نسبتاً طولانی است که برای ده ثانیه صحبت کافی به نظر می‌رسد و ادامه دارد"
        val t = PieceTranscriber({ calls++; long })
        assertThat(t(piece(10))).isEqualTo(long)
        assertThat(calls).isEqualTo(1)
    }
}
