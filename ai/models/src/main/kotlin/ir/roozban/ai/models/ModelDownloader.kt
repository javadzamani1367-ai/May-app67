package ir.roozban.ai.models

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class DownloadProgress(val downloadedBytes: Long, val totalBytes: Long) {
    val fraction: Float get() = if (totalBytes <= 0) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

class DownloadException(message: String, val retryable: Boolean) : IOException(message)

/**
 * Downloads one file with resume: bytes land in `<target>.part`, a later call continues from
 * its length with an HTTP Range request. The SHA-256 is computed while streaming (the existing
 * part is hashed first) and the file is renamed into place only when size and hash match.
 */
class ModelDownloader(
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val bufferSize: Int = 256 * 1024,
) {
    /** Blocking; call on an IO dispatcher. Cancellation is checked between buffers. */
    suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (DownloadProgress) -> Unit = {},
    ): File {
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        if (expectedSize > 0 && part.length() > expectedSize) part.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        var offset = part.length()
        if (offset > 0) hashPrefix(part, offset, digest)

        if (expectedSize <= 0 || offset < expectedSize) {
            val conn = open(URL(url))
            try {
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Roozban")
                if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")
                val code = conn.responseCode
                when {
                    code == HttpURLConnection.HTTP_PARTIAL && offset > 0 -> Unit
                    code == HttpURLConnection.HTTP_OK -> {
                        // The server ignored the range: start over.
                        if (offset > 0) {
                            digest.reset()
                            offset = 0
                        }
                    }
                    code == 416 -> {
                        // Already complete on our side, or the part is bogus: verify below.
                    }
                    else -> throw DownloadException("HTTP $code", retryable = code >= 500 || code == 429)
                }
                val total = if (expectedSize > 0) expectedSize else offset + conn.contentLengthLong.coerceAtLeast(0)
                if (code != 416) {
                    RandomAccessFile(part, "rw").use { out ->
                        out.setLength(offset)
                        out.seek(offset)
                        conn.inputStream.use { input ->
                            val buf = ByteArray(bufferSize)
                            var done = offset
                            var lastReport = 0L
                            onProgress(DownloadProgress(done, total))
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = try {
                                    input.read(buf)
                                } catch (e: IOException) {
                                    throw DownloadException(e.message ?: "read failed", retryable = true)
                                }
                                if (n < 0) break
                                out.write(buf, 0, n)
                                digest.update(buf, 0, n)
                                done += n
                                if (expectedSize in 1 until done) throw DownloadException("more data than expected", retryable = false)
                                if (done - lastReport >= REPORT_STEP || done == total) {
                                    lastReport = done
                                    onProgress(DownloadProgress(done, total))
                                }
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }

        val size = part.length()
        if (expectedSize > 0 && size != expectedSize) {
            if (size > expectedSize) part.delete()
            throw DownloadException("incomplete: $size of $expectedSize bytes", retryable = size < expectedSize)
        }
        val sha = digest.digest().toHex()
        if (expectedSha256.isNotEmpty() && !sha.equals(expectedSha256, ignoreCase = true)) {
            part.delete()
            throw DownloadException("checksum mismatch", retryable = false)
        }
        onProgress(DownloadProgress(size, size))
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw DownloadException("rename failed", retryable = false)
        return target
    }

    private suspend fun hashPrefix(file: File, length: Long, digest: MessageDigest) {
        file.inputStream().use { input ->
            val buf = ByteArray(bufferSize)
            var left = length
            while (left > 0) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) break
                digest.update(buf, 0, n)
                left -= n
            }
        }
    }

    private companion object {
        const val REPORT_STEP = 1L shl 20
    }
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
