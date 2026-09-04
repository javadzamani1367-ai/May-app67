package ir.ilam.inspection.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.common.dispatchUnitLabel
import ir.ilam.inspection.ui.common.outputFormatLabel
import ir.ilam.inspection.ui.common.reportTypeLabel
import ir.ilam.inspection.ui.common.statusLabel
import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.data.model.OutputFormat
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.TrackingCode

/** The whole case file: summary, documents, exports and dispatch history. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseDetailScreen(
    reportId: String,
    onBack: () -> Unit,
    onContinueVisit: (String) -> Unit,
    onDispatch: (String) -> Unit
) {
    val context = LocalContext.current
    val appContainer = context.container
    val viewModel: CaseDetailViewModel = viewModel(
        key = "case-$reportId",
        factory = remember(reportId) {
            ContainerViewModelFactory(appContainer) { CaseDetailViewModel(it, reportId) }
        }
    )
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val dispatches by viewModel.dispatches.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(TrackingCode.forDisplay(detail?.report?.displayCode))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        val current = detail
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            message?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            if (current == null) return@Column

            SectionCard(title = stringResource(R.string.form_section_1)) {
                Column {
                    ValueRow(
                        stringResource(R.string.form_report_type),
                        reportTypeLabel(current.report.reportType)
                    )
                    ValueRow(
                        stringResource(R.string.form_status),
                        statusLabel(current.report.status)
                    )
                    ValueRow(
                        stringResource(R.string.form_report_date),
                        PersianDate.format(current.report.reportDate)
                    )
                    ValueRow(
                        stringResource(R.string.form_visit_date),
                        current.report.visitDate?.let { PersianDate.format(it) }
                    )
                    ValueRow(stringResource(R.string.field_county), current.report.county)
                    ValueRow(stringResource(R.string.field_address), current.report.address)
                    ValueRow(
                        stringResource(R.string.field_subscription_number),
                        PersianNumbers.toPersian(current.report.subscriptionNumber)
                    )
                    ValueRow(stringResource(R.string.field_owner_name), current.report.ownerName)
                    ValueRow(
                        stringResource(R.string.device_count),
                        PersianNumbers.toPersian(current.deviceCount)
                    )
                    ValueRow(
                        stringResource(R.string.stats_total_power),
                        PersianNumbers.grouped(current.totalPower)
                    )
                    ValueRow(
                        stringResource(R.string.media_count),
                        PersianNumbers.toPersian(current.media.size)
                    )
                }
            }

            AttachmentSection(detail = current, viewModel = viewModel)

            SectionCard(title = stringResource(R.string.dispatch_history)) {
                Column {
                    if (dispatches.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dispatch_history_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    dispatches.forEach { dispatch ->
                        ValueRow(
                            label = dispatchUnitLabel(DispatchUnit.of(dispatch.unit)) + " - " +
                                outputFormatLabel(OutputFormat.of(dispatch.outputFormat)),
                            value = PersianDate.format(dispatch.dispatchedAt)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.exportPdf(context) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.export_pdf)) }
                OutlinedButton(
                    onClick = { viewModel.exportWord(context) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.export_word)) }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onDispatch(reportId) },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.dispatch_title)) }
                if (current.report.status == ReportStatus.VISITED.code) {
                    OutlinedButton(
                        onClick = viewModel::archive,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_archive)) }
                } else {
                    OutlinedButton(
                        onClick = { onContinueVisit(reportId) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.visit_title)) }
                }
            }
        }
    }
}
