package ir.ilam.inspection.sync

import ir.ilam.inspection.data.model.ApprovalState
import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.data.model.UnitPerformance
import org.json.JSONObject

/**
 * The calls only a manager makes: the approval cycle and the unit performance
 * figures. People and their accounts are in PersonsApi.
 *
 * Extensions on [ServerApi] rather than members of it, so the client an expert
 * app uses stays the short one — and so neither file grows past reading size.
 */

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
