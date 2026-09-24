package ir.roozban.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.alarm.ReminderPermissions
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.model.ReminderKind
import ir.roozban.core.model.UserSettings
import ir.roozban.core.ui.TimePickerDialog
import java.time.LocalTime

internal fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        startActivity(ReminderPermissions.appDetails(this))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    onBack: () -> Unit,
    onOpenBatteryGuide: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DsR.drawable.ic_arrow_back), stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        val s = settings
        if (s != null) Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HealthCard(onOpenBatteryGuide)
            DefaultReminderCard(s, viewModel)
            AllDayCard(s, viewModel)
            WordsCard(s, viewModel)
            CalendarCard(s, viewModel)
            BackupCard(viewModel, snackbar)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsCard(stringResource(R.string.settings_appearance)) {
                    SwitchRow(stringResource(R.string.settings_dynamic_color), s.dynamicColor, viewModel::setDynamicColor)
                }
            }
            AboutCard()
        }
    }
}

@Composable
private fun SettingsCard(title: String, description: String? = null, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun HealthCard(onOpenBatteryGuide: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf(ReminderPermissions.status(context)) }
    LifecycleResumeEffect(Unit) {
        status = ReminderPermissions.status(context)
        onPauseOrDispose { }
    }
    SettingsCard(stringResource(R.string.settings_health)) {
        HealthRow(stringResource(R.string.health_notifications), status.notifications) {
            context.startSafely(ReminderPermissions.notificationSettings(context))
        }
        HealthRow(stringResource(R.string.health_exact), status.exactAlarms) {
            context.startSafely(ReminderPermissions.exactAlarmSettings(context))
        }
        HealthRow(stringResource(R.string.health_full_screen), status.fullScreen) {
            context.startSafely(ReminderPermissions.fullScreenSettings(context))
        }
        HealthRow(stringResource(R.string.health_battery), status.batteryUnrestricted, onFix = onOpenBatteryGuide)
        TextButton(onClick = onOpenBatteryGuide) { Text(stringResource(R.string.health_battery_guide)) }
    }
}

@Composable
private fun HealthRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            painterResource(if (ok) DsR.drawable.ic_check else DsR.drawable.ic_warning),
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (ok) {
            Text(stringResource(R.string.health_ok), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TextButton(onClick = onFix) { Text(stringResource(R.string.health_fix)) }
        }
    }
}

@Composable
private fun DefaultReminderCard(s: UserSettings, vm: SettingsViewModel) {
    SettingsCard(stringResource(R.string.settings_default_reminder)) {
        val kind = s.defaultReminder?.kind
        ChipRow {
            FilterChip(kind == null, { vm.setDefaultReminderKind(null) }, { Text(stringResource(R.string.settings_reminder_none)) })
            FilterChip(kind == ReminderKind.NOTIFICATION, { vm.setDefaultReminderKind(ReminderKind.NOTIFICATION) }, {
                Text(stringResource(R.string.settings_reminder_notification))
            })
            FilterChip(kind == ReminderKind.ALARM, { vm.setDefaultReminderKind(ReminderKind.ALARM) }, {
                Text(stringResource(R.string.settings_reminder_alarm))
            })
        }
        s.defaultReminder?.let { reminder ->
            ChipRow {
                listOf(0, 5, 15, 30).forEach { minutes ->
                    val label = if (minutes == 0) {
                        stringResource(R.string.settings_on_time)
                    } else {
                        stringResource(R.string.settings_minutes_before, PersianDigits.format(minutes))
                    }
                    FilterChip(reminder.offsetMinutes == minutes, { vm.setDefaultReminderOffset(minutes) }, { Text(label) })
                }
            }
        }
    }
}

@Composable
private fun AllDayCard(s: UserSettings, vm: SettingsViewModel) {
    var picking by remember { mutableStateOf(false) }
    SettingsCard(stringResource(R.string.settings_all_day), stringResource(R.string.settings_all_day_desc)) {
        val time = s.allDayReminderTime
        SwitchRow(
            label = time?.let { stringResource(R.string.settings_at_time, PersianDateFormatter.time(it)) }
                ?: stringResource(R.string.settings_reminder_none),
            checked = time != null,
            onChange = { on -> vm.setAllDayReminder(if (on) LocalTime.of(9, 0) else null) },
            onLabelClick = { if (time != null) picking = true },
        )
    }
    if (picking) {
        TimePickerDialog(
            initial = s.allDayReminderTime ?: LocalTime.of(9, 0),
            onConfirm = {
                vm.setAllDayReminder(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun CalendarCard(s: UserSettings, vm: SettingsViewModel) {
    SettingsCard(stringResource(R.string.settings_calendar)) {
        SwitchRow(stringResource(R.string.settings_show_gregorian), s.showGregorian, vm::setShowGregorian)
        SwitchRow(stringResource(R.string.settings_show_hijri), s.showHijri, vm::setShowHijri)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_hijri_offset), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_hijri_offset_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = { vm.adjustHijriOffset(-1) }) { Text("−", style = MaterialTheme.typography.titleMedium) }
            Text(
                PersianDigits.toPersian(if (s.hijriOffset > 0) "+${s.hijriOffset}" else s.hijriOffset.toString()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            FilledTonalIconButton(onClick = { vm.adjustHijriOffset(+1) }) { Text("+", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun WordsCard(s: UserSettings, vm: SettingsViewModel) {
    SettingsCard(stringResource(R.string.settings_words), stringResource(R.string.settings_words_desc)) {
        HourStepper(stringResource(R.string.word_morning), s.morningHour) { vm.adjustHour(DayPart.MORNING, it) }
        HourStepper(stringResource(R.string.word_noon), s.noonHour) { vm.adjustHour(DayPart.NOON, it) }
        HourStepper(stringResource(R.string.word_afternoon), s.afternoonHour) { vm.adjustHour(DayPart.AFTERNOON, it) }
        HourStepper(stringResource(R.string.word_evening), s.eveningHour) { vm.adjustHour(DayPart.EVENING, it) }
        HourStepper(stringResource(R.string.word_night), s.nightHour) { vm.adjustHour(DayPart.NIGHT, it) }
    }
}

@Composable
private fun HourStepper(label: String, hour: Int, onDelta: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("«$label»", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onDelta(-1) }) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            PersianDateFormatter.time(LocalTime.of(hour, 0)),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        FilledTonalIconButton(onClick = { onDelta(+1) }) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, onLabelClick: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (onLabelClick != null && checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .then(if (onLabelClick != null) Modifier.clickable(onClick = onLabelClick) else Modifier),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    SettingsCard(stringResource(R.string.settings_about)) {
        Text(stringResource(R.string.settings_version, PersianDigits.toPersian(version)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
