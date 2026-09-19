package ir.ilam.inspection.sync

import ir.ilam.inspection.data.model.ApprovalState
import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.data.model.UnitPerformance
import org.json.JSONArray
import org.json.JSONObject

/**
 * The calls only a manager makes: the user register, the installations waiting
 * to be registered, the approval cycle, and the unit performance figures.
 *
 * Extensions on [ServerApi] rather than members of it, so the client an expert
 * app uses stays the short one — and so neither file grows past reading size.
 */

/** Everyone the manager has registered, as the server holds them. */
suspend fun ServerApi.users(token: String): ApiResult<List<ServerApi.RemoteUser>> =
    get("users", token) { data -> data.optJSONArray("users").toUsers() }

/** Installations waiting to be registered, oldest first. */
suspend fun ServerApi.deviceRequests(token: String): ApiResult<List<ServerApi.DeviceRequest>> =
    get("users/requests", token) { data ->
        val array = data.optJSONArray("requests")
        (0 until (array?.length() ?: 0)).mapNotNull { index ->
            array?.optJSONObject(index)?.let { row ->
                ServerApi.DeviceRequest(
                    deviceCode = row.optString("device_code"),
                    fullName = row.optString("full_name"),
                    phone = row.optString("phone"),
                    county = row.optString("county"),
                    createdAt = row.optLong("created_at")
                )
            }
        }
    }

/**
 * Registers or updates one expert. An empty password leaves the existing
 * one alone, so editing a county does not sign the expert out.
 */
suspend fun ServerApi.saveUser(token: String, user: ServerApi.RemoteUser, password: String): ApiResult<Boolean> =
    post(
        path = "users/save",
        token = token,
        body = JSONObject()
            .put("user_code", user.userCode)
            .put("full_name", user.fullName)
            .put("role", user.role)
            .put("county", user.county)
            .put("phone", user.phone)
            .put("device_code", user.deviceCode)
            .put("password", password)
            .put("active", if (user.active) 1 else 0)
            .put("note", user.note)
    ) { true }

suspend fun ServerApi.deactivateUser(token: String, id: String): ApiResult<Boolean> =
    post("users/delete", JSONObject().put("id", id), token) { true }

/** Unit performance over a date range, as only the server can see it. */
suspend fun ServerApi.unitStats(
    token: String,
    from: Long,
    to: Long
): ApiResult<List<UnitPerformance>> =
    get("stats/units&from=$from&to=$to", token) { data ->
        val array = data.optJSONArray("units")
        (0 until (array?.length() ?: 0)).mapNotNull { index ->
            array?.optJSONObject(index)?.let { row ->
                UnitPerformance(
                    unit = DispatchUnit.of(row.optInt("unit")),
                    sent = row.optInt("sent"),
                    seen = row.optInt("seen"),
                    answered = row.optInt("answered"),
                    overdue = row.optInt("overdue"),
                    onTime = row.optInt("on_time"),
                    averageAnswerHours = if (row.isNull("avg_answer_hours")) {
                        null
                    } else {
                        row.optDouble("avg_answer_hours")
                    }
                )
            }
        }
    }

/** Everything waiting on the manager, across every expert's phone. */
suspend fun ServerApi.pendingApprovals(token: String): ApiResult<List<ServerApi.PendingApproval>> =
    get("approvals", token) { data ->
        val array = data.optJSONArray("pending")
        (0 until (array?.length() ?: 0)).mapNotNull { index ->
            array?.optJSONObject(index)?.let { row ->
                ServerApi.PendingApproval(
                    reportId = row.optString("report_id"),
                    trackingCode = row.optString("tracking_code"),
                    county = row.optString("county"),
                    expertCode = row.optString("expert_code"),
                    submittedAt = row.optLong("submitted_at")
                )
            }
        }
    }

suspend fun ServerApi.submitApproval(token: String, reportId: String): ApiResult<Boolean> =
    post("approvals/submit", JSONObject().put("report_id", reportId), token) { true }

suspend fun ServerApi.decideApproval(
    token: String,
    reportId: String,
    state: ApprovalState,
    comment: String
): ApiResult<Boolean> = post(
    path = "approvals/decide",
    token = token,
    body = JSONObject()
        .put("report_id", reportId)
        // The server speaks of approved and returned only; the phone's
        // draft and pending have no decision to report.
        .put("decision", if (state == ApprovalState.APPROVED) 1 else 2)
        .put("comment", comment)
) { true }

private fun JSONArray?.toUsers(): List<ServerApi.RemoteUser> =
    (0 until (this?.length() ?: 0)).mapNotNull { index ->
        this?.optJSONObject(index)?.let { row ->
            ServerApi.RemoteUser(
                id = row.optString("id"),
                userCode = row.optString("user_code"),
                fullName = row.optString("full_name"),
                role = row.optInt("role"),
                county = row.optString("county"),
                phone = row.optString("phone"),
                deviceCode = row.optString("device_code"),
                active = row.optInt("active", 1) == 1,
                note = row.optString("note")
            )
        }
    }

