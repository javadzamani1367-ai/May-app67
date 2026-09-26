package ir.ilam.inspection.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.ToneChip
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.ThemeMode
import ir.ilam.inspection.ui.theme.ThemePreference
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/** Who is signed in on this phone, and the code that identifies the installation. */
@Composable
fun AccountCard(expertCode: String, deviceCode: String) {
    SectionCard(
        title = stringResource(R.string.settings_account),
        icon = Icons.Filled.Badge,
        trailing = {
            StatusBadge(
                text = stringResource(if (UserRole.isManager) R.string.role_manager else R.string.role_expert),
                tone = if (UserRole.isManager) Tone.BRAND else Tone.ACCENT
            )
        }
    ) {
        ValueRow(stringResource(R.string.settings_expert_code), PersianNumbers.toPersian(expertCode), emphasize = true)
        ValueRow(stringResource(R.string.settings_device_code), deviceCode, ltr = true)
    }
}

/**
 * Light, dark, or the phone's own setting. Takes effect at once and is kept
 * outside the encrypted database, so the very first frame is already right.
 */
@Composable
fun AppearanceCard() {
    val context = LocalContext.current
    SectionCard(
        title = stringResource(R.string.settings_appearance),
        subtitle = stringResource(R.string.settings_appearance_hint),
        icon = Icons.Filled.Palette,
        tone = Tone.ACCENT
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)
        ) {
            listOf(
                Triple(ThemeMode.LIGHT, R.string.theme_light, Icons.Filled.LightMode),
                Triple(ThemeMode.DARK, R.string.theme_dark, Icons.Filled.DarkMode),
                Triple(ThemeMode.SYSTEM, R.string.theme_system, Icons.Filled.SettingsBrightness)
            ).forEach { (mode, label, icon) ->
                ToneChip(
                    text = stringResource(label),
                    selected = ThemePreference.mode == mode,
                    tone = Tone.ACCENT,
                    onClick = { ThemePreference.set(context, mode) },
                    selectedIcon = icon
                )
            }
        }
    }
}

/** A line of explanation under a card's content, in the quiet colour. */
@Composable
fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.sm)
    )
}
