package ir.ilam.inspection.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * One file down from any server route that answers with raw bytes — a case photo,
 * a referral's photo, a thermal image of an earlier inspection. Writes to a
 * neighbouring `.part` first: a transfer cut off half way would otherwise leave a
 * file that exists, opens, and shows a grey half-image.
 */
object RouteDownload {
    private const val CONNECT_TIMEOUT = 8_000
    private const val TRANSFER_TIMEOUT = 120_000
    private const val BUFFER = 64 * 1024

    suspend fun to(baseUrl: String, token: String, route: String, target: File): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        val partial = File(target.parentFile, target.name + ".part")
        try {
            target.parentFile?.mkdirs()
            val base = baseUrl.trim().trimEnd('/')
            connection = (URL("$base/index.php?route=$route").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = TRANSFER_TIMEOUT
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (connection.responseCode >= 400) return@withContext false
            connection.inputStream.use { input -> partial.outputStream().use { output -> input.copyTo(output, BUFFER) } }
            if (partial.length() == 0L) {
                partial.delete()
                return@withContext false
            }
            partial.renameTo(target)
        } catch (e: Exception) {
            partial.delete()
            false
        } finally {
            connection?.disconnect()
        }
    }
}
