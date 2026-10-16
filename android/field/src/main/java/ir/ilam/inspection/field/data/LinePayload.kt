package ir.ilam.inspection.field.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A pole either has its three readings, or says why it has none. */
enum class PoleState { MEASURED, NOT_MEASURED }

/**
 * One pole of the walk: where it stands, its plate, and the current on each
 * phase of the feeder as it passes the pole. [photos] are the capture times
 * of this pole's camera photos, which the item's files carry too.
 */
data class PoleReading(
    val id: String = UUID.randomUUID().toString(),
    val plate: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracy: Double? = null,
    val positionAt: Long? = null,
    /** Where the user's receiver put them when the pole was marked. */
    val observerLat: Double? = null,
    val observerLon: Double? = null,
    val observerAccuracy: Double? = null,
    val state: PoleState = PoleState.MEASURED,
    val phases: PhaseReading = PhaseReading(),
    val neutral: String = "",
    /** Branches leaving this pole for subscribers, single and three phase. */
    val branches1: Int = 0,
    val branches3: Int = 0,
    /** Subscribers on this pole whose current was abnormal; at most [branches]. */
    val subscribers: List<Subscriber> = emptyList(),
    val reason: String = "",
    val note: String = "",
    val photos: List<Long> = emptyList()
) {
    val hasPosition: Boolean get() = latitude != null && longitude != null
    val branches: Int get() = branches1 + branches3
}

/**
 * Pole-by-pole amperage of one feeder, as it travels in the item's `payload`.
 * The panel plate is the item's plate and the item's position is the first
 * pole's; everything else is here. The analysis goes along — computed on the
 * phone with the thresholds of the day — so the office sees what the user saw.
 */
data class LinePayload(
    /** "A" to "D", or "other" with [feederName]. */
    val feeder: String? = null,
    val feederName: String = "",
    val network: String? = null,
    /** The feeder's own reading at the panel, if taken: the point the walk starts from. */
    val start: PhaseReading = PhaseReading(),
    val poles: List<PoleReading> = emptyList(),
    val note: String = "",
    val crew: Crew = Crew(),
    /** The medium-voltage feeder the panel hangs on; optional. */
    val mvFeeder: Pick? = null
) {
    /**
     * [singleA] and [threeA] are the manager's abnormal-current limits of the
     * day: each subscriber goes with the verdict the user saw on the phone.
     */
    fun toJson(verdict: LineVerdict, singleA: Int? = null, threeA: Int? = null): JSONObject = JSONObject().apply {
        // 2: crew, MV feeder, panel position, branches by phase, abnormal subscribers.
        put("v", 2)
        put("crew", crew.toJson())
        mvFeeder?.let { put("mv_feeder", it.toJson()) }
        putOpt("feeder", feeder)
        if (feederName.isNotBlank()) put("feeder_name", feederName.trim())
        putOpt("network", network)
        if (start.values().any { it != null }) put("start", start.toJson())
        put("poles", JSONArray().apply {
            poles.forEachIndexed { index, pole ->
                put(JSONObject().apply {
                    put("id", pole.id)
                    put("n", index + 1)
                    if (pole.plate.isNotBlank()) put("plate", pole.plate.trim())
                    putOpt("lat", pole.latitude)
                    putOpt("lon", pole.longitude)
                    putOpt("acc", pole.accuracy?.let { Math.round(it * 10) / 10.0 })
                    putOpt("at", pole.positionAt)
                    putOpt("olat", pole.observerLat)
                    putOpt("olon", pole.observerLon)
                    putOpt("oacc", pole.observerAccuracy?.let { Math.round(it * 10) / 10.0 })
                    put("state", pole.state.name.lowercase())
                    if (pole.state == PoleState.MEASURED) {
                        put("phases", pole.phases.toJson())
                        pole.neutral.toDoubleOrNull()?.let { put("neutral", it) }
                    } else {
                        put("reason", pole.reason.trim())
                    }
                    if (pole.branches > 0) put("branches", pole.branches)
                    if (pole.branches1 > 0) put("b1", pole.branches1)
                    if (pole.branches3 > 0) put("b3", pole.branches3)
                    if (pole.subscribers.isNotEmpty()) put("subscribers", JSONArray(pole.subscribers.map { sub ->
                        sub.toJson().apply {
                            if (singleA != null && threeA != null && sub.ampsComplete) put("abnormal", sub.abnormal(singleA, threeA))
                        }
                    }))
                    if (pole.note.isNotBlank()) put("note", pole.note.trim())
                    if (pole.photos.isNotEmpty()) put("photos", JSONArray(pole.photos))
                })
            }
        })
        put("analysis", verdict.toJson())
        if (singleA != null && threeA != null) put("abnormal_a", JSONObject().put("single", singleA).put("three", threeA))
        if (note.isNotBlank()) put("note", note.trim())
    }

    companion object {
        val FEEDERS = listOf("A", "B", "C", "D", "other")
        val NETWORKS = listOf("overhead", "abc", "underground")

        fun fromJson(text: String?): LinePayload {
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return LinePayload()
            val poles = json.optJSONArray("poles") ?: JSONArray()
            return LinePayload(
                feeder = json.optString("feeder").ifBlank { null },
                feederName = json.optString("feeder_name"),
                network = json.optString("network").ifBlank { null },
                start = phases(json.optJSONObject("start")),
                poles = (0 until poles.length()).mapNotNull { poles.optJSONObject(it) }.map { pole(it) },
                note = json.optString("note"),
                crew = Crew.fromJson(json.optJSONObject("crew")),
                mvFeeder = Pick.fromJson(json.optJSONObject("mv_feeder"))
            )
        }

        private fun pole(p: JSONObject) = PoleReading(
            id = p.optString("id").ifBlank { UUID.randomUUID().toString() },
            plate = p.optString("plate"),
            latitude = p.double("lat"),
            longitude = p.double("lon"),
            accuracy = p.double("acc"),
            positionAt = if (p.has("at") && !p.isNull("at")) p.optLong("at") else null,
            observerLat = p.double("olat"),
            observerLon = p.double("olon"),
            observerAccuracy = p.double("oacc"),
            state = if (p.optString("state") == "not_measured") PoleState.NOT_MEASURED else PoleState.MEASURED,
            phases = phases(p.optJSONObject("phases")),
            neutral = number(p, "neutral"),
            // Before 0.8 there was one count: it is kept as single-phase branches.
            branches1 = if (p.has("b1") || p.has("b3")) p.optInt("b1") else p.optInt("branches"),
            branches3 = p.optInt("b3"),
            subscribers = p.optJSONArray("subscribers")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Subscriber::fromJson) }
            }.orEmpty(),
            reason = p.optString("reason"),
            note = p.optString("note"),
            photos = p.optJSONArray("photos")?.let { a -> (0 until a.length()).map { a.optLong(it) }.filter { it > 0 } }.orEmpty()
        )

        private fun JSONObject.double(key: String): Double? =
            if (!has(key) || isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

        private fun phases(json: JSONObject?): PhaseReading = if (json == null) PhaseReading() else PhaseReading(
            r = number(json, "r"), s = number(json, "s"), t = number(json, "t")
        )

        /** "12", not "12.0": the field shows back exactly what was typed. */
        private fun number(json: JSONObject, key: String): String {
            val value = json.double(key) ?: return ""
            return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        }

        private fun PhaseReading.toJson() = JSONObject().apply {
            r.toDoubleOrNull()?.let { put("r", it) }
            s.toDoubleOrNull()?.let { put("s", it) }
            t.toDoubleOrNull()?.let { put("t", it) }
        }
    }
}
