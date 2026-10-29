package ir.minerfinder.ui.search

import ir.minerfinder.R

/**
 * Labels for the raw values sources report. A key missing here is shown as it
 * is, so a new source still displays its numbers.
 */
object RawLabels {
    class Label(val text: Int, val unit: Int?, val decimals: Int)

    private val known = mapOf(
        "f0Hz" to Label(R.string.raw_f0, R.string.unit_hz, 1),
        "partials" to Label(R.string.raw_partials, null, 0),
        "stability" to Label(R.string.raw_stability, R.string.unit_percent, 0),
        "levelDbfs" to Label(R.string.raw_level, R.string.unit_dbfs, 1),
        "deltaUt" to Label(R.string.raw_delta, R.string.unit_ut, 1),
        "magnitudeUt" to Label(R.string.raw_magnitude, R.string.unit_ut, 1),
        "rateHz" to Label(R.string.raw_rate, R.string.unit_hz, 0),
        "ac50Ut" to Label(R.string.raw_ac50, R.string.unit_ut, 2),
        "levelDb" to Label(R.string.raw_coil_level, R.string.unit_db, 1),
        "variation" to Label(R.string.raw_variation, R.string.unit_percent, 0),
        "harmonicRatio" to Label(R.string.raw_harmonic_ratio, null, 2),
    )

    fun of(key: String): Label? = known[key]
}
