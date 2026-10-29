package ir.minerfinder.ui.magnetic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.minerfinder.R
import ir.minerfinder.core.magnetic.MagneticAnalyzer
import ir.minerfinder.graph
import ir.minerfinder.sources.SourceIds
import ir.minerfinder.ui.common.BigButton
import ir.minerfinder.ui.common.CalibrationControls
import ir.minerfinder.ui.common.CalibrationTarget
import ir.minerfinder.ui.common.Fa
import ir.minerfinder.ui.common.Field
import ir.minerfinder.ui.common.Header
import ir.minerfinder.ui.common.LineChart
import ir.minerfinder.ui.common.ScoreBar
import ir.minerfinder.ui.common.Section
import ir.minerfinder.ui.common.rememberPermissionAsker
import ir.minerfinder.ui.search.scanPermissions
import ir.minerfinder.ui.settings.InputPicker
import ir.minerfinder.ui.theme.Palette
import kotlinx.coroutines.flow.filterNotNull

private const val HISTORY = 160

@Composable
fun MagneticScreen() {
    val graph = LocalContext.current.graph
    val running by graph.scan.running.collectAsState()
    val asker = rememberPermissionAsker()
    Column(Modifier.fillMaxSize()) {
        Header(stringResource(R.string.tab_magnetic), stringResource(R.string.magnetic_subtitle))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!running) {
                BigButton(stringResource(R.string.scan_start), { asker.ask(scanPermissions()) { graph.scan.start() } })
            }
            MagnetometerSection(running)
            CoilSection(running)
        }
    }
}

@Composable
private fun MagnetometerSection(running: Boolean) {
    val graph = LocalContext.current.graph
    val source = graph.catalog.magnetometer
    val frame by source.frames.collectAsState()
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(source) {
        source.frames.filterNotNull().collect {
            history.add(it.deltaUt.toFloat())
            if (history.size > HISTORY) history.removeAt(0)
        }
    }
    Section(stringResource(R.string.source_magnetometer)) {
        if (!source.isAvailable()) {
            Text(stringResource(R.string.magnetometer_missing), color = Palette.Hot)
            return@Section
        }
        val f = frame
        if (f == null) {
            Text(stringResource(R.string.spectrum_waiting), color = Palette.Muted)
        } else {
            Field(stringResource(R.string.raw_magnitude), Fa.num(f.magnitude, 1) + " " + stringResource(R.string.unit_ut))
            Field(stringResource(R.string.raw_delta), Fa.num(f.deltaUt, 1) + " " + stringResource(R.string.unit_ut))
            Field(stringResource(R.string.raw_rate), Fa.num(f.rateHz) + " " + stringResource(R.string.unit_hz))
            val ac = f.ac50Ut
            if (ac != null) {
                Field(stringResource(R.string.raw_ac50), Fa.num(ac, 2) + " " + stringResource(R.string.unit_ut))
            } else {
                Text(
                    stringResource(R.string.magnetometer_too_slow, Fa.num(MagneticAnalyzer.MIN_RATE_FOR_50HZ)),
                    color = Palette.Warm,
                )
            }
            ScoreBar(stringResource(R.string.source_score), f.score.toFloat())
        }
        Text(stringResource(R.string.magnetic_delta_chart), color = Palette.Muted)
        LineChart(history, minSpan = 2f)
    }
    CalibrationControls(
        stringResource(R.string.magnetic_calibration), stringResource(R.string.magnetic_calibration_help), 3, running,
        CalibrationTarget({ source.hasCalibration }, { source.startCalibration() }, { source.finishCalibration() }, { source.clearCalibration() }),
    )
}

@Composable
private fun CoilSection(running: Boolean) {
    val graph = LocalContext.current.graph
    val settings by graph.settings.state.collectAsState()
    val source = graph.catalog.coil
    val frame by source.frames.collectAsState()
    val history = remember { mutableStateListOf<Float>() }
    LaunchedEffect(source) {
        source.frames.filterNotNull().collect {
            history.add(it.levelDb.toFloat())
            if (history.size > HISTORY) history.removeAt(0)
        }
    }
    val enabled = settings.isEnabled(SourceIds.COIL)
    Section(stringResource(R.string.source_coil)) {
        Text(stringResource(R.string.coil_help), color = Palette.Muted)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.coil_enable))
            Switch(checked = enabled, onCheckedChange = { on ->
                graph.settings.update { it.copy(disabled = if (on) it.disabled - SourceIds.COIL else it.disabled + SourceIds.COIL) }
                graph.scan.restart()
            })
        }
        if (!enabled) return@Section
        Text(stringResource(R.string.coil_input), color = Palette.Navy)
        InputPicker(settings.coilInputId) { id ->
            graph.settings.update { it.copy(coilInputId = id) }
            graph.scan.restart()
        }
        frame?.let { f ->
            val unit = stringResource(if (f.calibrated) R.string.unit_db_above_base else R.string.unit_dbfs)
            Field(stringResource(R.string.raw_coil_level), Fa.num(f.levelDb, 1) + " " + unit)
            Field(stringResource(R.string.coil_150), Fa.num(f.a150 * 1000, 3))
            Field(stringResource(R.string.coil_250), Fa.num(f.a250 * 1000, 3))
            Field(stringResource(R.string.raw_variation), Fa.num(f.variation * 100) + stringResource(R.string.unit_percent))
            Field(stringResource(R.string.raw_harmonic_ratio), Fa.num(f.harmonicRatio, 2))
            ScoreBar(stringResource(R.string.coil_heavy), (f.heavy * 100).toFloat())
            ScoreBar(stringResource(R.string.coil_steady), (f.steady * 100).toFloat())
            ScoreBar(stringResource(R.string.source_score), f.score.toFloat(), Palette.Hot)
        }
        Text(stringResource(R.string.coil_chart), color = Palette.Muted)
        LineChart(history, minSpan = 3f, color = Palette.Warm)
    }
    if (enabled) {
        CalibrationControls(
            stringResource(R.string.coil_calibration), stringResource(R.string.coil_calibration_help), 5, running,
            CalibrationTarget({ source.hasCalibration }, { source.startCalibration() }, { source.finishCalibration() }, { source.clearCalibration() }),
        )
    }
}
