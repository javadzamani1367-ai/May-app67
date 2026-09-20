package ir.ilam.inspection.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import ir.ilam.inspection.R
import java.io.File

/**
 * Sharing goes through the system chooser, which already covers WhatsApp,
 * Telegram, Eitaa, Bale, e-mail and Bluetooth. No per-network integration is
 * needed, and nothing leaves the phone without the expert choosing where.
 */
object ShareUtil {

    /**
     * Returns false when the hand-off could not be started.
     *
     * Wrapped because failing here used to take the whole app down with it,
     * after the report had already been built and the expert was one tap from
     * sending it. A file in a folder FileProvider has no root for throws, and
     * so does a phone with nothing able to receive the type. Neither is worth
     * losing the work over — the caller says so instead.
     */
    fun share(context: Context, file: File, mimeType: String = mimeFor(file)): Boolean =
        runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, context.getString(R.string.action_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.isSuccess

    /**
     * Several files in one hand-off: the report plus the documents and videos
     * that cannot live inside it. The type is narrowed only when every file
     * agrees, otherwise a chooser would hide apps that can take the rest.
     */
    fun shareMany(context: Context, files: List<File>): Boolean {
        if (files.isEmpty()) return false
        if (files.size == 1) return share(context, files.first())

        return runCatching {
            val uris = ArrayList(
                files.map {
                    FileProvider.getUriForFile(context, context.packageName + ".fileprovider", it)
                }
            )
            val types = files.map { mimeFor(it) }.distinct()
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = types.singleOrNull() ?: "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, context.getString(R.string.action_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }.isSuccess
    }

    fun mimeFor(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "mp4" -> "video/mp4"
        "cvz" -> "application/octet-stream"
        else -> "*/*"
    }
}
