package ir.roozban.feature.settings

import ir.roozban.core.designsystem.components.RoozbanTopBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import ir.roozban.core.alarm.OemBatteryGuide
import ir.roozban.core.alarm.ReminderPermissions
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.designsystem.R as DsR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatteryGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val guide = remember { OemBatteryGuide.guide(context) }
    var unrestricted by remember { mutableStateOf(ReminderPermissions.status(context).batteryUnrestricted) }
    LifecycleResumeEffect(Unit) {
        unrestricted = ReminderPermissions.status(context).batteryUnrestricted
        onPauseOrDispose { }
    }
    Scaffold(
        topBar = {
            RoozbanTopBar(
                title = { Text(stringResource(R.string.battery_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DsR.drawable.ic_arrow_back), stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.battery_intro), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.battery_device, guide.brand.displayName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            guide.steps.forEachIndexed { i, step ->
                Row {
                    Text("${PersianDigits.format(i + 1)}.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    Text(step, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (unrestricted) {
                Text(stringResource(R.string.battery_already), color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = { context.startSafely(ReminderPermissions.batteryOptimizationRequest(context)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.battery_request)) }
            }
            guide.autostart?.let { intent ->
                FilledTonalButton(onClick = { context.startSafely(intent) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.battery_autostart))
                }
            }
            OutlinedButton(
                onClick = { context.startSafely(ReminderPermissions.appDetails(context)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.battery_app_settings)) }
        }
    }
}
