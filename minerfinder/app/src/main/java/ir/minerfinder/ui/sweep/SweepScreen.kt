package ir.minerfinder.ui.sweep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.minerfinder.R
import ir.minerfinder.core.direction.SweepAnalyzer
import ir.minerfinder.graph
import ir.minerfinder.scan.SweepSignal
import ir.minerfinder.ui.common.BigButton
import ir.minerfinder.ui.common.Fa
import ir.minerfinder.ui.common.Field
import ir.minerfinder.ui.common.Header
import ir.minerfinder.ui.common.SecondaryButton
import ir.minerfinder.ui.common.Section
import ir.minerfinder.ui.common.rememberPermissionAsker
import ir.minerfinder.ui.search.scanPermissions
import ir.minerfinder.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SweepScreen() {
    val graph = LocalContext.current.graph
    val running by graph.scan.running.collectAsState()
    val heading by graph.heading.heading.collectAsState()
    val asker = rememberPermissionAsker()
    val scope = rememberCoroutineScope()
    val analyzer = remember { SweepAnalyzer(36) }
    var sweeping by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        graph.heading.acquire()
        onDispose { graph.heading.release() }
    }
    LaunchedEffect(sweeping) {
        while (sweeping) {
            val h = graph.heading.heading.value
            val s = SweepSignal.current(graph)
            if (h != null && s != null) {
                analyzer.add(h, s)
                version++
            }
            delay(100)
        }
    }

    // Recomputed each time a sample is added (version changes).
    val bins = remember(version) { analyzer.bins() }
    val coverage = remember(version) { analyzer.coverage() }
    val estimate = remember(version) { analyzer.estimate() }

    Column(Modifier.fillMaxSize()) {
        Header(stringResource(R.string.tab_sweep), stringResource(R.string.sweep_subtitle))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!graph.heading.isAvailable) Text(stringResource(R.string.sweep_no_sensor), color = Palette.Hot)
            if (!running) {
                BigButton(stringResource(R.string.scan_start), { asker.ask(scanPermissions()) { graph.scan.start() } })
            }
            Section(stringResource(R.string.sweep_chart)) {
                PolarChart(bins, heading, estimate?.bearingDeg)
                Field(stringResource(R.string.sweep_heading), heading?.let { Fa.num(it) + "°" } ?: "—")
                Field(stringResource(R.string.sweep_coverage), Fa.num(coverage * 100) + stringResource(R.string.unit_percent))
            }
            Text(stringResource(R.string.sweep_help), color = Palette.Muted)
            if (sweeping) {
                BigButton(stringResource(R.string.sweep_finish), { sweeping = false }, color = Palette.Warm)
            } else {
                BigButton(stringResource(R.string.sweep_start), enabled = running && graph.heading.isAvailable, onClick = {
                    analyzer.reset()
                    message = null
                    version++
                    sweeping = true
                })
            }
            Section(stringResource(R.string.sweep_result)) {
                if (estimate == null) {
                    Text(stringResource(R.string.sweep_need_more), color = Palette.Muted)
                } else {
                    Field(stringResource(R.string.sweep_bearing), Fa.num(estimate.bearingDeg) + "°", Palette.Hot)
                    Field(stringResource(R.string.sweep_sharpness), Fa.num(estimate.sharpness * 100) + stringResource(R.string.unit_percent))
                    if (estimate.sharpness < 0.15) Text(stringResource(R.string.sweep_flat), color = Palette.Warm)
                    SecondaryButton(stringResource(R.string.sweep_save), enabled = running, onClick = {
                        scope.launch {
                            val ok = graph.recorder.saveBearing(estimate.bearingDeg, estimate.sharpness, estimate.peakScore)
                            message = if (ok) R.string.sweep_saved else R.string.sweep_no_location
                        }
                    })
                }
                message?.let { Text(stringResource(it), color = if (it == R.string.sweep_saved) Palette.Ok else Palette.Hot) }
            }
        }
    }
}
