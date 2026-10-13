package ir.ilam.inspection.sync

import org.json.JSONObject

/**
 * The expert's field referrals on the server. Extensions on [ServerApi], like
 * every other family of calls. The server decides from the token whose
 * referrals these are; nothing the phone sends can widen that.
 */

/** Every referral of this expert the server still shows: open ones, and recently answered. */
suspend fun ServerApi.fetchReferrals(token: String): ApiResult<List<JSONObject>> =
    get("referrals", token) { data ->
        val array = data.optJSONArray("referrals")
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
    }

/** One referral by id — what the QR on the printed sheet carries. */
suspend fun ServerApi.fetchReferral(token: String, id: String): ApiResult<JSONObject> =
    get("referrals/item&id=$id", token) { data -> data.optJSONObject("referral") ?: JSONObject() }

suspend fun ServerApi.acceptReferral(token: String, id: String, reportId: String): ApiResult<Boolean> =
    post("referrals/accept", JSONObject().put("id", id).put("report_id", reportId), token) { true }

suspend fun ServerApi.sendReferralResult(token: String, id: String, outcome: String, note: String): ApiResult<Boolean> =
    post("referrals/result", JSONObject().put("id", id).put("outcome", outcome).put("note", note), token) { true }
