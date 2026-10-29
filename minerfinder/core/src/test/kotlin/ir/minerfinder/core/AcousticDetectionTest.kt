package ir.minerfinder.core

import ir.minerfinder.core.acoustic.AcousticAnalyzer
import ir.minerfinder.core.acoustic.AcousticConfig
import ir.minerfinder.core.acoustic.AcousticFrame
import ir.minerfinder.core.dsp.HarmonicDetector
import ir.minerfinder.core.dsp.Peak
import ir.minerfinder.core.dsp.SlidingWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcousticDetectionTest {

    private val config = AcousticConfig()

    /** Feeds [seconds] of [signal] through the analyzer the way the app does: 4800-sample chunks, 8192/4096 windows. */
    private fun run(seconds: Int, analyzer: AcousticAnalyzer = AcousticAnalyzer(config), signal: (Int) -> FloatArray): List<AcousticFrame> {
        val frames = ArrayList<AcousticFrame>()
        val window = SlidingWindow(config.fftSize, config.hopSize)
        var samples = 0L
        repeat(seconds * 10) {
            window.push(signal(4800)) { w ->
                frames += analyzer.process(w, samples * 1000 / config.sampleRate)
            }
            samples += 4800
        }
        return frames
    }

    private val minerTones = listOf(600.0 to 0.10, 1200.0 to 0.07, 1800.0 to 0.05, 2400.0 to 0.03)

    @Test
    fun detectsA600HzFanWithHarmonicsInNoise() {
        val signals = Signals()
        val frames = run(25) { signals.tones(it, minerTones, noise = 0.2) }
        val last = frames.last()
        assertNotNull(last.series)
        assertEquals(600.0, last.series!!.fundamentalHz, 2.0)
        assertTrue("partials ${last.series!!.count}", last.series!!.count >= 4)
        assertTrue("stability ${last.stability.score}", last.stability.score > 0.9)
        assertTrue("score ${last.score}", last.score > 75)
        assertTrue("confidence ${last.confidence}", last.confidence > 0.95)
    }

    @Test
    fun whiteNoiseIsNotAMiner() {
        val signals = Signals(seed = 7)
        val frames = run(25) { signals.noise(it, 0.2) }
        val withSeries = frames.count { it.series != null }
        assertTrue("series found in $withSeries of ${frames.size} frames", withSeries <= frames.size / 20)
        assertTrue("score ${frames.last().score}", frames.last().score < 15)
        assertTrue(frames.all { it.score < 30 })
    }

    @Test
    fun aSingleToneWithoutHarmonicsIsNotASeries() {
        val signals = Signals()
        val frames = run(10) { signals.tones(it, listOf(700.0 to 0.1), 0.05) }
        assertNull(frames.last().series)
        assertTrue(frames.last().score < 30)
    }

    @Test
    fun aMissingFundamentalStillFindsTheSeries() {
        val signals = Signals()
        val tones = listOf(1000.0 to 0.06, 1500.0 to 0.05, 2000.0 to 0.04, 2500.0 to 0.03)
        val frames = run(12) { signals.tones(it, tones, 0.1) }
        assertEquals(500.0, frames.last().series!!.fundamentalHz, 2.0)
    }

    @Test
    fun aGlidingToneIsNotStable() {
        val signals = Signals()
        var t = 0.0
        val frames = run(25) {
            t += it / 48_000.0
            // A siren-like sweep: f0 moves 600 → 900 Hz over and over.
            val f0 = 600.0 + 300.0 * ((t / 6.0) % 1.0)
            signals.tones(it, listOf(f0 to 0.1, 2 * f0 to 0.07, 3 * f0 to 0.05), 0.05)
        }
        assertTrue("stability ${frames.last().stability.score}", frames.last().stability.score < 0.3)
    }

    @Test
    fun calibrationRemovesAConstantAmbientHum() {
        val signals = Signals()
        val hum = listOf(450.0 to 0.1, 900.0 to 0.07, 1350.0 to 0.05)
        val analyzer = AcousticAnalyzer(config)
        analyzer.startCalibration()
        run(5, analyzer) { signals.tones(it, hum, 0.05) }
        assertNotNull(analyzer.finishCalibration())
        val after = run(25, analyzer) { signals.tones(it, hum, 0.05) }
        assertTrue("score ${after.last().score}", after.last().score < 30)
        assertTrue(after.last().calibrated)
    }

    @Test
    fun harmonicDetectorPrefersTheTrueFundamental() {
        val peaks = listOf(600.0, 1200.0, 1800.0, 2400.0, 3000.0).map { Peak(it, -20.0, 30.0) }
        val series = HarmonicDetector().detect(peaks)
        assertEquals(600.0, series!!.fundamentalHz, 0.5)
        assertEquals(1.0, series.coverage, 1e-9)
    }
}
