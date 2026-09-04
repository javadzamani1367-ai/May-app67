package ir.ilam.inspection.export

import android.content.Context
import android.print.PdfPrint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import ir.ilam.inspection.util.FileStore
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * HTML to PDF through the platform's own print pipeline. No PDF library is
 * linked in: WebView already lays out right-to-left Persian text correctly,
 * which is exactly what a third party engine tends to get wrong.
 */
class PdfExporter(private val context: Context, private val files: FileStore) {

    /** Runs the WebView on the main thread and returns when the file is written. */
    suspend fun export(html: String, fileName: String): File? = withContext(Dispatchers.Main) {
        val target = files.newExportFile(ensurePdfExtension(fileName))
        val webView = WebView(context)
        webView.settings.apply {
            allowFileAccess = true
            allowContentAccess = true
            javaScriptEnabled = false
            loadWithOverviewMode = false
        }
        val loaded = suspendCancellableCoroutine { continuation ->
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean = true
            }
            webView.loadDataWithBaseURL(
                "file://" + files.resolve("").absolutePath + "/",
                html,
                "text/html",
                "UTF-8",
                null
            )
        }
        if (!loaded) return@withContext null

        val written = suspendCancellableCoroutine { continuation ->
            PdfPrint(PdfPrint.a4()).print(
                webView.createPrintDocumentAdapter(target.nameWithoutExtension),
                target
            ) { file ->
                if (continuation.isActive) continuation.resume(file)
            }
        }
        webView.destroy()
        written
    }

    private fun ensurePdfExtension(fileName: String): String =
        if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
}
