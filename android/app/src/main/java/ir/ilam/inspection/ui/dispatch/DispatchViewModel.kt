package ir.ilam.inspection.ui.dispatch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.data.model.OutputFormat
import ir.ilam.inspection.data.model.ReportDetail
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.export.ShareUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DispatchState(
    val unit: DispatchUnit = DispatchUnit.SALES,
    val includeReportForm: Boolean = true,
    val mediaIds: Set<String> = emptySet(),
    val attachmentIds: Set<String> = emptySet(),
    val note: String = "",
    val format: OutputFormat = OutputFormat.PDF,
    /** Manager only: both formats plus every ticked document, in one send. */
    val fullBundle: Boolean = false,
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

    /** The file store the item list needs to render its previews. */
    val files = container.fileStore

    /** A manager is offered every document category, an expert only what exists. */
    val isManager: StateFlow<Boolean> = container.settingsRepository.settings
        .map { it.role == UserRole.MANAGER }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val builder = DispatchBuilder(container)

    private val _state = MutableStateFlow(DispatchState())
    val state: StateFlow<DispatchState> = _state.asStateFlow()

    fun setUnit(unit: DispatchUnit) = _state.update { it.copy(unit = unit) }
    fun setFormat(format: OutputFormat) = _state.update { it.copy(format = format) }
    fun setNote(note: String) = _state.update { it.copy(note = note) }
    fun toggleReportForm() = _state.update { it.copy(includeReportForm = !it.includeReportForm) }
    fun toggleFullBundle() = _state.update { it.copy(fullBundle = !it.fullBundle) }
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
            val bundle = builder.build(report, current, expertName, context)

            if (bundle.isEmpty && !bundle.viaPrintSheet) {
                _state.update { it.copy(busy = false, message = R.string.export_failed) }
                return@launch
            }

            // The hand-off is on the record even when the report itself went
            // out through the print sheet: something reached the unit.
            container.contentRepository.logDispatch(
                reportId = reportId,
                unit = current.unit,
                includedItemIds = current.mediaIds.toList() + current.attachmentIds.toList(),
                note = current.note,
                format = current.format
            )

            if (bundle.files.isNotEmpty()) {
                ShareUtil.shareMany(context, bundle.files)
            }
            _state.update {
                it.copy(
                    busy = false,
                    message = if (bundle.viaPrintSheet) {
                        R.string.export_via_print_dialog
                    } else {
                        R.string.dispatch_done
                    }
                )
            }
        }
    }

    private fun Set<String>.toggle(id: String): Set<String> =
        if (contains(id)) this - id else this + id
}
