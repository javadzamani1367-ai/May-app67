package ir.ilam.inspection.field.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A name picked from the manager's list, with its id so a person's work is
 * counted under one record; [id] is null only when the list had not reached
 * the phone yet and the name was typed.
 */
data class Pick(val id: String?, val name: String) {
    fun toJson(): JSONObject = JSONObject().put("name", name.trim()).apply { putOpt("id", id) }

    companion object {
        fun fromJson(json: JSONObject?): Pick? {
            val name = json?.optString("name").orEmpty().trim()
            if (name.isEmpty()) return null
            return Pick(json?.optString("id")?.ifBlank { null }, name)
        }
    }
}

/** The crew of an inspection: lead and lineman required, driver and vehicle optional. */
data class Crew(
    val lead: Pick? = null,
    val lineman: Pick? = null,
    val driver: Pick? = null,
    val vehicle: String = ""
) {
    val complete: Boolean get() = lead != null && lineman != null

    fun toJson(): JSONObject = JSONObject().apply {
        lead?.let { put("lead", it.toJson()) }
        lineman?.let { put("lineman", it.toJson()) }
        driver?.let { put("driver", it.toJson()) }
        if (vehicle.isNotBlank()) put("vehicle", vehicle.trim())
    }

    companion object {
        fun fromJson(json: JSONObject?): Crew = if (json == null) Crew() else Crew(
            lead = Pick.fromJson(json.optJSONObject("lead")),
            lineman = Pick.fromJson(json.optJSONObject("lineman")),
            driver = Pick.fromJson(json.optJSONObject("driver")),
            vehicle = json.optString("vehicle")
        )
    }
}

/** One name of the manager's lists, as the phone keeps it. */
data class RosterEntry(val id: String, val name: String, val role: Int, val county: String?)

/**
 * The manager's crew members and medium-voltage feeders, kept on the phone
 * from the last sync so the forms work with no signal.
 */
data class Roster(val crew: List<RosterEntry> = emptyList(), val feeders: List<RosterEntry> = emptyList()) {

    fun crewFor(role: Int): List<RosterEntry> = crew.filter { it.role == role }

    companion object {
        const val ROLE_LEAD = 0
        const val ROLE_LINEMAN = 1
        const val ROLE_DRIVER = 2

        /** The `roster` object of `GET /field/me`, which is also what the phone stores. */
        fun fromJson(text: String?): Roster {
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return Roster()
            fun list(key: String, role: (JSONObject) -> Int): List<RosterEntry> {
                val array = json.optJSONArray(key) ?: JSONArray()
                return (0 until array.length()).mapNotNull { i ->
                    val o = array.optJSONObject(i) ?: return@mapNotNull null
                    val id = o.optString("id")
                    val name = o.optString("name").trim()
                    if (id.isBlank() || name.isEmpty()) null
                    else RosterEntry(id, name, role(o), o.optString("county").ifBlank { null })
                }
            }
            return Roster(crew = list("crew") { it.optInt("role", -1) }, feeders = list("mv_feeders") { -1 })
        }
    }
}
