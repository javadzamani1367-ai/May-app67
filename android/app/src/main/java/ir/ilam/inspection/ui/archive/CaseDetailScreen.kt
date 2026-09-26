package ir.ilam.inspection.ui.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.ApprovalState
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.ColorTag
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.approvalLabel
import ir.ilam.inspection.ui.common.icon
import ir.ilam.inspection.ui.common.reportTypeLabel
import ir.ilam.inspection.ui.common.statusLabel
import ir.ilam.inspection.ui.common.tone
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.ui.theme.colorForReportType
import ir.ilam.inspection.ui.visit.MapPickerDialog
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.TrackingCode

/**
 * The whole case file, as a report: a header that says what the case is and
 * where it stands, the figures that matter, the seven sections of the official
 * form, the evidence, and then what happened next — the manager's decision,
 * the documents that came after, and every hand-off to a unit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaseDetailScreen(
    reportId: String,
    onBack: () -> Unit,
    onContinueVisit: (String) -> Unit,
    onDispatch: (String) -> Unit
) {
    val appContainer = LocalContext.current.container
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
    val locationFix by viewModel.locationFix.collectAsStateWithLifecycle()
    var showMap by remember { mutableStateOf(false) }

    val current = detail
    Scaffold(
        topBar = {
            TavanTopBar(
                title = TrackingCode.forDisplay(current?.report?.displayCode),
                subtitle = current?.let { reportTypeLabel(it.report.reportType) + " · " + statusLabel(it.report.status) },
                onBack = onBack,
                below = current?.let { found ->
                    {
                        val status = ReportStatus.of(found.report.status)
                        val approval = ApprovalState.of(found.report.approvalState)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.md),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ColorTag(
                                text = reportTypeLabel(found.report.reportType),
                                color = colorForReportType(found.report.reportType, dark = true)
                            )
                            StatusBadge(statusLabel(status), tone = status.tone(), icon = status.icon(), solid = true)
                            if (approval != ApprovalState.DRAFT) {
                                StatusBadge(approvalLabel(approval), tone = approval.tone(), icon = approval.icon(), solid = true)
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (current != null) {
                BottomActionBar {
                    PrimaryButton(
                        text = stringResource(R.string.dispatch_title),
                        onClick = { onDispatch(reportId) },
                        icon = Icons.AutoMirrored.Filled.Send,
                        modifier = Modifier.weight(1f)
                    )
                    if (current.report.status == ReportStatus.VISITED.code) {
                        SecondaryButton(
                            text = stringResource(R.string.action_archive),
                            onClick = viewModel::archive,
                            icon = Icons.Filled.Archive,
                            modifier = Modifier.weight(1f)
                        )
                    } else if (current.report.status == ReportStatus.PENDING.code) {
                        SecondaryButton(
                            text = stringResource(R.string.case_continue_visit),
                            onClick = { onContinueVisit(reportId) },
                            icon = Icons.Filled.EditNote,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
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

            CaseOverview(current)
            ApprovalCard(report = current.report, onSubmit = viewModel::submitForApproval, onDecide = viewModel::decide)
            CaseFileSection(current)
            CasePlaceSection(current, locationFix, onOpenMap = { showMap = true })
            CaseOwnerSection(current)
            CaseTechnicalSection(current)
            CaseDevicesSection(current)
            CaseNarrativeSection(current)
            CaseMediaSection(current)
            AttachmentSection(detail = current, viewModel = viewModel)

            SectionCard(
                title = stringResource(R.string.dispatch_history),
                subtitle = stringResource(R.string.dispatch_history_hint),
                icon = Icons.Filled.History,
                trailing = {
                    if (dispatches.isNotEmpty()) StatusBadge(PersianNumbers.toPersian(dispatches.size), tone = Tone.BRAND)
                }
            ) {
                if (dispatches.isEmpty()) {
                    Text(
                        text = stringResource(R.string.dispatch_history_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                dispatches.forEachIndexed { index, dispatch ->
                    DispatchHistoryRow(dispatch = dispatch, showDivider = index > 0)
                }
            }
        }
    }

    val report = current?.report
    if (showMap && report?.latitude != null && report.longitude != null) {
        MapPickerDialog(
            initialLatitude = report.latitude,
            initialLongitude = report.longitude,
            onDismiss = { showMap = false },
            onConfirm = { _, _ -> showMap = false },
            pickable = false
        )
    }
}
