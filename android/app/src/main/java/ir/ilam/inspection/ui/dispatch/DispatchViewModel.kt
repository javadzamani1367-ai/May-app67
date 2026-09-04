package ir.ilam.inspection.ui.dispatch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.data.model.OutputFormat
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.export.PdfOutcome
import ir.ilam.inspection.export.ShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DispatchState(
    val unit: DispatchUnit = DispatchUnit.SALES,
    val includeReportForm: Boolean = true,
    val mediaIds: Set<String> = emptySet(),
    val attachmentIds: Set<String> = emptySet(),
    val note: String = "",
    val format: OutputFormat = OutputFormat.PDF,
    val busy: Boolean = false,
    val message: Int? = null
)

/**
 * Selective hand-off: pick a unit, tick exactly what it should receive, add a
 * note for that dispatch, produce one file and share it. Every send is logged
 * so it stays known what went where, and when.
 */
class DispatchViewModel(
    private val container: AppContainer,
    private val reportId: String
) : ViewModel() {

    val detail: StateFlow<ReportDetail?> = container.reportRepository.observeDetail(reportId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(DispatchState())
    val state: StateFlow<DispatchState> = _state.asStateFlow()

    fun setUnit(unit: DispatchUnit) = _state.update { it.copy(unit = unit) }
    fun setFormat(format: OutputFormat) = _state.update { it.copy(format = format) }
    fun setNote(note: String) = _state.update { it.copy(note = note) }
    fun toggleReportForm() = _state.update { it.copy(includeReportForm = !it.includeReportForm) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    fun toggleMedia(id: String) = _state.update {
        it.copy(mediaIds = it.mediaIds.toggle(id))
    }

    fun toggleAttachment(id: String) = _state.update {
        it.copy(attachmentIds = it.attachmentIds.toggle(id))
    }

    fun generate(context: Context) {
        val current = _state.value
        val selectedCount = current.mediaIds.size + current.attachmentIds.size +
            if (current.includeReportForm) 1 else 0
        if (selectedCount == 0) {
            _state.update { it.copy(message = R.string.dispatch_nothing_selected) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val report = detail.value ?: container.reportRepository.detail(reportId)
            if (report == null) {
                _state.update { it.copy(busy = false, message = R.string.export_failed) }
                return@launch
            }
            val expertName = container.settingsRepository.settings.first().expertName
            val baseName = (report.report.displayCode ?: reportId.take(8)) + "-" +
                current.unit.code.toString()
            val outcome = if (current.format == OutputFormat.PDF) {
                val html = container.htmlReportBuilder.build(
                    detail = report,
                    expertName = expertName,
                    selectedMediaIds = current.mediaIds,
                    selectedAttachmentIds = current.attachmentIds,
                    dispatchNote = current.note
                )
                runCatching { container.pdfExporter.export(html, baseName, context) }
                    .getOrDefault(PdfOutcome.Failed)
            } else {
                val file = runCatching {
                    withContext(Dispatchers.IO) {
                        container.wordExporter.export(
                            detail = report,
                            fileName = baseName,
                            expertName = expertName,
                            selectedMediaIds = current.mediaIds,
                            selectedAttachmentIds = current.attachmentIds,
                            dispatchNote = current.note
                        )
                    }
                }.getOrNull()
                if (file == null) PdfOutcome.Failed else PdfOutcome.Saved(file)
            }

            if (outcome == PdfOutcome.Failed) {
                _state.update { it.copy(busy = false, message = R.string.export_failed) }
                return@launch
            }

            // The document exists either way, so the dispatch is on the record
            // even when the expert saved it through the print sheet.
            container.contentRepository.logDispatch(
                reportId = reportId,
                unit = current.unit,
                includedItemIds = current.mediaIds.toList() + current.attachmentIds.toList(),
                note = current.note,
                format = current.format
            )
            when (outcome) {
                is PdfOutcome.Saved -> {
                    _state.update { it.copy(busy = false, message = R.string.dispatch_done) }
                    ShareUtil.share(context, outcome.file)
                }
                else -> _state.update {
                    it.copy(busy = false, message = R.string.export_via_print_dialog)
                }
            }
        }
    }

    private fun Set<String>.toggle(id: String): Set<String> =
        if (contains(id)) this - id else this + id
}
