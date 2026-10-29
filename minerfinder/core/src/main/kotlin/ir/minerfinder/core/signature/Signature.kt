package ir.minerfinder.core.signature

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** A recorded miner sound: what the library stores and inspectors exchange. */
data class Signature(
    val id: String,
    val label: String,
    val model: String,
    val note: String,
    val createdAtMs: Long,
    val fundamentalHz: Double?,
    val harmonicsHz: List<Double>,
    val bands: DoubleArray,
)

/**
 * Spectral shape as a fixed-length vector: [BANDS] log-spaced bands from 100
 * to 5000 Hz, each the mean of (dB − floor) clamped at zero. Fixed length and
 * log spacing make it independent of FFT size, and the floor subtraction makes
 * it independent of distance and gain, so cosine similarity compares shapes.
 */
object SignatureFeatures {
    const val BANDS = 96
    const val MIN_HZ = 100.0
    const val MAX_HZ = 5000.0

    fun bands(db: FloatArray, floorDb: Double, firstHz: Double, binHz: Double): DoubleArray {
        val sums = DoubleArray(BANDS)
        val counts = IntArray(BANDS)
        val logMin = ln(MIN_HZ)
        val logSpan = ln(MAX_HZ) - logMin
        for (i in db.indices) {
            val f = firstHz + i * binHz
            if (f < MIN_HZ || f >= MAX_HZ) continue
            val band = ((ln(f) - logMin) / logSpan * BANDS).toInt().coerceIn(0, BANDS - 1)
            sums[band] += (db[i] - floorDb).coerceAtLeast(0.0)
            counts[band]++
        }
        // Low bands may hold no bin at all; borrow the nearest filled one.
        val out = DoubleArray(BANDS) { if (counts[it] > 0) sums[it] / counts[it] else Double.NaN }
        for (i in out.indices) if (out[i].isNaN()) out[i] = nearest(out, i)
        return out
    }

    fun cosine(a: DoubleArray, b: DoubleArray): Double {
        if (a.size != b.size || a.isEmpty()) return 0.0
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]
        }
        return if (na > 0 && nb > 0) (dot / sqrt(na * nb)).coerceIn(0.0, 1.0) else 0.0
    }

    /**
     * Match 0..100: the cosine of the shapes, pulled down when both sides have
     * a fundamental and they disagree (same fan model, same speed).
     */
    fun match(current: DoubleArray, currentF0: Double?, signature: Signature): Double {
        val shape = cosine(current, signature.bands)
        val f0 = signature.fundamentalHz
        val pitch = if (currentF0 != null && f0 != null && f0 > 0) {
            val d = (currentF0 - f0) / f0
            exp(-(d * d) / (2 * 0.05 * 0.05))
        } else 1.0
        return 100.0 * shape * (0.6 + 0.4 * pitch)
    }

    private fun nearest(values: DoubleArray, i: Int): Double {
        for (d in 1 until values.size) {
            val r = values.getOrNull(i + d)
            if (r != null && !r.isNaN()) return r
            val l = values.getOrNull(i - d)
            if (l != null && !l.isNaN()) return l
        }
        return 0.0
    }
}

/** Accumulates frames while recording a signature (30 s next to a known miner). */
class SignatureRecorder {
    private var sum: DoubleArray? = null
    private var count = 0
    private val fundamentals = ArrayList<Double>()
    private val harmonics = ArrayList<List<Double>>()

    val frames: Int get() = count

    @Synchronized
    fun add(bands: DoubleArray, fundamentalHz: Double?, harmonicsHz: List<Double>) {
        val s = sum ?: DoubleArray(bands.size).also { sum = it }
        for (i in s.indices) s[i] += bands[i]
        count++
        if (fundamentalHz != null) {
            fundamentals += fundamentalHz
            harmonics += harmonicsHz
        }
    }

    @Synchronized
    fun build(id: String, label: String, model: String, note: String, nowMs: Long): Signature? {
        val s = sum ?: return null
        if (count == 0) return null
        val f0 = fundamentals.sorted().getOrNull(fundamentals.size / 2)
        // Harmonics of the frame whose f0 is the median, so the list is one coherent series.
        val idx = fundamentals.indexOfFirst { it == f0 }
        val partials = if (idx >= 0) harmonics[idx] else emptyList()
        return Signature(id, label, model, note, nowMs, f0, partials, DoubleArray(s.size) { s[it] / count })
    }
}
