package ir.ilam.inspection.ui.dispatch

import android.content.Context
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.model.MediaType
import ir.ilam.inspection.data.model.OutputFormat
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.export.PdfOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What a dispatch actually hands over.
 *
 * The report form carries the case and its photos. Everything else that was
 * ticked — miner logs, commission minutes, bills, letters, and the videos —
 * cannot be embedded in a PDF or a Word file, so it travels beside the report
 * as its own file. Before this, ticking such a document quietly sent nothing,
 * which is the worst possible failure for a hand-off to a legal unit.
 */
data class DispatchBundle(
    val files: List<File>,
    /** True when the PDF went to the system print sheet and never came back. */
    val viaPrintSheet: Boolean
) {
    val isEmpty: Boolean get() = files.isEmpty()
}

class DispatchBuilder(private val container: AppContainer) {

    suspend fun build(
        detail: ReportDetail,
        state: DispatchState,
        context: Context
    ): DispatchBundle {
        val baseName = (detail.report.displayCode ?: detail.report.id.take(8)) +
            "-" + state.unit.code.toString()
        val files = mutableListOf<File>()
        var viaPrintSheet = false

        // The full copy is both formats: a unit that can only open one of them
        // should not have to ask for the other.
        val wantsPdf = state.fullBundle || state.format == OutputFormat.PDF
        val wantsWord = state.fullBundle || state.format == OutputFormat.WORD

        if (state.includeReportForm && wantsPdf) {
            when (val outcome = pdf(detail, state, baseName, context)) {
                is PdfOutcome.Saved -> files += outcome.file
                else -> viaPrintSheet = true
            }
        }
        if (state.includeReportForm && wantsWord) {
            word(detail, state, baseName)?.let { files += it }
        }

        files += selectedFiles(detail, state)
        return DispatchBundle(files = files, viaPrintSheet = viaPrintSheet)
    }

    /**
     * The documents and videos that were ticked. A row whose file has gone
     * missing is skipped rather than failing the whole dispatch — the rest of
     * the hand-off is still worth sending.
     */
    private fun selectedFiles(detail: ReportDetail, state: DispatchState): List<File> {
        val store = container.fileStore
        val videos = detail.media
            .filter { it.type == MediaType.VIDEO.code && it.id in state.mediaIds }
            .map { store.resolve(it.filePath) }
        val documents = detail.attachments
            .filter { it.id in state.attachmentIds }
            .map { store.resolve(it.filePath) }
        return (documents + videos).filter { it.exists() }
    }

    private suspend fun pdf(
        detail: ReportDetail,
        state: DispatchState,
        baseName: String,
        context: Context
    ): PdfOutcome {
        val html = container.htmlReportBuilder.build(
            detail = detail,
            selectedMediaIds = state.mediaIds,
            selectedAttachmentIds = state.attachmentIds,
            dispatchNote = state.note
        )
        return runCatching { container.pdfExporter.export(html, baseName, context) }
            .getOrDefault(PdfOutcome.Failed)
    }

    private suspend fun word(
        detail: ReportDetail,
        state: DispatchState,
        baseName: String
    ): File? = runCatching {
        withContext(Dispatchers.IO) {
            container.wordExporter.export(
                detail = detail,
                fileName = baseName,
                    selectedMediaIds = state.mediaIds,
                selectedAttachmentIds = state.attachmentIds,
                dispatchNote = state.note
            )
        }
    }.getOrNull()
}
