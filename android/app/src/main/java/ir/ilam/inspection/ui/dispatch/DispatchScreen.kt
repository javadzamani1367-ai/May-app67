package ir.ilam.inspection.ui.dispatch

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
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import ir.ilam.inspection.data.model.OutputFormat
import ir.ilam.inspection.data.repo.SnippetFields
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.SnippetField
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.outputFormatLabel
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.TrackingCode

/**
 * The hand-off to a unit, in the order it is decided: who receives it, what
 * goes, what they are told and by when, and in which format. The button at
 * the bottom always says how much is about to go.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DispatchScreen(reportId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val appContainer = context.container
    val viewModel: DispatchViewModel = viewModel(
        key = "dispatch-$reportId",
        factory = remember(reportId) {
            ContainerViewModelFactory(appContainer) { DispatchViewModel(it, reportId) }
        }
    )
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected = state.mediaIds.size + state.attachmentIds.size + if (state.includeReportForm) 1 else 0

    Scaffold(
        topBar = {
            TavanTopBar(
                title = stringResource(R.string.dispatch_title),
                subtitle = detail?.report?.displayCode?.let { TrackingCode.forDisplay(it) },
                onBack = onBack
            )
        },
        bottomBar = {
            BottomActionBar(
                note = {
                    val message = state.message
                    val good = message == R.string.dispatch_done || message == R.string.export_via_print_dialog
                    Text(
                        text = message?.let { stringResource(it) }
                            ?: stringResource(R.string.dispatch_selected, PersianNumbers.toPersian(selected)),
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            message == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            good -> Tavan.colors.success.strong
                            else -> Tavan.colors.danger.strong
                        },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            ) {
                PrimaryButton(
                    text = stringResource(R.string.dispatch_generate),
                    onClick = { viewModel.generate(context) },
                    busy = state.busy,
                    enabled = selected > 0,
                    icon = Icons.AutoMirrored.Filled.Send,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { padding ->
        val current = detail
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
        ) {
            SectionCard(
                title = stringResource(R.string.dispatch_unit),
                subtitle = stringResource(R.string.dispatch_unit_hint),
                icon = Icons.Filled.Apartment
            ) {
                UnitPicker(selected = state.unit, onSelect = viewModel::setUnit)
            }
            if (current != null) {
                DispatchItems(
                    detail = current,
                    state = state,
                    files = viewModel.files,
                    isManager = viewModel.isManager,
                    onToggleReportForm = viewModel::toggleReportForm,
                    onToggleFullBundle = viewModel::toggleFullBundle,
                    onToggleMedia = viewModel::toggleMedia,
                    onToggleAttachment = viewModel::toggleAttachment
                )
            }
            SectionCard(
                title = stringResource(R.string.dispatch_note_section),
                icon = Icons.Filled.EditNote,
                tone = Tone.NEUTRAL
            ) {
                SnippetField(
                    label = stringResource(R.string.dispatch_note),
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    fieldKey = SnippetFields.DISPATCH_NOTE,
                    multiline = true
                )
                NumberField(
                    label = stringResource(R.string.dispatch_deadline_days),
                    value = state.deadlineDays,
                    onValueChange = viewModel::setDeadlineDays
                )
                // The usual deadlines, one tap each.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(3, 7, 14, 30).forEach { days ->
                        ToneChip(
                            text = stringResource(R.string.dispatch_days_chip, PersianNumbers.toPersian(days)),
                            selected = state.deadlineDays == days.toString(),
                            tone = Tone.WARNING,
                            onClick = { viewModel.setDeadlineDays(days.toString()) }
                        )
                    }
                }
            }
            SectionCard(
                title = stringResource(R.string.dispatch_format),
                subtitle = stringResource(R.string.dispatch_format_hint),
                icon = Icons.Filled.Description,
                tone = Tone.INFO
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutputFormat.entries.forEach { format ->
                        ToneChip(
                            text = outputFormatLabel(format),
                            selected = state.format == format,
                            tone = Tone.INFO,
                            onClick = { viewModel.setFormat(format) }
                        )
                    }
                }
            }
        }
    }
}
