package ir.minerfinder.ui.search

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.minerfinder.R
import ir.minerfinder.core.engine.Detection
import ir.minerfinder.core.sensor.BaseSource
import ir.minerfinder.core.sensor.SensorSource
import ir.minerfinder.core.sensor.SourceStatus
import ir.minerfinder.graph
import ir.minerfinder.ui.common.BigButton
import ir.minerfinder.ui.common.Fa
import ir.minerfinder.ui.common.Field
import ir.minerfinder.ui.common.Header
import ir.minerfinder.ui.common.ScoreBar
import ir.minerfinder.ui.common.Section
import ir.minerfinder.ui.common.rememberPermissionAsker
import ir.minerfinder.ui.theme.Palette

/** Permissions a scan asks for; missing ones only switch off what needs them. */
fun scanPermissions(): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}

@Composable
fun SearchScreen() {
    val graph = LocalContext.current.graph
    val running by graph.scan.running.collectAsState()
    val detection by graph.scan.hub.detection.collectAsState()
    val asker = rememberPermissionAsker()

    Column(Modifier.fillMaxSize()) {
        Header(stringResource(R.string.app_name), stringResource(R.string.search_subtitle))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProbabilityCard(detection, running)
            if (running) {
                BigButton(stringResource(R.string.scan_stop), { graph.scan.stop() }, color = Palette.Hot)
            } else {
                BigButton(stringResource(R.string.scan_start), {
                    asker.ask(scanPermissions()) { graph.scan.start() }
                })
            }
            graph.catalog.all.filter { graph.settings.current.isEnabled(it.id) }.forEach { source ->
                SourceCard(source, detection)
            }
        }
    }
}

@Composable
private fun ProbabilityCard(detection: Detection, running: Boolean) {
    Section(stringResource(R.string.probability_title)) {
        val p = if (running) detection.probability else 0f
        Text(
            Fa.num(p) + stringResource(R.string.unit_percent),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 64.sp,
            color = heatColor(p),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            stringResource(heatWord(p, running)),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
        HotColdBar(p)
        Field(stringResource(R.string.confidence), Fa.num(detection.confidence * 100) + stringResource(R.string.unit_percent))
    }
}

private fun heatWord(p: Float, running: Boolean): Int = when {
    !running -> R.string.heat_idle
    p >= 75 -> R.string.heat_hot
    p >= 50 -> R.string.heat_warm
    p >= 25 -> R.string.heat_lukewarm
    else -> R.string.heat_cold
}

@Composable
private fun SourceCard(source: SensorSource, detection: Detection) {
    val status by source.status.collectAsState()
    val reading by source.readings.collectAsState()
    val error = (source as? BaseSource)?.error?.collectAsState()?.value
    Section(source.label) {
        val share = detection.contributions.firstOrNull { it.sourceId == source.id }?.share ?: 0f
        when (status) {
            SourceStatus.IDLE -> Text(stringResource(R.string.source_idle), color = Palette.Muted)
            SourceStatus.UNAVAILABLE -> Text(stringResource(R.string.source_unavailable), color = Palette.Warm)
            SourceStatus.ERROR -> Text(stringResource(R.string.source_error, error.orEmpty()), color = Palette.Hot)
            SourceStatus.RUNNING -> Unit
        }
        reading?.let { r ->
            ScoreBar(stringResource(R.string.source_score), r.score, heatColor(r.score))
            Field(stringResource(R.string.confidence), Fa.num(r.confidence * 100) + stringResource(R.string.unit_percent))
            Field(stringResource(R.string.source_share), Fa.num(share * 100) + stringResource(R.string.unit_percent))
            r.raw.forEach { (key, value) -> RawRow(key, value) }
        }
    }
}

@Composable
private fun RawRow(key: String, value: Double) {
    val label = RawLabels.of(key)
    if (label == null) {
        Field(key, Fa.num(value, 2))
    } else {
        val unit = label.unit?.let { " " + stringResource(it) }.orEmpty()
        Field(stringResource(label.text), Fa.num(value, label.decimals) + unit)
    }
}

@Composable
fun CenteredNote(text: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = Palette.Muted, textAlign = TextAlign.Center)
    }
}
