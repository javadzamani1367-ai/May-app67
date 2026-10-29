package ir.minerfinder.core

import ir.minerfinder.core.magnetic.CoilAnalyzer
import ir.minerfinder.core.magnetic.MagneticAnalyzer
import ir.minerfinder.core.signature.SignatureFeatures
import ir.minerfinder.core.signature.SignatureRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MagneticAndSignatureTest {

    @Test
    fun fastMagnetometerSees50Hz() {
        val analyzer = MagneticAnalyzer()
        val random = java.util.Random(3)
        var t = 0L
        // ~200 Hz with jitter, Earth field plus a 3 µT 50 Hz component on x.
        repeat(400) {
            t += 5_000_000L + random.nextInt(400_000) - 200_000
            val s = t / 1e9
            analyzer.add(t, 20 + 3 * sin(2 * PI * 50 * s), -5.0, 40.0)
        }
        val frame = analyzer.frame(2_000)!!
        assertEquals(200.0, frame.rateHz, 10.0)
        assertNotNull(frame.ac50Ut)
        assertEquals(3.0, frame.ac50Ut!!, 0.5)
        assertTrue(frame.score > 60)
    }

    @Test
    fun slowMagnetometerReportsNo50Hz() {
        val analyzer = MagneticAnalyzer()
        var t = 0L
        repeat(100) {
            t += 20_000_000L // 50 Hz sampling
            analyzer.add(t, 20.0, -5.0, 40.0)
        }
        val frame = analyzer.frame(2_000)!!
        assertNull(frame.ac50Ut)
        assertTrue(frame.score < 5)
    }

    @Test
    fun steadyHeavyLoadScoresAboveASwingingOne() {
        val steady = CoilAnalyzer()
        val swinging = CoilAnalyzer()
        val signals = Signals()
        val random = java.util.Random(5)
        var last = 0.0 to 0.0
        for (i in 0 until 40) {
            val ts = i * 500L
            val a = steady.process(signals.tones(steady.blockSize, listOf(50.0 to 0.05, 150.0 to 0.02), 0.001), ts)
            val level = 0.05 * (0.2 + 1.6 * random.nextDouble())
            val b = swinging.process(signals.tones(swinging.blockSize, listOf(50.0 to level), 0.001), ts)
            last = a.score to b.score
        }
        assertTrue("steady ${last.first} swinging ${last.second}", last.first > last.second + 15)
    }

    @Test
    fun signatureMatchesTheSameShape() {
        val first = 100.0
        val bin = 48_000.0 / 8192
        fun spectrum(vararg tones: Double) = FloatArray(840) { i ->
            val f = first + i * bin
            if (tones.any { kotlin.math.abs(it - f) < bin * 1.5 }) -20f else -60f
        }
        val miner = SignatureFeatures.bands(spectrum(600.0, 1200.0, 1800.0), -60.0, first, bin)
        val recorder = SignatureRecorder()
        repeat(10) { recorder.add(miner, 600.0, listOf(600.0, 1200.0, 1800.0)) }
        val signature = recorder.build("s1", "S19", "Antminer S19", "", 0)!!
        val same = SignatureFeatures.match(miner, 601.0, signature)
        val other = SignatureFeatures.bands(spectrum(430.0, 2900.0), -60.0, first, bin)
        val different = SignatureFeatures.match(other, 430.0, signature)
        assertTrue("same $same", same > 95)
        assertTrue("different $different", different < 40)
    }
}
