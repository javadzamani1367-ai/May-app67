package ir.ilam.inspection.util

import android.content.Context
import java.io.File

/**
 * Every media and attachment path in the database is relative to this root, so
 * a `.cvz` package unpacks the same way on any device or on the Windows
 * archive. Nothing is written to the public gallery.
 */
class FileStore(context: Context) {

    private val root: File = context.filesDir

    val mediaRoot: File get() = File(root, MEDIA).ensure()
    val attachmentRoot: File get() = File(root, ATTACHMENTS).ensure()
    val exportRoot: File get() = File(root, EXPORTS).ensure()

    /** Turns a stored relative path into a real file. */
    fun resolve(relativePath: String): File = File(root, relativePath)

    /** Turns a real file back into the relative form stored in the database. */
    fun relativize(file: File): String =
        file.absolutePath.removePrefix(root.absolutePath).trimStart(File.separatorChar)

    fun newMediaFile(reportId: String, extension: String): File {
        val dir = File(mediaRoot, reportId).ensure()
        return File(dir, "${System.currentTimeMillis()}.$extension")
    }

    fun newAttachmentFile(reportId: String, fileName: String): File {
        val dir = File(attachmentRoot, reportId).ensure()
        return File(dir, "${System.currentTimeMillis()}_${fileName.sanitized()}")
    }

    fun newExportFile(fileName: String): File = File(exportRoot.ensure(), fileName.sanitized())

    fun deleteQuietly(relativePath: String) {
        runCatching { resolve(relativePath).delete() }
    }

    fun sizeOf(relativePath: String): Long = resolve(relativePath).let { if (it.exists()) it.length() else 0L }

    private fun File.ensure(): File = apply { if (!exists()) mkdirs() }

    private fun String.sanitized(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    private companion object {
        const val MEDIA = "media"
        const val ATTACHMENTS = "attachments"
        const val EXPORTS = "exports"
    }
}
