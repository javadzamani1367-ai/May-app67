package android.print

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Prints a `PrintDocumentAdapter` straight to a file, with no print dialog.
 *
 * This class lives in `android.print` on purpose: the constructors of
 * `LayoutResultCallback` and `WriteResultCallback` are package private, so the
 * only way to drive the adapter directly is from inside that package. Without
 * it the expert would have to walk through the system print sheet for every
 * report — unacceptable in the field.
 */
class PdfPrint(private val attributes: PrintAttributes) {

    fun print(adapter: PrintDocumentAdapter, target: File, onResult: (File?) -> Unit) {
        adapter.onLayout(
            null,
            attributes,
            null,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    val descriptor = openDescriptor(target)
                    if (descriptor == null) {
                        onResult(null)
                        return
                    }
                    adapter.onWrite(
                        arrayOf(PageRange.ALL_PAGES),
                        descriptor,
                        CancellationSignal(),
                        object : PrintDocumentAdapter.WriteResultCallback() {
                            override fun onWriteFinished(pages: Array<out PageRange>?) {
                                close(descriptor)
                                onResult(if ((pages?.size ?: 0) > 0) target else null)
                            }

                            override fun onWriteFailed(error: CharSequence?) {
                                close(descriptor)
                                onResult(null)
                            }
                        }
                    )
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    onResult(null)
                }
            },
            Bundle()
        )
    }

    private fun openDescriptor(target: File): ParcelFileDescriptor? = runCatching {
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        target.createNewFile()
        ParcelFileDescriptor.open(
            target,
            ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE
        )
    }.getOrNull()

    private fun close(descriptor: ParcelFileDescriptor) {
        runCatching { descriptor.close() }
    }

    companion object {
        /** A4 at 600 dpi with the margins the printed form expects. */
        fun a4(): PrintAttributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
    }
}
