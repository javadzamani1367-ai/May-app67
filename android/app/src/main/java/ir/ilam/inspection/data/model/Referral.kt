package ir.ilam.inspection.data.model

import org.json.JSONArray
import org.json.JSONObject

/** A reporter's photo the server holds for a referral. */
data class ReferralPhoto(val id: String, val capturedAt: Long?)

/**
 * A field report the office referred to this expert (`GET /referrals`). The
 * checklist arrives already in Persian rows from the server, so this app does
 * not need the field app's labels to show what the reporter answered.
 */
data class Referral(
    val id: String,
    val kind: Int,
    val kindName: String,
    val trackingCode: String,
    val status: Int,
    val statusName: String,
    val priority: Int?,
    val createdAt: Long,
    val dueAt: Long?,
    val address: String?,
    val plate: String?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Double?,
    val description: String?,
    val reporter: String?,
    val details: List<Pair<String, String>>,
    val photos: List<ReferralPhoto>,
    /** What the server last recorded as the result: an outcome key, or "case". */
    val resultOutcome: String?,
    /** The case this phone built from it, if any. */
    val localReportId: String? = null,
    /** A result picked on this phone that has not reached the server yet. */
    val pendingOutcome: String? = null
) {
    val isOpen: Boolean get() = status == STATUS_REFERRED && pendingOutcome == null
    val isCrypto: Boolean get() = kind == KIND_CRYPTO

    fun isOverdue(now: Long = System.currentTimeMillis()): Boolean =
        status == STATUS_REFERRED && dueAt != null && dueAt < now

    companion object {
        const val KIND_CRYPTO = 1
        const val STATUS_REFERRED = 2

        /** The payload of the expert-app QR on the printed referral sheet. */
        private const val QR_PREFIX = "TKV:F1:"
        private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        /** The referral id in a scanned QR, or null if it is not one of ours. */
        fun idFromQr(text: String?): String? {
            val value = text?.trim() ?: return null
            if (!value.startsWith(QR_PREFIX)) return null
            return value.removePrefix(QR_PREFIX).takeIf { UUID.matches(it) }?.lowercase()
        }

        fun fromJson(json: JSONObject, localReportId: String? = null, pendingOutcome: String? = null): Referral =
            Referral(
                id = json.getString("id"),
                kind = json.optInt("kind"),
                kindName = json.optString("kind_name"),
                trackingCode = json.optString("tracking_code"),
                status = json.optInt("status"),
                statusName = json.optString("status_name"),
                priority = json.intOrNull("priority"),
                createdAt = json.optLong("created_at"),
                dueAt = json.longOrNull("due_at"),
                address = json.textOrNull("address"),
                plate = json.textOrNull("plate"),
                latitude = json.doubleOrNull("latitude"),
                longitude = json.doubleOrNull("longitude"),
                accuracy = json.doubleOrNull("accuracy"),
                description = json.textOrNull("description"),
                reporter = json.textOrNull("reporter"),
                details = json.optJSONArray("details").pairs(),
                photos = json.optJSONArray("photos").photos(),
                resultOutcome = json.optJSONObject("result")?.optString("outcome")?.ifBlank { null },
                localReportId = localReportId,
                pendingOutcome = pendingOutcome
            )

        private fun JSONObject.textOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).trim().ifBlank { null }

        private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)
        private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
        private fun JSONObject.doubleOrNull(key: String): Double? =
            if (isNull(key)) null else optDouble(key).takeUnless { it.isNaN() }

        private fun JSONArray?.pairs(): List<Pair<String, String>> = if (this == null) emptyList() else
            (0 until length()).mapNotNull { i ->
                optJSONArray(i)?.let { row -> row.optString(0) to row.optString(1) }
            }.filter { it.first.isNotBlank() && it.second.isNotBlank() }

        private fun JSONArray?.photos(): List<ReferralPhoto> = if (this == null) emptyList() else
            (0 until length()).mapNotNull { i ->
                optJSONObject(i)?.let { p ->
                    ReferralPhoto(p.optString("id"), if (p.isNull("captured_at")) null else p.optLong("captured_at"))
                }
            }.filter { it.id.isNotBlank() }
    }
}

/** The light result form's choices; the keys are the server's `FieldReferrals::OUTCOMES`. */
enum class ReferralOutcome(val key: String) {
    CONFIRMED("confirmed"), NOT_FOUND("not_found"), NO_ACCESS("no_access"), OTHER("other");

    companion object {
        fun of(key: String?): ReferralOutcome? = entries.firstOrNull { it.key == key }
    }
}
