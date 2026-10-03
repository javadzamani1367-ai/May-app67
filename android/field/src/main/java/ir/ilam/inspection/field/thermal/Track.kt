package ir.ilam.inspection.field.thermal

import kotlin.math.abs

/**
 * One position on the route. [phoneMillis] is the phone's clock when the fix
 * arrived — the same clock HIKMICRO Viewer names its files with, which is what
 * the matching uses. [gpsMillis] is the satellites' own time, kept to show how
 * far the phone's clock has drifted.
 */
data class TrackPoint(
    val phoneMillis: Long,
    val gpsMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    val altitude: Double?
) {
    /** One CSV line: plain, append-only, readable on any computer. */
    fun toLine(): String = listOf(phoneMillis, gpsMillis, latitude, longitude, accuracy, altitude ?: "")
        .joinToString(",")

    companion object {
        const val HEADER = "phone_millis,gps_millis,latitude,longitude,accuracy_m,altitude_m"

        fun parse(line: String): TrackPoint? {
            val parts = line.trim().split(',')
            if (parts.size < 5) return null
            return runCatching {
                TrackPoint(
                    phoneMillis = parts[0].toLong(),
                    gpsMillis = parts[1].toLong(),
                    latitude = parts[2].toDouble(),
                    longitude = parts[3].toDouble(),
                    accuracy = parts[4].toDouble(),
                    altitude = parts.getOrNull(5)?.toDoubleOrNull()
                )
            }.getOrNull()
        }
    }
}

/** Where a file was taken, as the route says, and how much to trust it. */
data class Located(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Double,
    /** Time to the nearest recorded point, in milliseconds. */
    val gapMillis: Long,
    val uncertain: Boolean
)

object TrackMatcher {
    /** Beyond this the nearest point says little about where the file was taken. */
    const val MAX_GAP_MILLIS = 2 * 60 * 1000L

    /**
     * The position at [time] on the phone's clock. Between two points it is
     * interpolated in proportion to time; outside the route it is the nearest
     * end. Either way, if the nearest point is more than [maxGap] away the
     * result is marked uncertain rather than dropped — the user still sees
     * where it probably was, and so does the office, with the warning.
     */
    fun locate(points: List<TrackPoint>, time: Long, maxGap: Long = MAX_GAP_MILLIS): Located? {
        if (points.isEmpty()) return null
        val sorted = if (points.zipWithNext().all { (a, b) -> a.phoneMillis <= b.phoneMillis }) points
        else points.sortedBy { it.phoneMillis }
        val after = sorted.indexOfFirst { it.phoneMillis >= time }
        val (lat, lon, acc) = when {
            after == 0 -> sorted.first().let { Triple(it.latitude, it.longitude, it.accuracy) }
            after < 0 -> sorted.last().let { Triple(it.latitude, it.longitude, it.accuracy) }
            else -> {
                val a = sorted[after - 1]
                val b = sorted[after]
                val span = (b.phoneMillis - a.phoneMillis).toDouble()
                val f = if (span <= 0) 0.0 else (time - a.phoneMillis) / span
                Triple(
                    a.latitude + (b.latitude - a.latitude) * f,
                    a.longitude + (b.longitude - a.longitude) * f,
                    maxOf(a.accuracy, b.accuracy)
                )
            }
        }
        val gap = sorted.minOf { abs(it.phoneMillis - time) }
        return Located(lat, lon, acc, gap, uncertain = gap > maxGap)
    }

    /**
     * How far the phone's clock is from GPS time, in seconds (positive: the
     * phone is ahead). The median, so one odd fix does not move it.
     */
    fun clockSkewSeconds(points: List<TrackPoint>): Long? {
        if (points.isEmpty()) return null
        val skews = points.map { it.phoneMillis - it.gpsMillis }.sorted()
        return skews[skews.size / 2] / 1000
    }
}
