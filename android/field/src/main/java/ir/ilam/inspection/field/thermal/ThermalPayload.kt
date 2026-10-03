package ir.ilam.inspection.field.thermal

import org.json.JSONObject

/** The five temperatures HIKMICRO Viewer prints on a photo: the scene, and the R1 box. */
data class Temperatures(
    val max: String = "",
    val min: String = "",
    val boxMax: String = "",
    val boxMin: String = "",
    val boxAvg: String = ""
) {
    fun isEmpty() = listOf(max, min, boxMax, boxMin, boxAvg).all { it.isBlank() }

    fun toJson(): JSONObject = JSONObject().apply {
        mapOf("max" to max, "min" to min, "r1_max" to boxMax, "r1_min" to boxMin, "r1_avg" to boxAvg)
            .forEach { (k, v) -> v.toDoubleOrNull()?.let { put(k, it) } }
    }

    companion object {
        /** What may be typed as a temperature: a minus first (winter mornings), digits, one decimal point. */
        fun clean(text: String): String {
            val out = StringBuilder()
            text.trim().forEachIndexed { i, ch ->
                when {
                    ch in '0'..'9' -> out.append(ch)
                    (ch == '-' || ch == '\u2212') && i == 0 -> out.append('-')
                    (ch == '.' || ch == '/' || ch == '\u066B') && '.' !in out -> out.append('.')
                }
            }
            return out.toString()
        }

        fun fromJson(json: JSONObject?): Temperatures {
            if (json == null) return Temperatures()
            fun s(k: String) = if (!json.has(k)) "" else json.optDouble(k).let {
                when { it.isNaN() -> ""; it % 1.0 == 0.0 -> it.toLong().toString(); else -> it.toString() }
            }
            return Temperatures(s("max"), s("min"), s("r1_max"), s("r1_min"), s("r1_avg"))
        }
    }
}

/** What the payload keeps per file: its original HIKMICRO name, and the temperatures read off it. */
data class ThermalFileInfo(val name: String = "", val temperatures: Temperatures = Temperatures())

/**
 * A thermal session, as it travels in the item's payload: when the route ran,
 * how many points it has, how far the phone's clock was from GPS time, and
 * per file (keyed by file id) its original name and its temperatures.
 */
data class ThermalPayload(
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val points: Int = 0,
    val clockSkewSeconds: Long? = null,
    val files: Map<String, ThermalFileInfo> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        putOpt("started_at", startedAt)
        putOpt("ended_at", endedAt)
        put("points", points)
        putOpt("clock_skew_s", clockSkewSeconds)
        val list = JSONObject()
        files.toSortedMap().forEach { (id, info) ->
            list.put(id, JSONObject().put("name", info.name).apply {
                if (!info.temperatures.isEmpty()) put("temperatures", info.temperatures.toJson())
            })
        }
        put("files", list)
    }

    companion object {
        fun fromJson(text: String?): ThermalPayload {
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return ThermalPayload()
            val list = json.optJSONObject("files") ?: JSONObject()
            return ThermalPayload(
                startedAt = json.optLong("started_at").takeIf { json.has("started_at") },
                endedAt = json.optLong("ended_at").takeIf { json.has("ended_at") },
                points = json.optInt("points"),
                clockSkewSeconds = json.optLong("clock_skew_s").takeIf { json.has("clock_skew_s") },
                files = list.keys().asSequence().associateWith { id ->
                    val f = list.optJSONObject(id) ?: JSONObject()
                    ThermalFileInfo(f.optString("name"), Temperatures.fromJson(f.optJSONObject("temperatures")))
                }
            )
        }
    }
}
