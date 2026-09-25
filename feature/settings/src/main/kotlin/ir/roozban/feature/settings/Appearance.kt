package ir.roozban.feature.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.theme.BackgroundPreview
import ir.roozban.core.designsystem.theme.Backgrounds
import ir.roozban.core.designsystem.theme.paletteSwatch
import ir.roozban.core.model.StartScreen
import ir.roozban.core.model.ThemeMode
import ir.roozban.core.model.ThemePalette
import ir.roozban.core.model.UserSettings

private fun paletteName(p: ThemePalette): Int = when (p) {
    ThemePalette.INDIGO -> R.string.palette_indigo
    ThemePalette.OCEAN -> R.string.palette_ocean
    ThemePalette.VIOLET -> R.string.palette_violet
    ThemePalette.ROSE -> R.string.palette_rose
    ThemePalette.CORAL -> R.string.palette_coral
    ThemePalette.AMBER -> R.string.palette_amber
    ThemePalette.TEAL -> R.string.palette_teal
    ThemePalette.SLATE -> R.string.palette_slate
    ThemePalette.GREEN -> R.string.palette_green
}

@Composable
internal fun AppearanceCard(s: UserSettings, vm: SettingsViewModel, section: @Composable (String, @Composable () -> Unit) -> Unit, switchRow: @Composable (String, Boolean, (Boolean) -> Unit) -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            vm.setBackgroundImage(uri) { ok ->
                if (!ok) Toast.makeText(context, R.string.appearance_image_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    section(stringResource(R.string.settings_appearance)) {
        Label(stringResource(R.string.appearance_mode))
        val modes = listOf(ThemeMode.LIGHT to R.string.appearance_light, ThemeMode.DARK to R.string.appearance_dark, ThemeMode.SYSTEM to R.string.appearance_system)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = s.themeMode == mode,
                    onClick = { vm.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                ) { Text(stringResource(label)) }
            }
        }

        Label(stringResource(R.string.appearance_palette))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemePalette.entries.forEach { p ->
                val selected = s.palette == p && !s.dynamicColor
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { vm.setPalette(p) }) {
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(paletteSwatch(p))
                            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(painterResource(DsR.drawable.ic_check), null, tint = Color.White)
                    }
                    Text(stringResource(paletteName(p)), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            switchRow(stringResource(R.string.settings_dynamic_color), s.dynamicColor, vm::setDynamicColor)
        }

        Label(stringResource(R.string.appearance_background))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val tile = Modifier.size(width = 64.dp, height = 96.dp)
            val shape = RoundedCornerShape(14.dp)
            val strong = MaterialTheme.colorScheme.onSurface
            val faint = MaterialTheme.colorScheme.outlineVariant
            fun Modifier.selectedBorder(on: Boolean) = if (on) border(3.dp, strong, shape) else border(1.dp, faint, shape)
            BackgroundTile(stringResource(R.string.appearance_background_none), tile.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLowest).selectedBorder(s.background == null)) {
                vm.setBackgroundPreset(null)
            }
            Backgrounds.presets.forEach { preset ->
                val on = s.background == Backgrounds.PRESET_PREFIX + preset.id
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(tile.clip(shape).selectedBorder(on).clickable { vm.setBackgroundPreset(preset.id) }) {
                        BackgroundPreview(preset, Modifier.fillMaxSize())
                    }
                    Text(preset.name, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
            val imageOn = s.background?.startsWith(UserSettings.BACKGROUND_IMAGE) == true
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    tile.clip(shape).background(MaterialTheme.colorScheme.secondaryContainer).selectedBorder(imageOn)
                        .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(DsR.drawable.ic_image), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Text(stringResource(R.string.appearance_background_gallery), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (s.background != null) {
            Label(stringResource(R.string.appearance_veil))
            Slider(value = s.backgroundVeil, onValueChange = vm::setBackgroundVeil, valueRange = 0.3f..0.95f)
        }

        Label(stringResource(R.string.appearance_start))
        val starts = listOf(StartScreen.TODAY to R.string.appearance_start_today, StartScreen.CALENDAR to R.string.appearance_start_calendar)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            starts.forEachIndexed { i, (screen, label) ->
                SegmentedButton(
                    selected = s.startScreen == screen,
                    onClick = { vm.setStartScreen(screen) },
                    shape = SegmentedButtonDefaults.itemShape(i, starts.size),
                ) { Text(stringResource(label)) }
            }
        }
    }
}

@Composable
private fun BackgroundTile(label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier.clickable(onClick = onClick))
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}
