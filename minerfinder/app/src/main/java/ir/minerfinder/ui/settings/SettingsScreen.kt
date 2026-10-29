package ir.minerfinder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.minerfinder.R
import ir.minerfinder.graph
import ir.minerfinder.sources.SourceIds
import ir.minerfinder.ui.common.Fa
import ir.minerfinder.ui.common.Field
import ir.minerfinder.ui.common.Header
import ir.minerfinder.ui.common.SecondaryButton
import ir.minerfinder.ui.common.Section
import ir.minerfinder.ui.map.MapSetup
import ir.minerfinder.ui.theme.Palette

@Composable
fun SettingsScreen() {
    val graph = LocalContext.current.graph
    val s by graph.settings.state.collectAsState()
    // Changes that the running analysis reads at start take effect on restart.
    fun change(restart: Boolean = false, block: (ir.minerfinder.settings.Settings) -> ir.minerfinder.settings.Settings) {
        graph.settings.update(block)
        if (restart) graph.scan.restart()
    }

    Column(Modifier.fillMaxSize()) {
        Header(stringResource(R.string.tab_settings))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(stringResource(R.string.settings_alerts)) {
                LabeledSlider(
                    stringResource(R.string.settings_alert_threshold), s.alertThreshold, 20f..95f, 14,
                    Fa.num(s.alertThreshold) + stringResource(R.string.unit_percent),
                ) { v -> change { it.copy(alertThreshold = v) } }
                SwitchRow(stringResource(R.string.settings_beep), s.beep) { on -> change { it.copy(beep = on) } }
                SwitchRow(stringResource(R.string.settings_vibrate), s.vibrate) { on -> change { it.copy(vibrate = on) } }
            }

            Section(stringResource(R.string.settings_sources)) {
                Text(stringResource(R.string.settings_weights_help), color = Palette.Muted)
                graph.catalog.all.forEach { source ->
                    val enabled = s.isEnabled(source.id)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(source.label)
                        Switch(checked = enabled, onCheckedChange = { on ->
                            change(restart = true) { it.copy(disabled = if (on) it.disabled - source.id else it.disabled + source.id) }
                        })
                    }
                    if (enabled) {
                        val w = s.weight(source.id, SourceIds.defaultWeight(source.id))
                        LabeledSlider(stringResource(R.string.settings_weight), w, 0f..3f, 11, Fa.num(w, 1)) { v ->
                            change { it.copy(weights = it.weights + (source.id to v)) }
                        }
                    }
                }
            }

            Section(stringResource(R.string.settings_audio)) {
                LabeledSlider(
                    stringResource(R.string.settings_stability_window), s.stabilityWindowS.toFloat(), 10f..30f, 3,
                    Fa.int(s.stabilityWindowS) + " " + stringResource(R.string.unit_seconds),
                ) { v -> change(restart = true) { it.copy(stabilityWindowS = v.toInt()) } }
                LabeledSlider(
                    stringResource(R.string.settings_peak_threshold), s.peakThresholdDb, 6f..24f, 17,
                    Fa.num(s.peakThresholdDb) + " " + stringResource(R.string.unit_db),
                ) { v -> change(restart = true) { it.copy(peakThresholdDb = v) } }
                Text(stringResource(R.string.audio_input), color = Palette.Navy)
                InputPicker(s.audioInputId) { id -> change(restart = true) { it.copy(audioInputId = id) } }
            }

            Section(stringResource(R.string.settings_map)) {
                LabeledSlider(
                    stringResource(R.string.settings_log_interval), s.logIntervalS.toFloat(), 1f..15f, 13,
                    Fa.int(s.logIntervalS) + " " + stringResource(R.string.unit_seconds),
                ) { v -> change { it.copy(logIntervalS = v.toInt()) } }
                val context = LocalContext.current
                var cache by remember { mutableLongStateOf(MapSetup.cacheBytes(context)) }
                Field(stringResource(R.string.settings_map_cache), Fa.num(cache / 1_048_576.0, 1) + " " + stringResource(R.string.unit_mb))
                Text(stringResource(R.string.settings_map_cache_help), color = Palette.Muted)
                SecondaryButton(stringResource(R.string.settings_map_cache_clear), onClick = {
                    MapSetup.clearCache(context)
                    cache = MapSetup.cacheBytes(context)
                })
            }

            Section(stringResource(R.string.about)) {
                Field(stringResource(R.string.version), Fa.digits(ir.minerfinder.BuildConfig.VERSION_NAME))
                Text(stringResource(R.string.about_text), color = Palette.Muted)
            }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    shown: String,
    onChange: (Float) -> Unit,
) {
    Column {
        Field(label, shown)
        Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
    }
}
