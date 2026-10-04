package ir.ilam.inspection.util.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches a map bundle into [target], continuing where an earlier attempt
 * stopped: the map is megabytes, and the signal in the field comes and goes.
 * The finished file still has to pass [MapBundle]'s checks before it counts.
 */
object MapDownload {

    /** Where the bundle sits on the server: copied into the api folder by the manager. */
    const val SERVER_PATH = "maps/tavankav-ilam-map.zip"

    fun serverUrl(serverAddress: String): String = serverAddress.trim().trimEnd('/') + "/" + SERVER_PATH

    class Failed(val status: Int) : Exception("HTTP $status")

    suspend fun fetch(url: String, target: File, onProgress: (done: Long, total: Long?) -> Unit) = withContext(Dispatchers.IO) {
        val part = File(target.path + ".part")
        val already = if (part.exists()) part.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            if (already > 0) setRequestProperty("Range", "bytes=$already-")
        }
        try {
            val status = connection.responseCode
            // 206: the server continues; 200: it starts over, so must we.
            val resuming = status == HttpURLConnection.HTTP_PARTIAL
            if (status != HttpURLConnection.HTTP_OK && !resuming) throw Failed(status)
            val start = if (resuming) already else 0L
            val length = connection.contentLengthLong.takeIf { it > 0 }
            val total = length?.let { it + start }
            var done = start
            connection.inputStream.use { input ->
                FileOutputStream(part, resuming).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        onProgress(done, total)
                    }
                }
            }
            if (total != null && done != total) throw Failed(-1)
            target.delete()
            if (!part.renameTo(target)) throw Failed(-1)
        } finally {
            connection.disconnect()
        }
    }

    private const val TIMEOUT = 20_000
}
