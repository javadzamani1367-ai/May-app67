package ir.minerfinder.core.engine

import ir.minerfinder.core.sensor.SourceReading

/** One source's part in a fused result. [share] is its fraction of the total effective weight. */
data class Contribution(
    val sourceId: String,
    val score: Float,
    val confidence: Float,
    val weight: Float,
    val share: Float,
)

/**
 * The fused result.
 *
 * @param probability 0..100, the "miner probability" shown on the search screen.
 * @param confidence 0..1, how much of the configured weight actually reported
 *   with confidence. A single shaky source gives a low value.
 */
data class Detection(
    val probability: Float,
    val confidence: Float,
    val contributions: List<Contribution>,
    val timestampMs: Long,
) {
    companion object {
        val EMPTY = Detection(0f, 0f, emptyList(), 0L)
    }
}

/**
 * Combines the latest reading of every active source.
 *
 * Each reading counts with weight × its own confidence, so a source that is
 * still warming up (or a slow magnetometer) cannot outvote a confident one.
 * Readings older than [staleAfterMs] are ignored: a source that stopped
 * reporting must not keep the needle up.
 */
class DetectionEngine(var staleAfterMs: Long = 5_000L) {

    fun fuse(
        readings: Collection<SourceReading>,
        weights: Map<String, Float>,
        nowMs: Long,
        defaultWeight: Float = 1f,
    ): Detection {
        val fresh = readings.filter { nowMs - it.timestampMs <= staleAfterMs }
            .map { it to (weights[it.sourceId] ?: defaultWeight).coerceAtLeast(0f) }
            .filter { (_, w) -> w > 0f }
        if (fresh.isEmpty()) return Detection(0f, 0f, emptyList(), nowMs)

        val totalWeight = fresh.sumOf { (_, w) -> w.toDouble() }
        val effective = fresh.sumOf { (r, w) -> (w * r.confidence).toDouble() }
        val probability = if (effective > 0.0) {
            fresh.sumOf { (r, w) -> (w * r.confidence * r.score).toDouble() } / effective
        } else 0.0

        val contributions = fresh.map { (r, w) ->
            val share = if (effective > 0.0) w * r.confidence / effective else 0.0
            Contribution(r.sourceId, r.score, r.confidence, w, share.toFloat())
        }.sortedByDescending { it.share }

        return Detection(
            probability = probability.toFloat().coerceIn(0f, 100f),
            confidence = (effective / totalWeight).toFloat().coerceIn(0f, 1f),
            contributions = contributions,
            timestampMs = nowMs,
        )
    }
}
