package ir.ilam.inspection.ui.visit

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.util.PersianNumbers

private val STEP_TITLES = listOf(
    R.string.visit_step_location,
    R.string.visit_step_owner,
    R.string.visit_step_technical,
    R.string.visit_step_devices,
    R.string.visit_step_media
)

/**
 * Field completion in five steps with a progress bar. Leaving mid-way is
 * expected: everything is already saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(finished) {
        if (finished) onFinished(reportId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(STEP_TITLES[step])) },
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LinearProgressIndicator(
                progress = { (step + 1f) / VISIT_STEP_COUNT },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(
                    R.string.visit_step_of,
                    PersianNumbers.toPersian(step + 1),
                    PersianNumbers.toPersian(VISIT_STEP_COUNT)
                ),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (current == null) {
                Text(
                    text = stringResource(R.string.value_empty),
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    when (step) {
                        0 -> LocationStep(current, viewModel)
                        1 -> OwnerStep(current, viewModel)
                        2 -> TechnicalStep(current, viewModel)
                        3 -> DevicesStep(current, viewModel)
                        else -> MediaStep(current, viewModel)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = viewModel::previous,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.action_previous)) }
                    }
                    if (step < VISIT_STEP_COUNT - 1) {
                        Button(
                            onClick = viewModel::next,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.action_next)) }
                    } else {
                        Button(
                            onClick = viewModel::finish,
                            modifier = Modifier.weight(1f)
                        ) { Text(stringResource(R.string.action_finish)) }
                    }
                }
            }
        }
    }

    if (missing.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissMissing,
            title = { Text(stringResource(R.string.complete_missing_title)) },
            text = {
                Column {
                    missing.forEach { Text("• " + stringResource(it)) }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissMissing) {
                    Text(stringResource(R.string.action_confirm))
                }
            }
        )
    }
}
