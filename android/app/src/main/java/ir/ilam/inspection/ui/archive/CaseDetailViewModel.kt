package ir.ilam.inspection.ui.archive

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.db.AttachmentEntity
import ir.ilam.inspection.data.db.DispatchEntity
import ir.ilam.inspection.data.model.AttachmentCategory
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.export.ShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The case file after the visit: documents, exports and the dispatch log. */
class CaseDetailViewModel(
    private val container: AppContainer,
    private val reportId: String
) : ViewModel() {

    val detail: StateFlow<ReportDetail?> = container.reportRepository.observeDetail(reportId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dispatches: StateFlow<List<DispatchEntity>> =
        container.reportRepository.observeDispatches(reportId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun archive() {
        viewModelScope.launch { container.reportRepository.archive(reportId) }
    }

    fun reopen() {
        viewModelScope.launch { container.reportRepository.reopen(reportId) }
    }

    /** Copies the picked document into private storage, then records it. */
    fun addAttachment(context: Context, uri: Uri, category: AttachmentCategory, title: String, note: String) {
        viewModelScope.launch {
            _busy.value = true
            val stored = withContext(Dispatchers.IO) {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
                val target = container.fileStore.newAttachmentFile(reportId, name)
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target
                }.getOrNull()
            }
            if (stored != null) {
                container.contentRepository.addAttachment(
                    reportId = reportId,
                    file = stored,
                    category = category,
                    title = title,
                    mimeType = context.contentResolver.getType(uri),
                    note = note
                )
            }
            _busy.value = false
        }
    }

    fun removeAttachment(attachment: AttachmentEntity) {
        viewModelScope.launch { container.contentRepository.removeAttachment(attachment) }
    }

    fun exportPdf(context: Context) = export(context) { detail, expertName ->
        val html = container.htmlReportBuilder.build(detail, expertName)
        container.pdfExporter.export(html, fileNameFor(detail))
    }

    fun exportWord(context: Context) = export(context) { detail, expertName ->
        withContext(Dispatchers.IO) {
            container.wordExporter.export(detail, fileNameFor(detail), expertName)
        }
    }

    private fun export(context: Context, block: suspend (ReportDetail, String) -> File?) {
        viewModelScope.launch {
            val current = detail.value ?: container.reportRepository.detail(reportId) ?: return@launch
            _busy.value = true
            val expertName = container.settingsRepository.settings.first().expertName
            val file = runCatching { block(current, expertName) }.getOrNull()
            _busy.value = false
            if (file == null) {
                _message.value = ir.ilam.inspection.R.string.export_failed
            } else {
                ShareUtil.share(context, file)
            }
        }
    }

    private fun fileNameFor(detail: ReportDetail): String =
        (detail.report.displayCode ?: detail.report.id.take(8)).replace(" ", "")
}
