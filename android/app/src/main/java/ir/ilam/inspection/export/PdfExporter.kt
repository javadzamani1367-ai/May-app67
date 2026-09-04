package ir.ilam.inspection.export

import android.content.Context
import android.print.PdfPrint
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import ir.ilam.inspection.util.FileStore
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** What came of a PDF request. */
sealed class PdfOutcome {
    /** Written straight to a file, ready to share. */
    data class Saved(val file: File) : PdfOutcome()

    /** The platform refused the silent path; the system print sheet is open. */
    object HandedToPrinter : PdfOutcome()

    object Failed : PdfOutcome()
}

/**
 * HTML to PDF through the platform's own print pipeline. No PDF library is
 * linked in: WebView already lays out right-to-left Persian text correctly,
 * which is exactly what a third party engine tends to get wrong.
 */
class PdfExporter(private val appContext: Context, private val files: FileStore) {

    /** Held while the system print sheet is up; destroying it would blank the job. */
    private var printingWebView: WebView? = null

    /**
     * [context] should be the activity: the fallback opens the system print
     * sheet, which an application context cannot do.
     */
    suspend fun export(html: String, fileName: String, context: Context = appContext): PdfOutcome =
        withContext(Dispatchers.Main) {
            val target = files.newExportFile(ensurePdfExtension(fileName))
            val webView = createWebView(context)
            if (!load(webView, html)) {
                webView.destroy()
                return@withContext PdfOutcome.Failed
            }

            // The direct adapter path drives the print machinery without a
            // dialog. It relies on callback constructors that some builds
            // refuse, so a failure here is expected, not exceptional.
            val written = runCatching {
                suspendCancellableCoroutine { continuation ->
                    PdfPrint(PdfPrint.a4()).print(
                        webView.createPrintDocumentAdapter(target.nameWithoutExtension),
                        target
                    ) { file ->
                        if (continuation.isActive) continuation.resume(file)
                    }
                }
            }.getOrNull()

            if (written != null) {
                webView.destroy()
                return@withContext PdfOutcome.Saved(written)
            }

            if (handToPrinter(context, webView, target.nameWithoutExtension)) {
                PdfOutcome.HandedToPrinter
            } else {
                webView.destroy()
                PdfOutcome.Failed
            }
        }

    private fun createWebView(context: Context): WebView = WebView(context).apply {
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.javaScriptEnabled = false
        settings.loadWithOverviewMode = false
    }

    private suspend fun load(webView: WebView, html: String): Boolean =
        suspendCancellableCoroutine { continuation ->
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

    /** The system print sheet, where the expert picks “save as PDF”. */
    private fun handToPrinter(context: Context, webView: WebView, jobName: String): Boolean =
        runCatching {
            val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: return@runCatching false
            printingWebView = webView
            manager.print(
                jobName,
                webView.createPrintDocumentAdapter(jobName),
                PdfPrint.a4()
            )
            true
        }.getOrDefault(false)

    private fun ensurePdfExtension(fileName: String): String =
        if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
}
