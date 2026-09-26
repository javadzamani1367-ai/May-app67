package ir.ilam.inspection.ui.performance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianDate
import ir.ilam.inspection.util.PersianNumbers

private val PERIODS = listOf(7, 30, 90, 365)

/**
 * Manager only: how the units are performing, one card each, and the three
 * files the report can leave as. Which source produced the figures is said
 * plainly: one phone's rows are not the whole province.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PerformanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContainer = context.container
    val viewModel: PerformanceViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(appContainer) { PerformanceViewModel(it) } }
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var period by remember { mutableIntStateOf(30) }

    LaunchedEffect(state.messageRes) {
        if (state.messageRes != null) {
            kotlinx.coroutines.delay(5_000)
            viewModel.clearMessage()
        }
    }

    val report = state.report
    Scaffold(
        topBar = {
            TavanTopBar(
                title = stringResource(R.string.performance_title),
                subtitle = report?.let {
                    stringResource(R.string.performance_period, PersianDate.format(it.from), PersianDate.format(it.to))
                },
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PERIODS.forEach { days ->
                    ToneChip(
                        text = stringResource(R.string.perf_period_days, PersianNumbers.toPersian(days)),
                        selected = period == days,
                        tone = Tone.ACCENT,
                        onClick = {
                            period = days
                            val now = System.currentTimeMillis()
                            viewModel.setRange(now - days * DAY_MILLIS, now)
                        }
                    )
                }
            }
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))

            StatusBadge(
                text = stringResource(
                    if (state.fromServer) R.string.performance_source_server else R.string.performance_source_local
                ),
                tone = if (state.fromServer) Tone.INFO else Tone.WARNING,
                icon = if (state.fromServer) Icons.Filled.Cloud else Icons.Filled.PhoneAndroid,
                modifier = Modifier.padding(vertical = Spacing.sm)
            )
            state.messageRes?.let {
                Text(stringResource(it), color = Tavan.colors.danger.strong, style = MaterialTheme.typography.bodyMedium)
            }

            report?.rows?.forEach { UnitCard(it) }

            SectionCard(title = stringResource(R.string.perf_exports), icon = Icons.Filled.FileDownload, tone = Tone.SUCCESS) {
                PrimaryButton(
                    text = stringResource(R.string.performance_excel),
                    onClick = { viewModel.exportExcel(context) },
                    enabled = report != null,
                    busy = state.busy,
                    icon = Icons.Filled.TableView,
                    tone = Tone.SUCCESS,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    SecondaryButton(
                        text = stringResource(R.string.performance_word),
                        onClick = { viewModel.exportWord(context) },
                        enabled = report != null && !state.busy,
                        icon = Icons.Filled.Description,
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryButton(
                        text = stringResource(R.string.performance_pdf),
                        onClick = { viewModel.exportPdf(context) },
                        enabled = report != null && !state.busy,
                        icon = Icons.Filled.PictureAsPdf,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
