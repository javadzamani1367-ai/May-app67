package ir.ilam.inspection.field.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a crypto-mining, illegal-power or burnt-equipment report says beyond
 * its position and description. Travels as the item's `payload`; the keys
 * below are the wire names, and the screen maps each to its Persian label.
 *
 * Every list holds stable keys rather than labels, so renaming a label never
 * changes what an old report means.
 */
data class ReportDetails(
    // Crypto mining (the amperage also serves the burn report)
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
    // Burnt equipment: what burnt and how many, on which phases, since when, and why
    val equipment: Map<String, Int> = emptyMap(),
    val otherEquipment: String = "",
    val phases: Set<String> = emptySet(),
    val causes: Set<String> = emptySet(),
    val occurred: String? = null,
    val outage: Boolean? = null,
    // For the collection team — every report kind
    val answers: Map<String, Boolean> = emptyMap(),
    val bestTime: String? = null,
    val entrances: String = "",
    val entranceNote: String = "",
    /** Crypto and illegal-power reports: who went (payload v2). */
    val crew: Crew = Crew()
) {
    /** A burn report names at least one piece of equipment, from the list or in words. */
    val hasEquipment: Boolean get() = equipment.values.any { it > 0 } || otherEquipment.isNotBlank()

    fun toJson(kind: FieldKind): JSONObject = JSONObject().apply {
        if (CrewSlot.hasCrew(kind)) {
            // 2: the crew went along; the server then needs its lead and lineman.
            put("v", 2)
            put("crew", crew.toJson())
        }
        when (kind) {
            FieldKind.CRYPTO -> {
                putNumber("amperage", amperage)
                putNumber("miner_count", minerCount)
                putText("miner_type", minerType)
                put("signs", JSONArray(signs.sorted()))
            }
            FieldKind.BURN -> {
                put("equipment", JSONObject(equipment.filterValues { it > 0 }.toSortedMap()))
                putText("other_equipment", otherEquipment)
                put("phases", JSONArray(phases.sorted()))
                put("causes", JSONArray(causes.sorted()))
                putOpt("occurred", occurred)
                putOpt("outage", outage)
                putNumber("amperage", amperage)
            }
            else -> {
                put("consumers", JSONObject(consumers.filterValues { it > 0 }.toSortedMap()))
                putText("other_consumer", otherConsumer)
                putOpt("usage", usage)
                put("violations", JSONArray(violations.sorted()))
                putText("meter_or_bill", meterOrBill)
            }
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
            return ReportDetails(
                amperage = json.numberText("amperage"),
                minerCount = json.numberText("miner_count"),
                minerType = json.optString("miner_type"),
                signs = json.optJSONArray("signs").strings(),
                consumers = json.optJSONObject("consumers").counts(),
                otherConsumer = json.optString("other_consumer"),
                usage = json.optString("usage").ifBlank { null },
                violations = json.optJSONArray("violations").strings(),
                meterOrBill = json.optString("meter_or_bill"),
                equipment = json.optJSONObject("equipment").counts(),
                otherEquipment = json.optString("other_equipment"),
                phases = json.optJSONArray("phases").strings(),
                causes = json.optJSONArray("causes").strings(),
                occurred = json.optString("occurred").ifBlank { null },
                outage = if (json.opt("outage") is Boolean) json.optBoolean("outage") else null,
                answers = team.keys().asSequence()
                    .filter { team.opt(it) is Boolean }
                    .associateWith { team.optBoolean(it) },
                bestTime = team.optString("best_time").ifBlank { null },
                entrances = team.numberText("entrances"),
                entranceNote = team.optString("entrance_note"),
                crew = Crew.fromJson(json.optJSONObject("crew"))
            )
        }

        private fun JSONObject.numberText(key: String): String = optString(key).takeUnless { isNull(key) }.orEmpty()

        private fun JSONObject?.counts(): Map<String, Int> =
            this?.keys()?.asSequence()?.associateWith { optInt(it) }.orEmpty()

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

    /** Burnt equipment, grouped the way it stands in the network: panel, transformer, line and service. */
    val EQUIPMENT_PANEL = listOf("main_switch", "fuse_switch", "nh_fuse", "breaker", "busbar", "panel_wiring",
        "cable_lug", "panel_body")
    val EQUIPMENT_TRANSFORMER = listOf("transformer", "lv_bushing", "cutout", "cutout_fuse", "arrester")
    val EQUIPMENT_LINE = listOf("feeder_cable", "lv_line", "connector", "service_cable", "service_breaker",
        "meter", "meter_box")
    val EQUIPMENT = EQUIPMENT_PANEL + EQUIPMENT_TRANSFORMER + EQUIPMENT_LINE
    val PHASES = listOf("r", "s", "t", "n")
    val CAUSES = listOf("overload_crypto", "overload_illegal", "wrong_fuse", "loose_connection",
        "phase_imbalance", "weather", "aging", "unknown")
    val OCCURRED = listOf("just_now", "today", "yesterday", "older")
}

/** Priority the reporter suggests; the codes are the server's (`field_items.priority`). */
enum class Priority(val code: Int) {
    URGENT(0), NORMAL(1), LOW(2);

    companion object {
        fun of(code: Int?): Priority? = entries.firstOrNull { it.code == code }
    }
}
