package ir.ilam.inspection.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** What came back from the server, or why nothing did. */
sealed class ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>()

    /** The server answered, and said no. [code] is its machine-readable reason. */
    data class Refused(val code: String, val message: String) : ApiResult<Nothing>()

    /** Nothing was reached. Offline, wrong address, or the host is down. */
    data object Unreachable : ApiResult<Nothing>()
}

/**
 * The phone's client for the central server.
 *
 * Built on HttpURLConnection and org.json, both already in Android. A client
 * library would add megabytes to an APK that has a twenty megabyte ceiling,
 * for calls that are this plain.
 *
 * Every call is short and gives up quickly: an expert standing in a village
 * with one bar must not watch a spinner for a minute before the app admits it
 * cannot reach anything.
 */
class ServerApi(private val baseUrl: String) {

    val configured: Boolean get() = baseUrl.isNotBlank()

    suspend fun login(
        userCode: String,
        password: String,
        deviceCode: String
    ): ApiResult<LoginResult> = post(
        path = "auth/login",
        body = JSONObject()
            .put("user_code", userCode)
            .put("password", password)
            .put("device_code", deviceCode)
    ) { data ->
        LoginResult(
            token = data.optString("token"),
            fullName = data.optJSONObject("user")?.optString("full_name").orEmpty(),
            role = data.optJSONObject("user")?.optInt("role") ?: 0
        )
    }

    /**
     * Asks the manager to register this installation. Nothing is granted here:
     * the request lands in the manager's queue, so the expert no longer has to
     * read a sixteen character code down a phone line.
     */
    suspend fun requestDevice(
        deviceCode: String,
        fullName: String,
        phone: String,
        county: String
    ): ApiResult<Boolean> = post(
        path = "auth/request-device",
        body = JSONObject()
            .put("device_code", deviceCode)
            .put("full_name", fullName)
            .put("phone", phone)
            .put("county", county)
    ) { true }

    suspend fun ping(): ApiResult<Int> = get("ping", token = null) { data ->
        data.optInt("schema_version", 0)
    }

    // ---- manager only ----------------------------------------------------

    /** Everyone the manager has registered, as the server holds them. */
    suspend fun users(token: String): ApiResult<List<RemoteUser>> =
        get("users", token) { data -> data.optJSONArray("users").toUsers() }

    /** Installations waiting to be registered, oldest first. */
    suspend fun deviceRequests(token: String): ApiResult<List<DeviceRequest>> =
        get("users/requests", token) { data ->
            val array = data.optJSONArray("requests")
            (0 until (array?.length() ?: 0)).mapNotNull { index ->
                array?.optJSONObject(index)?.let { row ->
                    DeviceRequest(
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
    suspend fun saveUser(token: String, user: RemoteUser, password: String): ApiResult<Boolean> =
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

    suspend fun deactivateUser(token: String, id: String): ApiResult<Boolean> =
        post("users/delete", JSONObject().put("id", id), token) { true }

    // ---- approval cycle --------------------------------------------------

    suspend fun submitApproval(token: String, reportId: String): ApiResult<Boolean> =
        post("approvals/submit", JSONObject().put("report_id", reportId), token) { true }

    suspend fun decideApproval(
        token: String,
        reportId: String,
        state: ir.ilam.inspection.data.model.ApprovalState,
        comment: String
    ): ApiResult<Boolean> = post(
        path = "approvals/decide",
        token = token,
        body = JSONObject()
            .put("report_id", reportId)
            // The server speaks of approved and returned only; the phone's
            // draft and pending have no decision to report.
            .put("decision", if (state == ir.ilam.inspection.data.model.ApprovalState.APPROVED) 1 else 2)
            .put("comment", comment)
    ) { true }

    private fun org.json.JSONArray?.toUsers(): List<RemoteUser> =
        (0 until (this?.length() ?: 0)).mapNotNull { index ->
            this?.optJSONObject(index)?.let { row ->
                RemoteUser(
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

    private suspend fun <T> post(
        path: String,
        body: JSONObject,
        token: String? = null,
        parse: (JSONObject) -> T
    ): ApiResult<T> = call(path, "POST", body, token, parse)

    private suspend fun <T> get(
        path: String,
        token: String?,
        parse: (JSONObject) -> T
    ): ApiResult<T> = call(path, "GET", null, token, parse)

    private suspend fun <T> call(
        path: String,
        method: String,
        body: JSONObject?,
        token: String?,
        parse: (JSONObject) -> T
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        if (!configured) return@withContext ApiResult.Unreachable

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint(path)).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("Accept", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            body?.let { payload ->
                connection.outputStream.use { it.write(payload.toString().toByteArray()) }
            }

            val status = connection.responseCode
            val text = (if (status < 400) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use(BufferedReader::readText)
                .orEmpty()

            // A body that is not JSON means something other than our service
            // answered — a parking page, a login wall, a 500 page from the host.
            val json = runCatching { JSONObject(text) }.getOrNull()
                ?: return@withContext ApiResult.Unreachable

            if (json.optBoolean("ok", false)) {
                ApiResult.Ok(parse(json.optJSONObject("data") ?: JSONObject()))
            } else {
                val error = json.optJSONObject("error")
                ApiResult.Refused(
                    code = error?.optString("code").orEmpty().ifBlank { "server_error" },
                    message = error?.optString("message").orEmpty()
                )
            }
        } catch (e: Exception) {
            ApiResult.Unreachable
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * The address is whatever the manager typed into settings, so it may or may
     * not end in a slash, and the host may have rewriting switched off. The
     * query form works either way, which is why it is the one used.
     */
    private fun endpoint(path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return "$base/index.php?route=$path"
    }

    data class LoginResult(val token: String, val fullName: String, val role: Int)

    data class RemoteUser(
        val id: String = "",
        val userCode: String = "",
        val fullName: String = "",
        val role: Int = 0,
        val county: String = "",
        val phone: String = "",
        val deviceCode: String = "",
        val active: Boolean = true,
        val note: String = ""
    )

    data class DeviceRequest(
        val deviceCode: String,
        val fullName: String,
        val phone: String,
        val county: String,
        val createdAt: Long
    )

    private companion object {
        const val CONNECT_TIMEOUT = 8_000
        const val READ_TIMEOUT = 15_000
    }
}
