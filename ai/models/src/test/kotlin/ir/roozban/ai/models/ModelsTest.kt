package ir.roozban.ai.models

import com.google.common.truth.Truth.assertThat
import com.sun.net.httpserver.HttpServer
import ir.roozban.ai.core.ChatTemplate
import ir.roozban.ai.core.DeviceTier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class ModelsTest {
    @TempDir
    lateinit var dir: File

    private val body = Random(7).nextBytes(3_000_000)
    private val sha = MessageDigest.getInstance("SHA-256").digest(body).toHex()
    private var server: HttpServer? = null
    private val requests = AtomicInteger()
    private val ranges = mutableListOf<String?>()

    /** Serves [body]; honours Range unless [ignoreRange]; stops after [cutAfter] bytes if set. */
    private fun serve(ignoreRange: Boolean = false, cutAfter: Int? = null, status: Int? = null): String {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/m.gguf") { ex ->
            requests.incrementAndGet()
            val range = ex.requestHeaders.getFirst("Range")
            synchronized(ranges) { ranges += range }
            if (status != null) {
                ex.sendResponseHeaders(status, -1)
                ex.close()
                return@createContext
            }
            val from = if (!ignoreRange && range != null) range.removePrefix("bytes=").removeSuffix("-").toInt() else 0
            val slice = body.copyOfRange(from, body.size)
            if (from > 0) ex.responseHeaders.add("Content-Range", "bytes $from-${body.size - 1}/${body.size}")
            ex.sendResponseHeaders(if (from > 0) 206 else 200, slice.size.toLong())
            try {
                ex.responseBody.use { out ->
                    if (cutAfter != null) out.write(slice, 0, cutAfter) else out.write(slice)
                }
            } catch (_: Exception) {
            }
            ex.close()
        }
        s.start()
        server = s
        return "http://127.0.0.1:${s.address.port}/m.gguf"
    }

    @AfterEach
    fun stop() {
        server?.stop(0)
    }

    private suspend fun download(url: String, target: File, expectedSha: String = sha, onProgress: (DownloadProgress) -> Unit = {}) =
        withContext(Dispatchers.IO) { ModelDownloader(bufferSize = 64 * 1024).download(url, target, body.size.toLong(), expectedSha, onProgress) }

    @Test
    fun `downloads, verifies and renames into place`() = runTest {
        val url = serve()
        val target = File(dir, "m.gguf")
        val progress = mutableListOf<DownloadProgress>()
        download(url, target) { progress += it }
        assertThat(target.readBytes()).isEqualTo(body)
        assertThat(File(dir, "m.gguf.part").exists()).isFalse()
        assertThat(progress.last().fraction).isEqualTo(1f)
        assertThat(progress.map { it.downloadedBytes }).isInOrder()
    }

    @Test
    fun `an interrupted download resumes with a range request`() = runTest {
        val target = File(dir, "m.gguf")
        val url = serve(cutAfter = 1_000_000)
        assertThrows<DownloadException> { download(url, target) }
        assertThat(File(dir, "m.gguf.part").length()).isEqualTo(1_000_000)
        stop()

        val url2 = serve()
        download(url2, target)
        assertThat(ranges.last()).isEqualTo("bytes=1000000-")
        assertThat(target.readBytes()).isEqualTo(body)
    }

    @Test
    fun `a server that ignores the range restarts cleanly`() = runTest {
        File(dir, "m.gguf.part").writeBytes(body.copyOf(500_000))
        val url = serve(ignoreRange = true)
        val target = File(dir, "m.gguf")
        download(url, target)
        assertThat(target.readBytes()).isEqualTo(body)
    }

    @Test
    fun `a wrong checksum deletes the file`() = runTest {
        val url = serve()
        val target = File(dir, "m.gguf")
        val e = assertThrows<DownloadException> { download(url, target, expectedSha = "00".repeat(32)) }
        assertThat(e.retryable).isFalse()
        assertThat(target.exists()).isFalse()
        assertThat(File(dir, "m.gguf.part").exists()).isFalse()
    }

    @Test
    fun `server errors are reported with retry advice`() = runTest {
        val e = assertThrows<DownloadException> { download(serve(status = 503), File(dir, "m.gguf")) }
        assertThat(e.retryable).isTrue()
        stop()
        val e2 = assertThrows<DownloadException> { download(serve(status = 404), File(dir, "m.gguf")) }
        assertThat(e2.retryable).isFalse()
    }

    @Test
    fun `cancelling keeps the partial file for later`() = runTest {
        val url = serve()
        val target = File(dir, "m.gguf")
        val job = async(Dispatchers.IO) {
            ModelDownloader(bufferSize = 16 * 1024).download(url, target, body.size.toLong(), sha) { p ->
                if (p.downloadedBytes > 0) throw CancellationException("stop")
            }
        }
        runCatching { job.await() }
        assertThat(target.exists()).isFalse()
        val part = File(dir, "m.gguf.part")
        assertThat(part.exists()).isTrue()
        download(url, target)
        assertThat(target.readBytes()).isEqualTo(body)
    }

    @Test
    fun `gguf header is read and the template guessed`() {
        val info = GgufInfo.read(ByteArrayInputStream(gguf("qwen3", "Qwen3-1.7B")))
        assertThat(info.version).isEqualTo(3)
        assertThat(info.architecture).isEqualTo("qwen3")
        assertThat(info.name).isEqualTo("Qwen3-1.7B")
        assertThat(info.template).isEqualTo(ChatTemplate.CHATML_NO_THINK)
        assertThat(GgufInfo.read(ByteArrayInputStream(gguf("qwen3", "Qwen3-4B-Instruct-2507"))).template).isEqualTo(ChatTemplate.CHATML)
        assertThat(GgufInfo.read(ByteArrayInputStream(gguf("gemma3", "gemma"))).template).isEqualTo(ChatTemplate.GEMMA)
        assertThrows<InvalidModelException> { GgufInfo.read(ByteArrayInputStream("not a model".toByteArray())) }
        assertThrows<InvalidModelException> { GgufInfo.read(ByteArrayInputStream(byteArrayOf(0x47, 0x47))) }
    }

    @Test
    fun `import copies a gguf once and lists it`() = runTest {
        val store = ModelStore(File(dir, "models"))
        val bytes = gguf("gemma3", "Gemma 3 1B") + ByteArray(10_000)
        val model = store.import("my-model.gguf") { ByteArrayInputStream(bytes) }
        assertThat(model.name).isEqualTo("my-model")
        assertThat(model.template).isEqualTo(ChatTemplate.GEMMA)
        assertThat(model.sizeBytes).isEqualTo(bytes.size.toLong())
        store.import("again.gguf") { ByteArrayInputStream(bytes) }
        assertThat(store.installed()).hasSize(1)
        assertThrows<InvalidModelException> { store.import("x.gguf") { ByteArrayInputStream(ByteArray(100)) } }
        assertThat(store.installed()).hasSize(1)
        store.delete(model.id)
        assertThat(store.installed()).isEmpty()
    }

    @Test
    fun `downloaded catalog models are listed with their template`() {
        val store = ModelStore(File(dir, "models"))
        val spec = ModelCatalog.get("qwen3-0.6b-q4km")!!
        store.fileFor(spec).apply { parentFile.mkdirs() }.writeBytes(ByteArray(10))
        File(store.dir, "${spec.id}.gguf.part").writeBytes(ByteArray(5))
        store.recordDownload(spec)
        val installed = store.installed().single()
        assertThat(installed.name).isEqualTo("Qwen3 0.6B")
        assertThat(installed.template).isEqualTo(ChatTemplate.CHATML_NO_THINK)
        assertThat(installed.source).isEqualTo(ModelSource.CATALOG)
    }

    @Test
    fun `catalog offers what the device can run`() {
        assertThat(ModelCatalog.availableFor(DeviceTier.UNSUPPORTED)).isEmpty()
        assertThat(ModelCatalog.availableFor(DeviceTier.LIGHT).map { it.id }).containsExactly("qwen3-0.6b-q4km", "gemma3-1b-q4km").inOrder()
        assertThat(ModelCatalog.availableFor(DeviceTier.STANDARD).first().id).isEqualTo("qwen3-1.7b-q4km")
        assertThat(ModelCatalog.availableFor(DeviceTier.HIGH).map { it.id }).contains("qwen3-4b-2507-q4km")
        ModelCatalog.all.forEach {
            assertThat(it.sha256).hasLength(64)
            assertThat(it.sizeBytes).isGreaterThan(0)
            assertThat(it.url).startsWith("https://")
        }
        assertThat(ModelCatalog.all.map { it.id }.toSet()).hasSize(ModelCatalog.all.size)
    }

    private fun gguf(arch: String, name: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun le(n: Int, v: Long) = out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array(), 0, n)
        fun str(s: String) {
            val b = s.toByteArray()
            le(8, b.size.toLong())
            out.write(b)
        }
        out.write("GGUF".toByteArray())
        le(4, 3)
        le(8, 10) // tensors
        le(8, 4) // kv count
        str("general.architecture"); le(4, 8); str(arch)
        str("general.file_type"); le(4, 4); le(4, 15)
        str("general.name"); le(4, 8); str(name)
        str("tokenizer.ggml.tokens"); le(4, 9); le(4, 8); le(8, 1); str("a")
        return out.toByteArray()
    }
}
