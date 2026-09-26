package ir.ilam.inspection.ui.visit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.Completion
import ir.ilam.inspection.ui.common.BottomActionBar
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers
import ir.ilam.inspection.util.TrackingCode

/**
 * Field completion as a workflow. The stepper on the header says where the
 * expert is, which steps are finished and which still need something; the
 * bar at the bottom always holds the way forward. Leaving mid-way is
 * expected: everything is already saved.
 */
@Composable
fun VisitScreen(reportId: String, onBack: () -> Unit, onFinished: (String) -> Unit) {
    val container = LocalContext.current.container
    val viewModel: VisitViewModel = viewModel(
        key = "visit-$reportId",
        factory = remember(reportId) {
            ContainerViewModelFactory(container) { VisitViewModel(it, reportId) }
        }
    )
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    LaunchedEffect(finished) {
        if (finished) onFinished(reportId)
    }

    val current = detail
    val visitStep = VisitStep.entries[step]
    Scaffold(
        topBar = {
            TavanTopBar(
                title = stringResource(visitStep.title),
                subtitle = listOfNotNull(
                    current?.report?.displayCode?.let { TrackingCode.forDisplay(it) },
                    stringResource(
                        R.string.visit_progress,
                        PersianNumbers.toPersian(step + 1),
                        PersianNumbers.toPersian(VISIT_STEP_COUNT)
                    )
                ).joinToString(" · "),
                onBack = onBack,
                below = current?.let { { WorkflowStepper(current = step, detail = it, onStep = viewModel::goToStep) } }
            )
        },
        bottomBar = {
            if (current != null) {
                BottomActionBar {
                    if (step > 0) {
                        SecondaryButton(
                            text = stringResource(R.string.action_previous),
                            onClick = viewModel::previous,
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (visitStep != VisitStep.REVIEW) {
                        PrimaryButton(
                            text = stringResource(R.string.action_next),
                            onClick = viewModel::next,
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            modifier = Modifier.weight(1.3f)
                        )
                    } else {
                        PrimaryButton(
                            text = stringResource(R.string.action_finish),
                            onClick = viewModel::finish,
                            icon = Icons.Filled.TaskAlt,
                            busy = busy,
                            enabled = Completion.isComplete(current),
                            tone = Tone.SUCCESS,
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (current == null) {
            Text(text = stringResource(R.string.value_empty), modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
        ) {
            when (visitStep) {
                VisitStep.LOCATION -> LocationStep(current, viewModel)
                VisitStep.TECHNICAL -> TechnicalStep(current, viewModel)
                VisitStep.DEVICES -> DevicesStep(current, viewModel)
                VisitStep.MEDIA -> MediaStep(current, viewModel)
                VisitStep.OWNER -> OwnerStep(current, viewModel)
                VisitStep.REVIEW -> ReviewStep(current, onStep = viewModel::goToStep)
            }
        }
    }

    if (missing.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMissing,
            icon = { Icon(Icons.Filled.Cancel, contentDescription = null, tint = Tavan.colors.danger.strong) },
            title = { Text(stringResource(R.string.complete_missing_title)) },
            text = {
                Column {
                    missing.forEach {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            Icon(
                                Icons.Filled.Cancel,
                                contentDescription = null,
                                tint = Tavan.colors.danger.strong,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                stringResource(it),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMissing) { Text(stringResource(R.string.action_confirm)) }
            }
        )
    }
}
