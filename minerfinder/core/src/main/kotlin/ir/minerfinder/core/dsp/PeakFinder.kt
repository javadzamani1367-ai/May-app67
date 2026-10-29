package ir.minerfinder.core.dsp

/** A tonal peak. [prominenceDb] is its height above the noise floor. */
data class Peak(val freqHz: Double, val levelDb: Double, val prominenceDb: Double)

/**
 * Finds tonal peaks: local maxima standing at least [minProminenceDb] above the
 * floor. With a Hann window a pure tone spans about four bins, so a peak must
 * also be the highest within ±[neighbourBins]; its frequency is refined by
 * parabolic interpolation on the dB values.
 */
class PeakFinder(
    var minProminenceDb: Double = 12.0,
    private val neighbourBins: Int = 2,
    private val maxPeaks: Int = 32,
) {
    fun find(db: DoubleArray, floorDb: Double, firstHz: Double, binHz: Double): List<Peak> {
        val peaks = ArrayList<Peak>()
        val threshold = floorDb + minProminenceDb
        for (i in 1 until db.size - 1) {
            val v = db[i]
            if (v < threshold) continue
            var isMax = true
            for (d in 1..neighbourBins) {
                val left = i - d
                val right = i + d
                if (left >= 0 && db[left] > v) { isMax = false; break }
                if (right < db.size && db[right] >= v) { isMax = false; break }
            }
            if (!isMax) continue
            val a = db[i - 1]
            val c = db[i + 1]
            val denom = a - 2 * v + c
            val delta = if (denom < 0) (0.5 * (a - c) / denom).coerceIn(-0.5, 0.5) else 0.0
            val level = v - 0.25 * (a - c) * delta
            peaks += Peak(firstHz + (i + delta) * binHz, level, level - floorDb)
        }
        return peaks.sortedByDescending { it.prominenceDb }.take(maxPeaks)
    }
}
