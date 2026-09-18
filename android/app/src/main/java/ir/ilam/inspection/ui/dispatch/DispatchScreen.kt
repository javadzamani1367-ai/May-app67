package ir.ilam.inspection.ui.dispatch

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
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
import ir.ilam.inspection.data.model.DispatchUnit
import ir.ilam.inspection.data.model.OutputFormat
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.DropdownField
import ir.ilam.inspection.ui.common.dispatchUnitLabel
import ir.ilam.inspection.ui.common.outputFormatLabel
import ir.ilam.inspection.data.repo.SnippetFields
import ir.ilam.inspection.ui.common.SnippetField

/** Choose a unit, tick what it receives, pick the format, share the result. */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dispatch_title)) },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            state.message?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            DropdownField(
                label = stringResource(R.string.dispatch_unit),
                options = DispatchUnit.entries.toList(),
                selected = state.unit,
                optionLabel = { dispatchUnitLabel(it) },
                onSelect = viewModel::setUnit
            )
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
            SnippetField(
                label = stringResource(R.string.dispatch_note),
                value = state.note,
                onValueChange = viewModel::setNote,
                fieldKey = SnippetFields.DISPATCH_NOTE,
                multiline = true
            )
            DropdownField(
                label = stringResource(R.string.dispatch_format),
                options = OutputFormat.entries.toList(),
                selected = state.format,
                optionLabel = { outputFormatLabel(it) },
                onSelect = viewModel::setFormat
            )
            Button(
                onClick = { viewModel.generate(context) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
            ) {
                Text(stringResource(R.string.dispatch_generate))
            }
        }
    }
}
