package ir.ilam.inspection.util

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** A single fix with the accuracy the report has to record, and its source. */
data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val fromGoogle: Boolean = true
)

/**
 * Turns a stream of fixes into one coordinate worth putting in a report.
 *
 * A phone's first answer is rarely its best. The receiver is still acquiring
 * satellites, and the first fix is often the network's estimate from cell
 * towers — twenty to sixty metres off, which is what the field reported. Held
 * still for fifteen or twenty seconds, the same phone usually settles to a few
 * metres. So the rule is: keep listening, keep the best, stop when it is good
 * enough or when the expert decides it is.
 *
 * Once several good fixes are in, they are averaged, weighted by how sure the
 * receiver was of each (1/σ²). A stationary receiver's error wanders around the
 * true point, so the average sits closer to it than any one sample does.
 *
 * The accuracy reported is **never** better than the best single fix. Averaging
 * does reduce the error, but by how much depends on how correlated the samples
 * are, and a report that claimed a precision the sensor never gave would be a
 * report nobody could defend.
 *
 * No Android here, so the rules are tested on the JVM.
 */
class LocationRefiner(
    /** Good enough to stop on its own, in metres. */
    private val targetMeters: Double = TARGET_METERS
) {
    private val samples = ArrayList<Fix>()

    val count: Int get() = samples.size

    /** The single most accurate fix so far. */
    val best: Fix? get() = samples.minByOrNull { it.accuracy }

    fun add(fix: Fix) {
        // A fix with no accuracy, or an absurd one, is not information.
        if (fix.accuracy.isNaN() || fix.accuracy <= 0.0 || fix.accuracy > MAX_USEFUL_METERS) return
        if (fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0) return
        samples += fix
    }

    /**
     * Done when the best fix is within target and enough fixes agree with it
     * that it is not one lucky sample.
     */
    val settled: Boolean
        get() {
            val top = best ?: return false
            return top.accuracy <= targetMeters && usable(top).size >= MIN_AGREEING
        }

    /** The coordinate to record: a weighted mean of the fixes that agree with the best. */
    fun estimate(): Fix? {
        val top = best ?: return null
        val agreeing = usable(top)
        if (agreeing.size < 2) return top

        var weightSum = 0.0
        var lat = 0.0
        var lon = 0.0
        agreeing.forEach { fix ->
            val weight = 1.0 / (fix.accuracy * fix.accuracy)
            weightSum += weight
            lat += fix.latitude * weight
            lon += fix.longitude * weight
        }
        return Fix(
            latitude = lat / weightSum,
            longitude = lon / weightSum,
            accuracy = top.accuracy,
            fromGoogle = top.fromGoogle
        )
    }

    /**
     * Fixes nearly as sure as the best, and near where the best says the
     * point is. A cell-tower fix three hundred metres away agrees with nothing
     * and must not drag the average towards itself.
     */
    private fun usable(top: Fix): List<Fix> {
        val ceiling = maxOf(top.accuracy * ACCURACY_SLACK, top.accuracy + 2.0)
        return samples.filter { fix ->
            fix.accuracy <= ceiling &&
                distanceMeters(fix, top) <= maxOf(top.accuracy, fix.accuracy) * DISTANCE_SLACK
        }
    }

    companion object {
        const val TARGET_METERS = 5.0
        const val MAX_USEFUL_METERS = 500.0
        private const val MIN_AGREEING = 3
        private const val ACCURACY_SLACK = 1.5
        private const val DISTANCE_SLACK = 2.0
        private const val EARTH_RADIUS = 6_371_000.0

        /** Equirectangular: exact enough for the few metres this compares. */
        fun distanceMeters(a: Fix, b: Fix): Double {
            val meanLat = (a.latitude + b.latitude) / 2 * PI / 180
            val dx = (b.longitude - a.longitude) * PI / 180 * cos(meanLat)
            val dy = (b.latitude - a.latitude) * PI / 180
            return sqrt(dx * dx + dy * dy) * EARTH_RADIUS
        }
    }
}
