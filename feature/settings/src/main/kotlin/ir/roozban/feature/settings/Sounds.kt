package ir.roozban.feature.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.model.UserSettings

/** Which notification sound is being picked. */
private enum class SoundTarget { HABITS, EVENTS }

/** «صدای اعلان‌ها»: the user picks the sound for habit reminders and for personal occasions. */
@Composable
internal fun SoundsCard(s: UserSettings, vm: SettingsViewModel) {
    val context = LocalContext.current
    var target by remember { mutableStateOf<SoundTarget?>(null) }
    var playing by remember { mutableStateOf<Ringtone?>(null) }
    DisposableEffect(Unit) { onDispose { playing?.stop() } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val which = target ?: return@rememberLauncherForActivityResult
        target = null
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        val value = when {
            uri == null -> "" // «بی‌صدا»
            RingtoneManager.isDefault(uri) -> null
            else -> uri.toString()
        }
        if (which == SoundTarget.HABITS) vm.setHabitSound(value) else vm.setEventSound(value)
    }
    fun pick(which: SoundTarget, current: String?) {
        target = which
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_RINGTONE or RingtoneManager.TYPE_ALARM)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(if (which == SoundTarget.HABITS) R.string.sounds_habits else R.string.sounds_events))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri(current))
        runCatching { picker.launch(intent) }
    }
    fun preview(sound: String?) {
        playing?.stop()
        val uri = existingUri(sound) ?: return
        playing = runCatching { RingtoneManager.getRingtone(context, uri) }.getOrNull()?.also { it.play() }
    }
    SettingsCard(
        stringResource(R.string.sounds_title),
        stringResource(R.string.sounds_desc),
        icon = DsR.drawable.ic_notifications,
        role = Roozban.colors.streak,
    ) {
        SoundRow(stringResource(R.string.sounds_habits), soundName(context, s.habitSound), { pick(SoundTarget.HABITS, s.habitSound) }) { preview(s.habitSound) }
        SoundRow(stringResource(R.string.sounds_events), soundName(context, s.eventSound), { pick(SoundTarget.EVENTS, s.eventSound) }) { preview(s.eventSound) }
    }
}

@Composable
private fun SoundRow(label: String, current: String, onPick: () -> Unit, onPreview: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(current, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onPreview) { Text(stringResource(R.string.sounds_preview)) }
        FilledTonalButton(onClick = onPick) { Text(stringResource(R.string.sounds_choose)) }
    }
}

private fun existingUri(sound: String?): Uri? = when (sound) {
    null -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    "" -> null
    else -> Uri.parse(sound)
}

private fun soundName(context: Context, sound: String?): String = when (sound) {
    null -> context.getString(R.string.sounds_default)
    "" -> context.getString(R.string.sounds_silent)
    else -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(sound))?.getTitle(context) }.getOrNull()
        ?: context.getString(R.string.sounds_custom)
}
