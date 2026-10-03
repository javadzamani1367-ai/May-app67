package ir.ilam.inspection.field.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a crypto-mining or illegal-power report says beyond its position and
 * description. Travels as the item's `payload`; the keys below are the wire
 * names, and the screen maps each to its Persian label.
 *
 * Every list holds stable keys rather than labels, so renaming a label never
 * changes what an old report means.
 */
data class ReportDetails(
    // Crypto mining
    val amperage: String = "",
    val minerCount: String = "",
    val minerType: String = "",
    val signs: Set<String> = emptySet(),
    // Illegal power
    val consumers: Map<String, Int> = emptyMap(),
    val otherConsumer: String = "",
    val usage: String? = null,
    val violations: Set<String> = emptySet(),
    val meterOrBill: String = "",
    // For the collection team — both kinds
    val answers: Map<String, Boolean> = emptyMap(),
    val bestTime: String? = null,
    val entrances: String = "",
    val entranceNote: String = ""
) {
    fun toJson(kind: FieldKind): JSONObject = JSONObject().apply {
        if (kind == FieldKind.CRYPTO) {
            putNumber("amperage", amperage)
            putNumber("miner_count", minerCount)
            putText("miner_type", minerType)
            put("signs", JSONArray(signs.sorted()))
        } else {
            put("consumers", JSONObject(consumers.filterValues { it > 0 }.toSortedMap()))
            putText("other_consumer", otherConsumer)
            putOpt("usage", usage)
            put("violations", JSONArray(violations.sorted()))
            putText("meter_or_bill", meterOrBill)
        }
        put("team", JSONObject().apply {
            answers.toSortedMap().forEach { (key, value) -> put(key, value) }
            putOpt("best_time", bestTime)
            putNumber("entrances", entrances)
            putText("entrance_note", entranceNote)
        })
    }

    companion object {
        fun fromJson(text: String?): ReportDetails {
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return ReportDetails()
            val team = json.optJSONObject("team") ?: JSONObject()
            val consumers = json.optJSONObject("consumers")
            return ReportDetails(
                amperage = json.optString("amperage").takeUnless { json.isNull("amperage") }.orEmpty(),
                minerCount = json.optString("miner_count").takeUnless { json.isNull("miner_count") }.orEmpty(),
                minerType = json.optString("miner_type"),
                signs = json.optJSONArray("signs").strings(),
                consumers = consumers?.keys()?.asSequence()?.associateWith { consumers.optInt(it) }.orEmpty(),
                otherConsumer = json.optString("other_consumer"),
                usage = json.optString("usage").ifBlank { null },
                violations = json.optJSONArray("violations").strings(),
                meterOrBill = json.optString("meter_or_bill"),
                answers = team.keys().asSequence()
                    .filter { team.opt(it) is Boolean }
                    .associateWith { team.optBoolean(it) },
                bestTime = team.optString("best_time").ifBlank { null },
                entrances = team.optString("entrances").takeUnless { team.isNull("entrances") }.orEmpty(),
                entranceNote = team.optString("entrance_note")
            )
        }

        private fun JSONArray?.strings(): Set<String> =
            if (this == null) emptySet() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }.toSet()

        private fun JSONObject.putText(key: String, value: String) {
            if (value.isNotBlank()) put(key, value.trim())
        }

        /** Numbers travel as numbers, not text, and a blank one is simply absent. */
        private fun JSONObject.putNumber(key: String, value: String) {
            value.toDoubleOrNull()?.let { put(key, if (it % 1.0 == 0.0) it.toLong() else it) }
        }
    }
}

/** The keys each checklist offers, in the order the screen lists them. */
object ReportKeys {
    val SIGNS = listOf(
        "fan_noise", "heat_exhaust", "covered_windows", "heavy_cabling", "new_transformer",
        "network_gear", "night_activity", "neighbour_report", "thermal_hot_pole", "voltage_drops"
    )
    val CONSUMERS = listOf("air_conditioner", "heater", "water_pump", "industrial_motor", "welder", "furnace")
    val USAGES = listOf("residential", "commercial", "industrial", "agri_well", "agri_greenhouse",
        "agri_poultry", "agri_livestock", "construction", "other")
    val VIOLATIONS = listOf("no_meter", "meter_bypass", "meter_tamper", "borrowed_supply", "wrong_usage")
    val TEAM_QUESTIONS = listOf("vehicle_access", "needs_ladder", "needs_police", "guarded", "active_now",
        "safety_hazard", "people_seen")
    val BEST_TIMES = listOf("morning", "afternoon", "night")
}

/** Priority the reporter suggests; the codes are the server's (`field_items.priority`). */
enum class Priority(val code: Int) {
    URGENT(0), NORMAL(1), LOW(2);

    companion object {
        fun of(code: Int?): Priority? = entries.firstOrNull { it.code == code }
    }
}
