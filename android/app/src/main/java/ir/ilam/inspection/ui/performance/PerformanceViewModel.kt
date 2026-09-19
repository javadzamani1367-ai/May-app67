package ir.ilam.inspection.ui.performance

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.ilam.inspection.R
import ir.ilam.inspection.data.AppContainer
import ir.ilam.inspection.data.repo.PerformanceReport
import ir.ilam.inspection.data.repo.PerformanceSource
import ir.ilam.inspection.export.PdfOutcome
import ir.ilam.inspection.export.PerformanceExporter
import ir.ilam.inspection.export.ShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PerformanceState(
    val busy: Boolean = false,
    val report: PerformanceReport? = null,
    val messageRes: Int? = null
) {
    val fromServer: Boolean get() = report?.source == PerformanceSource.SERVER
}

/** The manager's performance report, and the three files it turns into. */
class PerformanceViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(PerformanceState())
    val state: StateFlow<PerformanceState> = _state.asStateFlow()

    /** Defaults to the last thirty days, which is what gets asked for. */
    private var from: Long = System.currentTimeMillis() - THIRTY_DAYS
    private var to: Long = System.currentTimeMillis()

    init {
        load()
    }

    fun setRange(fromMillis: Long, toMillis: Long) {
        from = fromMillis
        to = toMillis
        load()
    }

    fun clearMessage() = _state.update { it.copy(messageRes = null) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val report = container.performanceRepository.load(from, to)
            _state.update { it.copy(busy = false, report = report) }
        }
    }

    fun exportExcel(context: Context) = export(context) { exporter, report ->
        withContext(Dispatchers.IO) { exporter.excel(report, baseName()) }
    }

    fun exportWord(context: Context) = export(context) { exporter, report ->
        withContext(Dispatchers.IO) { exporter.word(report, baseName()) }
    }

    /**
     * PDF goes through the same WebView path as the case report, so the table
     * is laid out by something that understands right-to-left text rather than
     * by a drawing library that would need every cell positioned by hand.
     */
    fun exportPdf(context: Context) {
        val report = _state.value.report ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val exporter = PerformanceExporter(context, container.fileStore)
            val outcome = runCatching {
                container.pdfExporter.export(exporter.html(report), baseName(), context)
            }.getOrDefault(PdfOutcome.Failed)
            _state.update { it.copy(busy = false) }
            when (outcome) {
                is PdfOutcome.Saved -> ShareUtil.share(context, outcome.file)
                PdfOutcome.HandedToPrinter ->
                    _state.update { it.copy(messageRes = R.string.export_via_print_dialog) }
                PdfOutcome.Failed ->
                    _state.update { it.copy(messageRes = R.string.export_failed) }
            }
        }
    }

    private fun export(
        context: Context,
        block: suspend (PerformanceExporter, PerformanceReport) -> File
    ) {
        val report = _state.value.report ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val exporter = PerformanceExporter(context, container.fileStore)
            val file = runCatching { block(exporter, report) }.getOrNull()
            _state.update { it.copy(busy = false) }
            if (file == null) {
                _state.update { it.copy(messageRes = R.string.export_failed) }
            } else {
                ShareUtil.share(context, file)
            }
        }
    }

    private fun baseName(): String = "performance-$from-$to"

    private companion object {
        const val THIRTY_DAYS = 30L * 24 * 60 * 60 * 1000
    }
}
