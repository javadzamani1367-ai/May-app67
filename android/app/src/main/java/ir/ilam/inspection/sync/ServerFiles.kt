package ir.ilam.inspection.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Photos, videos and documents to and from the server.
 *
 * Separate from [ServerApi] because these are the only calls that are not
 * JSON in and JSON out: a multipart body going up and raw bytes coming down.
 * Written by hand for the same reason as the rest of the client — a transfer
 * library would cost more of the twenty megabyte ceiling than this is worth.
 *
 * Files are addressed by the id of their row, never by path. The server looks
 * the path up itself, which is what lets it apply the same rule as the rest of
 * a case: an expert reaches only their own.
 */
class ServerFiles(private val baseUrl: String, private val token: String) {

    /** One photo or document up. The row must already be on the server. */
    suspend fun upload(kind: String, id: String, file: File): Boolean = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext false

        val boundary = "----inspection" + System.nanoTime()
        var connection: HttpURLConnection? = null
        try {
            connection = open("POST", kind, id).apply {
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                // Streamed, so a ninety second video is never held in memory
                // twice on a phone that may have little of it to spare.
                setChunkedStreamingMode(BUFFER)
            }
            connection.outputStream.buffered().use { out ->
                out.write(
                    (
                        "--$boundary\r\n" +
                            "Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n" +
                            "Content-Type: application/octet-stream\r\n\r\n"
                        ).toByteArray()
                )
                file.inputStream().use { it.copyTo(out, BUFFER) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            succeeded(connection)
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * One file down, written to [target]. Writes to a neighbouring `.part`
     * first: a transfer cut off half way through would otherwise leave a file
     * that exists, opens, and shows a grey half-image in an official report.
     */
    suspend fun download(kind: String, id: String, target: File): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        val partial = File(target.parentFile, target.name + ".part")
        try {
            target.parentFile?.mkdirs()
            connection = open("GET", kind, id)
            if (connection.responseCode >= 400) return@withContext false

            connection.inputStream.use { input ->
                partial.outputStream().use { output -> input.copyTo(output, BUFFER) }
            }
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

    private fun open(method: String, kind: String, id: String): HttpURLConnection {
        val base = baseUrl.trim().trimEnd('/')
        val url = "$base/index.php?route=sync/file&kind=$kind&id=$id"
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = TRANSFER_TIMEOUT
            setRequestProperty("Authorization", "Bearer $token")
        }
    }

    /**
     * A 200 is not enough on its own: the service answers refusals with 200 in
     * some host configurations that rewrite error codes, so the envelope is
     * what decides.
     */
    private fun succeeded(connection: HttpURLConnection): Boolean {
        if (connection.responseCode >= 400) return false
        val text = connection.inputStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        return runCatching { JSONObject(text).optBoolean("ok", false) }.getOrDefault(false)
    }

    private companion object {
        const val CONNECT_TIMEOUT = 8_000

        /** Longer than the JSON calls: a video on a village connection is slow. */
        const val TRANSFER_TIMEOUT = 120_000
        const val BUFFER = 64 * 1024
    }
}
