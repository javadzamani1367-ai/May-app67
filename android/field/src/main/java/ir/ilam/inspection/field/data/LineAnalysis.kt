package ir.ilam.inspection.field.data

import ir.ilam.inspection.util.Fix
import ir.ilam.inspection.util.LocationRefiner
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

/** A point of the walk that has readings: the panel (pole 0) or a measured pole, by its number. */
data class LinePoint(val pole: Int, val phases: List<Double>, val latitude: Double? = null, val longitude: Double? = null) {
    val total: Double get() = phases.sum()
}

/**
 * The stretch between two measured points. What the current lost on the way
 * is the load taken off the line there — the houses, the wells, and the one
 * mining farm this walk is looking for.
 */
data class LineSegment(val from: LinePoint, val to: LinePoint) {
    val drops: List<Double> get() = from.phases.zip(to.phases) { a, b -> a - b }
    val drop: Double get() = from.total - to.total

    /** Metres between the two poles, when both were located. */
    val metres: Double?
        get() {
            if (from.latitude == null || from.longitude == null || to.latitude == null || to.longitude == null) return null
            return LocationRefiner.distanceMeters(Fix(from.latitude, from.longitude, 1.0), Fix(to.latitude, to.longitude, 1.0))
        }
}

enum class SegmentKind { NORMAL, HEAVY, RISE }

/**
 * What the walk shows. A HEAVY stretch takes more than a fifth of the
 * feeder's starting current (at least 10 A); a RISE gains current going
 * downstream, which a radial feeder cannot do — a misread clamp meter, a
 * pole read on the wrong feeder, or a second supply.
 */
data class LineVerdict(val points: List<LinePoint>, val segments: List<LineSegment>, val heavyA: Double, val riseA: Double) {
    fun kindOf(segment: LineSegment): SegmentKind = when {
        segment.drop >= heavyA -> SegmentKind.HEAVY
        segment.drop <= -riseA -> SegmentKind.RISE
        else -> SegmentKind.NORMAL
    }

    val heavy: List<LineSegment> get() = segments.filter { kindOf(it) == SegmentKind.HEAVY }
    val rises: List<LineSegment> get() = segments.filter { kindOf(it) == SegmentKind.RISE }
    val heaviest: LineSegment? get() = segments.filter { it.drop > 0 }.maxByOrNull { it.drop }

    /** Current still flowing past the last measured pole: load further down the line. */
    val tail: Double? get() = points.lastOrNull()?.total

    fun toJson(): JSONObject = JSONObject().apply {
        put("points", points.size)
        put("heavy_a", round(heavyA))
        put("rise_a", round(riseA))
        tail?.let { put("tail_a", round(it)) }
        heaviest?.let { put("heaviest", segmentJson(it)) }
        put("heavy", JSONArray(heavy.map(::segmentJson)))
        put("rises", JSONArray(rises.map(::segmentJson)))
    }

    private fun segmentJson(segment: LineSegment) = JSONObject().apply {
        put("from", segment.from.pole)
        put("to", segment.to.pole)
        put("drop", round(segment.drop))
        segment.metres?.let { put("m", it.roundToLong()) }
    }

    private fun round(value: Double) = Math.round(value * 10) / 10.0
}

object LineAnalysis {
    const val MIN_POLES = 2
    private const val HEAVY_PCT = 20.0
    private const val HEAVY_MIN_A = 10.0
    private const val RISE_PCT = 5.0
    private const val RISE_MIN_A = 5.0

    fun verdict(payload: LinePayload): LineVerdict {
        val points = buildList {
            if (payload.start.complete()) add(LinePoint(0, payload.start.values().map { it!! }))
            payload.poles.forEachIndexed { index, pole ->
                if (pole.state == PoleState.MEASURED && pole.phases.complete()) {
                    add(LinePoint(index + 1, pole.phases.values().map { it!! }, pole.latitude, pole.longitude))
                }
            }
        }
        val reference = points.firstOrNull()?.total ?: 0.0
        return LineVerdict(
            points = points,
            segments = points.zipWithNext { a, b -> LineSegment(a, b) },
            heavyA = maxOf(reference * HEAVY_PCT / 100, HEAVY_MIN_A),
            riseA = maxOf(reference * RISE_PCT / 100, RISE_MIN_A)
        )
    }

    /**
     * What stops saving, as keys the screen words: which feeder, enough
     * measured poles, and for each pole its place and readings (or why not).
     */
    fun missing(payload: LinePayload): List<String> = buildList {
        if (payload.feeder == null || payload.feeder == "other" && payload.feederName.isBlank()) add("feeder")
        if (payload.start.values().any { (it ?: 0.0) > FeederCheck.MAX_AMPERE }) add("start_range")
        val measured = payload.poles.count { it.state == PoleState.MEASURED && it.phases.complete() }
        if (measured < MIN_POLES) add("poles")
        payload.poles.forEachIndexed { index, pole -> addAll(poleMissing(pole, index + 1)) }
    }

    fun poleMissing(pole: PoleReading, number: Int): List<String> = buildList {
        if (!pole.hasPosition) add("pole_position:$number")
        when (pole.state) {
            PoleState.MEASURED -> {
                if (!pole.phases.complete()) add("pole_values:$number")
                if (pole.phases.values().any { (it ?: 0.0) > FeederCheck.MAX_AMPERE }) add("pole_range:$number")
            }
            PoleState.NOT_MEASURED -> if (pole.reason.isBlank()) add("pole_reason:$number")
        }
    }
}
