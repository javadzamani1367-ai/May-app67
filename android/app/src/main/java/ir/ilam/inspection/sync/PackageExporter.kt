package ir.ilam.inspection.sync

import ir.ilam.inspection.data.db.AppDatabase
import ir.ilam.inspection.data.db.SCHEMA_VERSION
import ir.ilam.inspection.data.repo.ReportRepository
import ir.ilam.inspection.util.CryptoBox
import ir.ilam.inspection.util.FileStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The offline route. When no cable and no Wi-Fi are available, everything that
 * has not been acknowledged yet goes into a `.cvz` — a zip of `data.json` plus
 * a `media/` folder, encrypted with the package password so a lost memory card
 * does not leak owner names and national ids.
 */
class PackageExporter(
    private val database: AppDatabase,
    private val reports: ReportRepository,
    private val files: FileStore
) {

    /** Returns the encrypted package, or null when nothing is pending. */
    suspend fun export(fileName: String, password: String, deviceId: String, expertCode: String): File? {
        val pending = database.reportDao().pendingSync()
        if (pending.isEmpty()) return null

        val payload = JSONArray()
        val mediaPaths = LinkedHashSet<String>()
        pending.forEach { report ->
            val detail = reports.detail(report.id) ?: return@forEach
            payload.put(SyncPayload.report(detail))
            detail.media.forEach { mediaPaths.add(it.filePath) }
            detail.attachments.forEach { mediaPaths.add(it.filePath) }
        }

        val manifest = JSONObject()
            .put("device_id", deviceId)
            .put("expert_code", expertCode)
            .put("schema_version", SCHEMA_VERSION)
            .put("exported_at", System.currentTimeMillis())
            .put("reports", payload)

        val plain = File.createTempFile("cvz", ".zip", files.exportRoot)
        ZipOutputStream(plain.outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            mediaPaths.forEach { relativePath ->
                val source = files.resolve(relativePath)
                if (!source.exists()) return@forEach
                zip.putNextEntry(ZipEntry(relativePath))
                source.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }

        val target = files.newExportFile(ensureExtension(fileName))
        CryptoBox.encrypt(plain, target, password)
        plain.delete()
        return target
    }

    /** Ids in the package, so they can be acknowledged once Windows confirms. */
    suspend fun pendingIds(): List<String> = database.reportDao().pendingSync().map { it.id }

    suspend fun markSynced(ids: List<String>) {
        database.reportDao().markSynced(ids, System.currentTimeMillis())
    }

    private fun ensureExtension(fileName: String): String =
        if (fileName.endsWith(EXTENSION, ignoreCase = true)) fileName else fileName + EXTENSION

    companion object {
        const val EXTENSION = ".cvz"
        const val DATA_ENTRY = "data.json"
    }
}
