package ir.minerfinder.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Writes a file to the export cache and hands it to Android's share sheet. */
object Share {
    fun text(context: Context, fileName: String, mime: String, content: String, chooserTitle: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
