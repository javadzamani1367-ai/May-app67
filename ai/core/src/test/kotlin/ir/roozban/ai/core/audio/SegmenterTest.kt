package ir.roozban.ai.core.audio

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class SegmenterTest {
    private val rate = 16_000
    private val frame = 480

    private fun noise(ms: Int, amplitude: Double = 0.003, seed: Int = 1): ShortArray {
        val r = Random(seed)
        return ShortArray(rate * ms / 1000) { ((r.nextDouble() * 2 - 1) * amplitude * 32767).toInt().toShort() }
    }

    private fun speech(ms: Int, amplitude: Double = 0.3): ShortArray {
        val n = noise(ms, seed = 7)
        return ShortArray(n.size) { i -> (n[i] + amplitude * 32767 * sin(2 * PI * 220 * i / rate)).toInt().coerceIn(-32768, 32767).toShort() }
    }

    /** Pieces (in seconds) the segmenter produces for the audio, plus the flushed rest. */
    private fun pieces(vararg parts: ShortArray): List<Double> {
        val s = Segmenter()
        val all = parts.reduce { a, b -> a + b }
        val out = mutableListOf<Double>()
        var i = 0
        while (i + frame <= all.size) {
            s.feed(all.copyOfRange(i, i + frame))?.let { out += it.size / rate.toDouble() }
            i += frame
        }
        s.flush()?.let { out += it.size / rate.toDouble() }
        return out
    }

    @Test
    fun `sentences split at pauses, silence between them is dropped`() {
        val out = pieces(noise(2000), speech(4000), noise(1500), speech(3000), noise(20_000), speech(5000))
        assertThat(out).hasSize(3)
        out.forEach { assertThat(it).isLessThan(6.0) }
    }

    @Test
    fun `short breaths inside a sentence do not split it`() {
        val out = pieces(noise(300), speech(1500), noise(400), speech(1500), noise(1500))
        assertThat(out).hasSize(1)
    }

    /** Talking on and on: syllables with only 100 ms gaps, never a real pause. */
    private fun rapidSpeech(ms: Int): ShortArray = (0 until ms / 400).map { speech(300) + noise(100, seed = it) }.reduce { a, b -> a + b }

    @Test
    fun `speech without pauses is still cut into pieces`() {
        val out = pieces(rapidSpeech(70_000))
        assertThat(out.size).isAtLeast(5)
        out.forEach { assertThat(it).isAtMost(14.5) }
        assertThat(out.sum()).isGreaterThan(65.0)
    }

    @Test
    fun `silence alone gives nothing`() {
        assertThat(pieces(noise(60_000))).isEmpty()
    }

    /**
     * A room with echo: between words the level only drops to a hum, never to the quiet of the
     * room. Minutes of this must not raise the noise floor into the voice, or a softer sentence
     * afterwards would be taken for silence and lost.
     */
    @Test
    fun `a long stretch of talking does not make later speech count as silence`() {
        val talking = (0 until 150).map { speech(300) + noise(100, amplitude = 0.03, seed = it) }.reduce { a, b -> a + b }
        val soft = speech(4000, amplitude = 0.04)
        val out = pieces(noise(1000), talking, noise(1200), soft, noise(1500))
        // Everything said is kept: 60 s of talking plus the 4 s sentence.
        assertThat(out.sum()).isGreaterThan(63.0)
    }

    @Test
    fun `pieces stay short so text shows soon and the model does not stop early`() {
        val out = pieces(rapidSpeech(70_000))
        out.forEach { assertThat(it).isAtMost(15.5) }
    }
}
