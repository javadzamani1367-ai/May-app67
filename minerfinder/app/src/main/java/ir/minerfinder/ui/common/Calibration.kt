package ir.minerfinder.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.minerfinder.R
import ir.minerfinder.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What a calibratable source offers the calibration card. */
class CalibrationTarget(
    val hasCalibration: () -> Boolean,
    val start: () -> Unit,
    val finish: () -> Boolean,
    val clear: () -> Unit,
)

/** Start → countdown → stored baseline; or clear it. Needs a running scan to hear anything. */
@Composable
fun CalibrationControls(title: String, help: String, seconds: Int, running: Boolean, target: CalibrationTarget) {
    val scope = rememberCoroutineScope()
    var remaining by remember { mutableIntStateOf(0) }
    var calibrated by remember { mutableStateOf(target.hasCalibration()) }
    var failed by remember { mutableStateOf(false) }

    Section(title) {
        Text(help, color = Palette.Muted)
        Field(
            stringResource(R.string.calibration_state),
            stringResource(if (calibrated) R.string.calibration_done else R.string.calibration_none),
        )
        if (failed) Text(stringResource(R.string.calibration_failed), color = Palette.Hot)
        if (remaining > 0) {
            Text(stringResource(R.string.calibration_running, Fa.int(remaining)), color = Palette.Warm)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.calibration_start), enabled = running, onClick = {
                    failed = false
                    target.start()
                    scope.launch {
                        for (s in seconds downTo 1) {
                            remaining = s
                            delay(1_000)
                        }
                        remaining = 0
                        failed = !target.finish()
                        calibrated = target.hasCalibration()
                    }
                })
                SecondaryButton(stringResource(R.string.calibration_clear), enabled = calibrated, onClick = {
                    target.clear()
                    calibrated = false
                })
            }
            if (!running) Text(stringResource(R.string.calibration_needs_scan), color = Palette.Muted)
        }
    }
}
