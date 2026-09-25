package ir.roozban.ai.core.audio

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class AudioTest {
    private val rate = 16_000
    private val frame = 480 // 30 ms

    private fun noise(ms: Int, amplitude: Double, seed: Int = 1): ShortArray {
        val r = Random(seed)
        return ShortArray(rate * ms / 1000) { ((r.nextDouble() * 2 - 1) * amplitude * 32767).toInt().toShort() }
    }

    /** A voiced-sounding tone on top of the noise. */
    private fun speech(ms: Int, amplitude: Double = 0.3, background: Double = 0.003): ShortArray {
        val n = noise(ms, background, seed = 7)
        return ShortArray(n.size) { i -> (n[i] + amplitude * 32767 * sin(2 * PI * 220 * i / rate) * (0.6 + 0.4 * sin(2 * PI * 3 * i / rate))).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun run(vad: Vad, vararg parts: ShortArray): Pair<Vad.State, Int> {
        val all = parts.reduce { a, b -> a + b }
        var i = 0
        while (i + frame <= all.size) {
            if (vad.feed(all.copyOfRange(i, i + frame)) == Vad.State.DONE) return vad.state to i
            i += frame
        }
        return vad.state to i
    }

    @Test
    fun `stops after a pause following speech`() {
        val vad = Vad()
        val (state, at) = run(vad, noise(600, 0.003), speech(2000), noise(3000, 0.003))
        assertThat(state).isEqualTo(Vad.State.DONE)
        assertThat(vad.endReason).isEqualTo(Vad.EndReason.SILENCE_AFTER_SPEECH)
        assertThat(vad.heardSpeech).isTrue()
        // 600 ms lead-in + 2 s speech + ~1.2 s silence
        assertThat(at / (rate / 1000)).isIn(com.google.common.collect.Range.closed(3600, 4100))
    }

    @Test
    fun `short pauses inside speech do not stop it`() {
        val vad = Vad()
        val (state, _) = run(vad, noise(300, 0.003), speech(1000), noise(500, 0.003), speech(1000), noise(400, 0.003))
        assertThat(state).isEqualTo(Vad.State.SPEAKING)
    }

    @Test
    fun `gives up when nobody talks`() {
        val vad = Vad(noSpeechTimeoutMs = 3000)
        val (state, _) = run(vad, noise(5000, 0.003))
        assertThat(state).isEqualTo(Vad.State.DONE)
        assertThat(vad.endReason).isEqualTo(Vad.EndReason.NO_SPEECH)
        assertThat(vad.heardSpeech).isFalse()
    }

    @Test
    fun `a noisy room needs a louder voice`() {
        val vad = Vad(noSpeechTimeoutMs = 3000)
        // Steady fan noise at the level of quiet speech is learned as the floor.
        val (state, _) = run(vad, noise(4000, 0.08))
        assertThat(vad.heardSpeech).isFalse()
        assertThat(state).isEqualTo(Vad.State.DONE)
        val loud = Vad()
        run(loud, noise(600, 0.03), speech(1500, amplitude = 0.5, background = 0.03), noise(2000, 0.03))
        assertThat(loud.heardSpeech).isTrue()
    }

    @Test
    fun `caps the length`() {
        val vad = Vad(maxMs = 2000)
        val (state, _) = run(vad, speech(5000))
        assertThat(state).isEqualTo(Vad.State.DONE)
        assertThat(vad.endReason).isEqualTo(Vad.EndReason.MAX_LENGTH)
    }

    @Test
    fun `wav and raw round trip, trim cuts silence`(@org.junit.jupiter.api.io.TempDir dir: File) {
        val s = noise(500, 0.001) + speech(800) + noise(700, 0.001)
        assertThat(Pcm.fromWav(Pcm.toWav(s))).isEqualTo(s)
        val f = File(dir, "a.pcm")
        Pcm.writeRaw(f, s)
        assertThat(Pcm.readRaw(f)).isEqualTo(s)
        val t = Pcm.trim(s)
        assertThat(t.size / 16).isIn(com.google.common.collect.Range.closed(1100, 1300)) // 800 ms + 2×200 ms padding
        assertThat(Pcm.trim(noise(500, 0.001))).isEmpty()
    }

    @Test
    fun `transcript cleanup`() {
        assertThat(Transcript.clean(" [موسیقی] فردا ساعت ۹ جلسه  داريم. ")).isEqualTo("فردا ساعت ۹ جلسه داریم")
        assertThat(Transcript.clean("(خنده) خرید نان…")).isEqualTo("خرید نان")
    }
}
