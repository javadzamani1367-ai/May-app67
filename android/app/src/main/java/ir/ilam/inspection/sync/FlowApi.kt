package ir.ilam.inspection.sync

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * A case's journey between the manager, the area executor and the expert, as
 * the apps read and move it live: the follow-up board, registering a report,
 * handing it on, the timeline, and — for the expert — what was referred to them
 * and whether what they sent has been seen. The server scopes every call by
 * the account's role and areas.
 */
object Flow {
    data class Person(val id: String, val name: String, val areas: List<String>)
    data class Area(val code: String, val name: String)
    data class People(val role: Int, val areas: List<Area>, val experts: List<Person>, val executors: List<Person>,
                      val reportTypes: List<Pair<Int, String>>)

    data class BoardCase(
        val id: String, val code: String, val type: String, val area: String, val address: String?, val createdAt: Long,
        val stage: String, val stageName: String, val executor: String?, val expert: String?, val dueAt: Long?,
        val referDueAt: Long?, val autoReferred: Boolean, val late: Boolean
    )

    data class Board(val cases: List<BoardCase>, val stageCounts: Map<String, Int>, val late: Int)

    data class Event(val at: Long, val who: String?, val what: String, val note: String?)

    data class Summary(
        val code: String, val area: String, val areaCode: String?, val status: Int, val approvalState: Int,
        val address: String?, val type: String,
        val originName: String, val intakeCode: String?, val intakeBy: String?, val intakeNote: String?, val referDueAt: Long?,
        val executor: String?, val autoReferred: Boolean, val expert: String?, val assignedAt: Long?, val dueAt: Long?,
        val visitedAt: Long?, val approvedAt: Long?, val dispatchedAt: Long?, val expertLate: Boolean, val timeline: List<Event>,
        /** 0 crypto, 1 illegal power; [canAct] false where this account only supervises (the manager in illegal power). */
        val caseKind: Int = 0, val canAct: Boolean = true, val hqAlertAt: Long? = null
    )

    /** A case referred to this expert and not yet visited. */
    data class Assignment(val reportId: String, val code: String, val by: String?, val byRole: Int?, val at: Long?,
                          val dueAt: Long?, val note: String?)

    /** Where the expert's last submission stands: seen, by whom, and the decision. */
    data class Status(val reportId: String, val submittedAt: Long, val seenAt: Long?, val seenBy: String?, val seenRole: Int?,
                      val decision: Int, val decidedAt: Long?, val decidedBy: String?, val decidedRole: Int?, val comment: String?)

    internal fun person(o: JSONObject) = Person(o.optString("id"), o.optString("name"),
        o.optJSONArray("areas")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty())

    internal fun summary(o: JSONObject) = Summary(
        code = o.optString("code"), area = o.optString("area"), areaCode = o.str("area_code"), status = o.optInt("status"),
        approvalState = o.optInt("approval_state"), address = o.str("address"), type = o.optString("type"),
        originName = o.optString("origin_name"), intakeCode = o.str("intake_code"), intakeBy = o.str("intake_by"),
        intakeNote = o.str("intake_note"), referDueAt = o.long("refer_due_at"), executor = o.str("executor"),
        autoReferred = o.optBoolean("auto_referred"), expert = o.str("expert"), assignedAt = o.long("assigned_at"),
        dueAt = o.long("due_at"), visitedAt = o.long("visited_at"), approvedAt = o.long("approved_at"),
        dispatchedAt = o.long("dispatched_at"), expertLate = o.optBoolean("expert_late"),
        timeline = o.optJSONArray("timeline").objects().map { Event(it.optLong("at"), it.str("who"), it.optString("what"), it.str("note")) },
        caseKind = o.optInt("case_kind", 0), canAct = o.optBoolean("can_act", true), hqAlertAt = o.long("hq_alert_at")
    )

    internal fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}

suspend fun ServerApi.flowBoard(token: String): ApiResult<Flow.Board> = get("cases/board", token) { data ->
    run {
        val counts = data.optJSONObject("counts")
        val stages = counts?.optJSONObject("stages")
        Flow.Board(
            cases = data.optJSONArray("cases").objects().map {
                Flow.BoardCase(it.optString("id"), it.optString("code"), it.optString("type"), it.optString("area"), it.str("address"),
                    it.optLong("created_at"), it.optString("stage"), it.optString("stage_name"), it.str("executor"), it.str("expert"),
                    it.long("due_at"), it.long("refer_due_at"), it.optBoolean("auto_referred"), it.optBoolean("late"))
            },
            stageCounts = stages?.keys()?.asSequence()?.associate { it to stages.optInt(it) }.orEmpty(),
            late = counts?.optInt("late") ?: 0
        )
    }
}

suspend fun ServerApi.flowPeople(token: String, area: String? = null): ApiResult<Flow.People> =
    get("cases/people" + (area?.let { "&area=$it" } ?: ""), token) { data ->
        run {
            Flow.People(
                role = data.optInt("role"),
                areas = data.optJSONArray("areas").objects().map { Flow.Area(it.optString("code"), it.optString("name")) },
                experts = data.optJSONArray("experts").objects().map(Flow::person),
                executors = data.optJSONArray("executors").objects().map(Flow::person),
                reportTypes = data.optJSONArray("report_types").objects().map { it.optInt("code") to it.optString("name") }
            )
        }
    }

/** A new report on the server; the answer is its id, and a warning if the referral that came with it was refused. */
suspend fun ServerApi.flowIntake(token: String, fields: Map<String, String>): ApiResult<Pair<String, String?>> {
    val body = JSONObject()
    fields.forEach { (key, value) -> if (value.isNotBlank()) body.put(key, value.trim()) }
    return post("cases/intake", body, token) { data -> data.optString("id") to data.str("warning") }
}

suspend fun ServerApi.flowToExecutor(token: String, reportId: String, executorId: String?, note: String): ApiResult<Flow.Summary> =
    post("cases/executor", JSONObject().put("report_id", reportId).putOpt("executor_id", executorId).put("note", note.trim()), token) {
        Flow.summary(it.optJSONObject("flow") ?: JSONObject())
    }

suspend fun ServerApi.flowToExpert(token: String, reportId: String, expertId: String, dueDays: Int?, note: String): ApiResult<Flow.Summary> =
    post("cases/expert", JSONObject().put("report_id", reportId).put("expert_id", expertId).putOpt("due_days", dueDays)
        .put("note", note.trim()), token) { Flow.summary(it.optJSONObject("flow") ?: JSONObject()) }

suspend fun ServerApi.flowFollowUp(token: String, reportId: String, note: String): ApiResult<Flow.Summary> =
    post("cases/followup", JSONObject().put("report_id", reportId).put("note", note.trim()), token) {
        Flow.summary(it.optJSONObject("flow") ?: JSONObject())
    }

suspend fun ServerApi.flowSummary(token: String, reportId: String): ApiResult<Flow.Summary> =
    get("cases/flow&report_id=$reportId", token) { Flow.summary(it.optJSONObject("flow") ?: JSONObject()) }

/** Cases referred to this expert and not yet visited, from the server's answer to cases/assigned. */
fun parseAssigned(data: JSONObject): List<Flow.Assignment> = data.optJSONArray("assigned").objects().map {
    Flow.Assignment(it.optString("report_id"), it.optString("code"), it.str("by"), it.int("by_role"), it.long("at"),
        it.long("due_at"), it.str("note"))
}

/** Where each of this expert's submissions stands, from the server's answer to cases/mine. */
fun parseStatuses(data: JSONObject): List<Flow.Status> = data.optJSONArray("statuses").objects().map {
    Flow.Status(it.optString("report_id"), it.optLong("submitted_at"), it.long("seen_at"), it.str("seen_by"), it.int("seen_role"),
        it.optInt("decision"), it.long("decided_at"), it.str("decided_by"), it.int("decided_role"), it.str("comment"))
}

/** The raw answer, so the phone can keep it for the next time it has no signal. */
suspend fun ServerApi.flowRaw(token: String, route: String): ApiResult<String> = get(route, token) { it.toString() }

/** JSON helpers for the live manager calls: null for absent and blank, never "null" as text. */
internal fun JSONArray?.objects(): List<JSONObject> = (0 until (this?.length() ?: 0)).mapNotNull { this?.optJSONObject(it) }
internal fun JSONObject.str(key: String): String? = if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }
internal fun JSONObject.long(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
internal fun JSONObject.int(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
