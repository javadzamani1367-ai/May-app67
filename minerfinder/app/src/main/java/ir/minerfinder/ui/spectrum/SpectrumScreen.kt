package ir.minerfinder.ui.spectrum

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.minerfinder.R
import ir.minerfinder.core.acoustic.AcousticFrame
import ir.minerfinder.graph
import ir.minerfinder.ui.common.BigButton
import ir.minerfinder.ui.common.CalibrationControls
import ir.minerfinder.ui.common.CalibrationTarget
import ir.minerfinder.ui.common.Fa
import ir.minerfinder.ui.common.Field
import ir.minerfinder.ui.common.Header
import ir.minerfinder.ui.common.ScoreBar
import ir.minerfinder.ui.common.Section
import ir.minerfinder.ui.common.rememberPermissionAsker
import ir.minerfinder.ui.search.CenteredNote
import ir.minerfinder.ui.search.scanPermissions
import ir.minerfinder.ui.settings.InputPicker
import ir.minerfinder.ui.theme.Palette

private const val CALIBRATION_SECONDS = 6

@Composable
fun SpectrumScreen() {
    val graph = LocalContext.current.graph
    val source = graph.catalog.audio
    val running by graph.scan.running.collectAsState()
    val frame by source.frames.collectAsState()
    val buffer = remember { SpectrogramBuffer() }
    val asker = rememberPermissionAsker()

    LaunchedEffect(source) {
        source.frames.collect { f -> if (f != null) buffer.push(f) }
    }

    Column(Modifier.fillMaxSize()) {
        Header(stringResource(R.string.tab_spectrum), stringResource(R.string.spectrum_subtitle))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!running) {
                BigButton(stringResource(R.string.scan_start), { asker.ask(scanPermissions()) { graph.scan.start() } })
            }
            Section(stringResource(R.string.spectrum_live)) {
                SpectrumChart(frame)
                Text(stringResource(R.string.spectrum_legend), color = Palette.Muted)
            }
            Section(stringResource(R.string.spectrogram)) {
                Spectrogram(buffer)
            }
            frame?.let { AnalysisSection(it) } ?: CenteredNote(stringResource(R.string.spectrum_waiting))
            CalibrationControls(
                stringResource(R.string.calibration), stringResource(R.string.calibration_help), CALIBRATION_SECONDS, running,
                CalibrationTarget({ source.hasCalibration }, { source.startCalibration() }, { source.finishCalibration() }, { source.clearCalibration() }),
            )
            Section(stringResource(R.string.audio_input)) {
                InputPicker(
                    selected = graph.settings.state.collectAsState().value.audioInputId,
                    onSelect = { id ->
                        graph.settings.update { it.copy(audioInputId = id) }
                        graph.scan.restart()
                    },
                )
            }
        }
    }
}

@Composable
private fun AnalysisSection(f: AcousticFrame) {
    Section(stringResource(R.string.analysis)) {
        val series = f.series
        if (series == null) {
            Field(stringResource(R.string.raw_f0), stringResource(R.string.not_found))
        } else {
            Field(stringResource(R.string.raw_f0), Fa.num(series.fundamentalHz, 1) + " " + stringResource(R.string.unit_hz))
            Field(stringResource(R.string.raw_partials), Fa.int(series.count))
            Field(stringResource(R.string.coverage), Fa.num(series.coverage * 100) + stringResource(R.string.unit_percent))
        }
        Field(stringResource(R.string.peaks_found), Fa.int(f.peaks.size))
        Field(stringResource(R.string.raw_level), Fa.num(f.levelDbfs, 1) + " " + stringResource(R.string.unit_dbfs))
        Field(
            stringResource(R.string.window_filled),
            Fa.num(f.stability.coverage * 100) + stringResource(R.string.unit_percent),
        )
        ScoreBar(stringResource(R.string.score_tonal), (f.tonalScore * 100).toFloat())
        ScoreBar(stringResource(R.string.score_harmonic), (f.harmonicScore * 100).toFloat())
        ScoreBar(stringResource(R.string.score_stability), (f.stability.score * 100).toFloat())
        ScoreBar(stringResource(R.string.source_score), f.score.toFloat(), Palette.Hot)
    }
}
