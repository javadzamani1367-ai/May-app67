package ir.ilam.inspection.field.data

import org.json.JSONObject

/** The case an expert opened from a crypto report, as far as the reporter may see it. */
data class CaseOutcome(val trackingCode: String?, val devices: Int?, val totalWatt: Double?)

/**
 * What the office says back about an item, as `GET /field/items` sends it.
 * How much of the result arrives is the manager's choice (none, the outcome,
 * or the outcome with its note); the reason for a rejection or a revisit
 * always arrives, since it is addressed to the reporter.
 */
data class ServerInfo(
    /** Why the office rejected the item or asks for another visit. */
    val note: String? = null,
    val noteAt: Long? = null,
    /** The result's key: "confirmed", "not_found", "no_access", "other", or "case". */
    val outcome: String? = null,
    val outcomeName: String? = null,
    val resultAt: Long? = null,
    val resultNote: String? = null,
    val case: CaseOutcome? = null
) {
    val hasResult: Boolean get() = outcome != null

    companion object {
        fun fromJson(text: String?): ServerInfo? {
            val json = text?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
            val result = json.optJSONObject("result")
            val case = result?.optJSONObject("case")
            val info = ServerInfo(
                note = json.text("note"),
                noteAt = json.long("note_at"),
                outcome = result?.text("outcome"),
                outcomeName = result?.text("outcome_name"),
                resultAt = result?.long("at"),
                resultNote = result?.text("note"),
                case = case?.let {
                    CaseOutcome(
                        trackingCode = it.text("tracking_code"),
                        devices = if (it.has("devices") && !it.isNull("devices")) it.optInt("devices") else null,
                        totalWatt = if (it.has("total_watt") && !it.isNull("total_watt")) it.optDouble("total_watt") else null
                    )
                }
            )
            return info.takeIf { it.note != null || it.outcome != null }
        }

        private fun JSONObject.text(key: String): String? =
            if (isNull(key)) null else optString(key).trim().ifBlank { null }

        private fun JSONObject.long(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
    }
}
