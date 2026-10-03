package ir.ilam.inspection.field.data

import org.json.JSONObject

/**
 * A feeder reading as it travels in the item's `payload`. The verdict goes
 * with it — computed on the phone with the tolerance in force at the time —
 * so the office sees what the user saw, even if the manager changes the
 * tolerance later.
 */
data class FeederPayload(
    val main: PhaseReading = PhaseReading(),
    val feeders: List<FeederReading> = List(FeederCheck.FEEDERS) { FeederReading() },
    val note: String = ""
) {
    fun toJson(verdict: FeederVerdict, tolerancePct: Int, toleranceMinA: Int): JSONObject = JSONObject().apply {
        put("main", main.toJson())
        put("feeders", JSONObject().apply {
            feeders.forEachIndexed { index, feeder ->
                put("ABCD"[index].toString(), JSONObject().apply {
                    putOpt("state", feeder.state?.name?.lowercase())
                    if (feeder.state == FeederState.MEASURED) put("phases", feeder.phases.toJson())
                    if (feeder.state == FeederState.NOT_MEASURED) put("reason", feeder.reason.trim())
                })
            }
        })
        put("check", JSONObject().apply {
            put("possible", verdict.possible)
            put("ok", !verdict.mismatched)
            put("tolerance_pct", tolerancePct)
            put("tolerance_min_a", toleranceMinA)
            verdict.imbalancePct?.let { put("imbalance_pct", Math.round(it * 10) / 10.0) }
        })
        if (note.isNotBlank()) put("note", note.trim())
    }

    companion object {
        fun fromJson(text: String?): FeederPayload {
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return FeederPayload()
            val feeders = json.optJSONObject("feeders") ?: JSONObject()
            return FeederPayload(
                main = phases(json.optJSONObject("main")),
                feeders = (0 until FeederCheck.FEEDERS).map { index ->
                    val f = feeders.optJSONObject("ABCD"[index].toString()) ?: JSONObject()
                    FeederReading(
                        state = FeederState.entries.firstOrNull { it.name.lowercase() == f.optString("state") },
                        phases = phases(f.optJSONObject("phases")),
                        reason = f.optString("reason")
                    )
                },
                note = json.optString("note")
            )
        }

        private fun phases(json: JSONObject?): PhaseReading = if (json == null) PhaseReading() else PhaseReading(
            r = number(json, "r"), s = number(json, "s"), t = number(json, "t")
        )

        /** "12", not "12.0": the field shows back exactly what was typed. */
        private fun number(json: JSONObject, key: String): String {
            if (!json.has(key) || json.isNull(key)) return ""
            val value = json.optDouble(key)
            if (value.isNaN()) return ""
            return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        }

        private fun PhaseReading.toJson() = JSONObject().apply {
            r.toDoubleOrNull()?.let { put("r", it) }
            s.toDoubleOrNull()?.let { put("s", it) }
            t.toDoubleOrNull()?.let { put("t", it) }
        }
    }
}
