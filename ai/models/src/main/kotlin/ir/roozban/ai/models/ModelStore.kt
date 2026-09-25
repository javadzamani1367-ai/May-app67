package ir.roozban.ai.models

import ir.roozban.ai.core.ChatTemplate
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties

enum class ModelSource { CATALOG, IMPORTED }

data class InstalledModel(
    val id: String,
    val name: String,
    val file: File,
    val sizeBytes: Long,
    val template: ChatTemplate,
    val source: ModelSource,
    val kind: ModelKind = ModelKind.LLM,
)

/**
 * Models on disk: `<dir>/<id>.gguf` with a `<id>.properties` sidecar. Unfinished downloads
 * (`.part`) are not listed.
 */
class ModelStore(val dir: File) {

    fun installed(): List<InstalledModel> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".gguf") } ?: return emptyList()
        return files.mapNotNull { f ->
            val id = f.name.removeSuffix(".gguf")
            val props = Properties()
            sidecar(id).takeIf { it.exists() }?.inputStream()?.use(props::load)
            val spec = ModelCatalog.get(id)
            InstalledModel(
                id = id,
                name = props.getProperty("name") ?: spec?.name ?: id,
                file = f,
                sizeBytes = f.length(),
                template = props.getProperty("template")?.let { runCatching { ChatTemplate.valueOf(it) }.getOrNull() }
                    ?: spec?.template ?: ChatTemplate.CHATML,
                source = if (props.getProperty("source") == ModelSource.IMPORTED.name) ModelSource.IMPORTED else ModelSource.CATALOG,
                kind = spec?.kind ?: if (ModelCatalog.isLegacySpeech(id)) ModelKind.SPEECH else ModelKind.LLM,
            )
        }.sortedBy { it.name }
    }

    fun get(id: String): InstalledModel? = installed().firstOrNull { it.id == id }

    fun fileFor(spec: ModelSpec): File = File(dir, "${spec.id}.gguf")

    /** Bytes already downloaded for [spec], for "continue download". */
    fun partialBytes(spec: ModelSpec): Long = File(dir, "${spec.id}.gguf.part").length()

    fun recordDownload(spec: ModelSpec) = writeSidecar(spec.id, spec.name, spec.template, ModelSource.CATALOG)

    /**
     * Copies a user-picked GGUF into the store. The header is checked first; the id is derived
     * from the content hash so importing the same file twice does not duplicate it.
     */
    suspend fun import(displayName: String, open: () -> InputStream): InstalledModel {
        val info = open().use { GgufInfo.read(it) }
        dir.mkdirs()
        val tmp = File(dir, "import.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            open().use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                    }
                }
            }
            val id = "import-" + digest.digest().toHex().take(12)
            val target = File(dir, "$id.gguf")
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw InvalidModelException("could not store the file")
            val name = displayName.removeSuffix(".gguf").ifBlank { info.name ?: id }
            writeSidecar(id, name, info.template, ModelSource.IMPORTED)
            return get(id) ?: throw InvalidModelException("import failed")
        } finally {
            tmp.delete()
        }
    }

    fun delete(id: String) {
        dir.listFiles { f -> f.name.startsWith("$id.gguf.prefix-") }?.forEach { it.delete() }
        File(dir, "$id.gguf").delete()
        File(dir, "$id.gguf.part").delete()
        sidecar(id).delete()
    }

    fun deletePartial(spec: ModelSpec) {
        File(dir, "${spec.id}.gguf.part").delete()
    }

    private fun sidecar(id: String) = File(dir, "$id.properties")

    private fun writeSidecar(id: String, name: String, template: ChatTemplate, source: ModelSource) {
        dir.mkdirs()
        val props = Properties()
        props["name"] = name
        props["template"] = template.name
        props["source"] = source.name
        sidecar(id).outputStream().use { props.store(it, null) }
    }
}
