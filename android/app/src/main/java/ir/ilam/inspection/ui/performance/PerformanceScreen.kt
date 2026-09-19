package ir.ilam.inspection.ui.performance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.UnitPerformance
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.dispatchUnitLabel
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

private val COLUMN_WIDTHS = listOf(120, 90, 90, 100, 100, 90, 120, 130)

/**
 * Manager only: how the units are performing, and the three files it can
 * leave as. The table scrolls sideways rather than shrinking its text — the
 * figures have to stay readable on a phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContainer = context.container
    val viewModel: PerformanceViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(appContainer) { PerformanceViewModel(it) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val headers = stringArrayResource(R.array.performance_headers)

    LaunchedEffect(state.messageRes) {
        if (state.messageRes != null) {
            kotlinx.coroutines.delay(5_000)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.performance_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }
            state.messageRes?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            val report = state.report
            SectionCard(title = stringResource(R.string.performance_title)) {
                Column {
                    Text(
                        text = report?.let {
                            stringResource(
                                R.string.performance_period,
                                PersianDate.format(it.from),
                                PersianDate.format(it.to)
                            )
                        } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Which source produced these numbers matters: one phone's
                    // rows are not the whole province.
                    Text(
                        text = stringResource(
                            if (state.fromServer) {
                                R.string.performance_source_server
                            } else {
                                R.string.performance_source_local
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.fromServer) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 12.dp)
                    ) {
                        Column {
                            HeaderRow(headers.toList())
                            report?.rows?.forEach { row ->
                                HorizontalDivider()
                                DataRow(row)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.exportExcel(context) },
                    enabled = report != null && !state.busy,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.performance_excel)) }
                OutlinedButton(
                    onClick = { viewModel.exportWord(context) },
                    enabled = report != null && !state.busy,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.performance_word)) }
                OutlinedButton(
                    onClick = { viewModel.exportPdf(context) },
                    enabled = report != null && !state.busy,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.performance_pdf)) }
            }
        }
    }
}

@Composable
private fun HeaderRow(headers: List<String>) {
    Row {
        headers.forEachIndexed { index, header ->
            Cell(text = header, index = index, bold = true)
        }
    }
}

@Composable
private fun DataRow(row: UnitPerformance) {
    Row {
        Cell(dispatchUnitLabel(row.unit), 0)
        Cell(PersianNumbers.toPersian(row.sent), 1)
        Cell(PersianNumbers.toPersian(row.seen), 2)
        Cell(PersianNumbers.toPersian(row.answered), 3)
        Cell(PersianNumbers.toPersian(row.overdue), 4, warn = row.overdue > 0)
        Cell(PersianNumbers.toPersian("%.1f".format(row.answerRate)) + "٪", 5)
        Cell(PersianNumbers.toPersian("%.1f".format(row.onTimeRate)) + "٪", 6)
        Cell(
            row.averageAnswerHours?.let { PersianNumbers.toPersian("%.1f".format(it)) } ?: "—",
            7
        )
    }
}

@Composable
private fun Cell(text: String, index: Int, bold: Boolean = false, warn: Boolean = false) {
    Text(
        text = text,
        style = if (bold) {
            MaterialTheme.typography.bodyMedium
        } else {
            MaterialTheme.typography.bodySmall
        },
        color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(COLUMN_WIDTHS.getOrElse(index) { 100 }.dp)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}
