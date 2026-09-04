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

    fun share(context: Context, file: File, mimeType: String = mimeFor(file)) {
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
