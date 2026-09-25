package ir.roozban.learning

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/** Work done at a moment: a completion (weight in minutes-equivalent) or a stretch of tracked time. */
data class WorkEvent(val start: Instant, val end: Instant = start, val weightMinutes: Double = 15.0)

/**
 * When the user gets things done: a 7×24 histogram of completions and tracked time, with a
 * half-life so recent weeks count most. [grid] is indexed by ISO day of week (Monday = 0).
 */
class ProductiveHours(val grid: Array<DoubleArray>, val total: Double) {

    /** 0..1 relative to the busiest hour; 0.5 everywhere when nothing is known. */
    fun score(day: DayOfWeek, hour: Int): Double {
        val max = grid.maxOf { it.max() }
        if (max <= 0.0) return 0.5
        return grid[day.value - 1][hour] / max
    }

    /** Share of all activity per hour of day, across the week. */
    fun byHour(): DoubleArray {
        val out = DoubleArray(24) { h -> grid.sumOf { it[h] } }
        val sum = out.sum()
        return if (sum <= 0) out else DoubleArray(24) { out[it] / sum }
    }

    /** The [length]-hour window with the most activity: (start hour, share of activity). */
    fun bestWindow(length: Int = 2): Pair<Int, Double>? {
        if (total <= 0) return null
        val h = byHour()
        // Ties go to the window that starts at an active hour.
        return (0..24 - length).map { s -> s to (s until s + length).sumOf { h[it] } }
            .maxWithOrNull(compareBy<Pair<Int, Double>> { it.second }.thenBy { h[it.first] })
    }

    companion object {
        val EMPTY = ProductiveHours(Array(7) { DoubleArray(24) }, 0.0)

        fun fit(events: List<WorkEvent>, now: Instant, zone: ZoneId, halfLifeDays: Double = 28.0): ProductiveHours {
            val grid = Array(7) { DoubleArray(24) }
            var total = 0.0
            events.forEach { e ->
                val decay = 0.5.pow(Duration.between(e.start, now).toHours().coerceAtLeast(0) / 24.0 / halfLifeDays)
                if (!e.end.isAfter(e.start)) {
                    val t = e.start.atZone(zone)
                    grid[t.dayOfWeek.value - 1][t.hour] += e.weightMinutes * decay
                    total += e.weightMinutes * decay
                } else {
                    // Spread tracked time over the hours it covers.
                    var cursor = e.start
                    while (cursor.isBefore(e.end)) {
                        val t = cursor.atZone(zone)
                        val hourEnd = t.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant()
                        val stop = if (hourEnd.isBefore(e.end)) hourEnd else e.end
                        val minutes = Duration.between(cursor, stop).seconds / 60.0
                        grid[t.dayOfWeek.value - 1][t.hour] += minutes * decay
                        total += minutes * decay
                        cursor = stop
                    }
                }
            }
            return ProductiveHours(grid, total)
        }
    }
}
