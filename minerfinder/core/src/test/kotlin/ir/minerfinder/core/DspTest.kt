package ir.minerfinder.core

import ir.minerfinder.core.dsp.Fft
import ir.minerfinder.core.dsp.Goertzel
import ir.minerfinder.core.dsp.SlidingWindow
import ir.minerfinder.core.dsp.SpectrumAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class DspTest {

    @Test
    fun fftFindsTheToneBin() {
        val n = 1024
        val re = DoubleArray(n) { sin(2 * PI * 37 * it / n) }
        val im = DoubleArray(n)
        Fft(n).transform(re, im)
        val mags = DoubleArray(n / 2) { sqrt(re[it] * re[it] + im[it] * im[it]) }
        assertEquals(37, mags.indices.maxByOrNull { mags[it] })
        assertEquals(n / 2.0, mags[37], 1e-6)
        assertTrue(mags.indices.filter { it != 37 }.all { mags[it] < 1e-6 })
    }

    @Test
    fun fftKeepsEnergy() {
        val n = 256
        val random = java.util.Random(1)
        val x = DoubleArray(n) { random.nextGaussian() }
        val re = x.copyOf()
        val im = DoubleArray(n)
        Fft(n).transform(re, im)
        val time = x.sumOf { it * it }
        val freq = re.indices.sumOf { re[it] * re[it] + im[it] * im[it] } / n
        assertEquals(time, freq, 1e-9 * time)
    }

    @Test
    fun spectrumReadsSineAmplitudeSquared() {
        val analyzer = SpectrumAnalyzer(48_000, 8192, 100.0, 5000.0)
        // 1000 Hz is not on a bin (5.86 Hz spacing); Hann scalloping costs at most 1.42 dB.
        val samples = Signals().tones(8192, listOf(1000.0 to 0.5), 0.0)
        val db = SpectrumAnalyzer.toDb(analyzer.power(samples))
        val peak = db.max()
        assertEquals(SpectrumAnalyzer.toDb(0.25), peak, 1.5)
        val peakFreq = analyzer.frequencyOf(db.indices.maxByOrNull { db[it] }!!.toDouble())
        assertEquals(1000.0, peakFreq, analyzer.binHz)
    }

    @Test
    fun goertzelMeasuresMainsAndHarmonics() {
        val rate = 48_000
        val block = Signals(rate).tones(rate / 2, listOf(50.0 to 0.8, 150.0 to 0.2), 0.0)
        assertEquals(0.8, Goertzel.amplitude(block, rate.toDouble(), 50.0), 0.01)
        assertEquals(0.2, Goertzel.amplitude(block, rate.toDouble(), 150.0), 0.01)
        assertTrue(Goertzel.amplitude(block, rate.toDouble(), 250.0) < 0.01)
    }

    @Test
    fun goertzelIgnoresNoiseAtTheTarget() {
        val rate = 48_000
        val block = Signals(rate).noise(rate / 2, 0.3)
        // White noise of σ=0.3 over 24000 samples leaves ≈ 2σ/√N·√(π/4) ≈ 0.003.
        assertTrue(Goertzel.amplitude(block, rate.toDouble(), 50.0) < 0.02)
    }

    @Test
    fun slidingWindowHopsEvenly() {
        val window = SlidingWindow(8, 4)
        val seen = ArrayList<List<Float>>()
        val stream = FloatArray(20) { it.toFloat() }
        // Odd chunk sizes, so the window boundaries do not line up with them.
        var i = 0
        for (size in listOf(3, 5, 1, 7, 4)) {
            window.push(stream.copyOfRange(i, i + size)) { seen += it.toList() }
            i += size
        }
        assertEquals(4, seen.size)
        assertEquals((0..7).map { it.toFloat() }, seen[0])
        assertEquals((4..11).map { it.toFloat() }, seen[1])
        assertEquals((12..19).map { it.toFloat() }, seen[3])
        assertTrue(seen.all { abs(it.last() - it.first() - 7f) < 1e-6 })
    }
}
