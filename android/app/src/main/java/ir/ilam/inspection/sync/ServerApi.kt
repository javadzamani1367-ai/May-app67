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

    private companion object {
        const val CONNECT_TIMEOUT = 8_000
        const val READ_TIMEOUT = 15_000
    }
}
