package ir.ilam.inspection.field.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.field.R
import ir.ilam.inspection.ui.common.AppCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.ToneIcon
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone

/**
 * One large choice: big enough to hit with a gloved thumb in the sun. When the
 * account lacks the permission it stays on screen, dimmed, and says why —
 * a missing button would leave the user wondering where it went.
 */
@Composable
fun OptionCard(
    title: String,
    hint: String,
    icon: ImageVector,
    tone: Tone,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp).alpha(if (enabled) 1f else 0.6f),
        onClick = if (enabled) onClick else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 128.dp).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ToneIcon(icon = if (enabled) icon else Icons.Filled.Lock, tone = if (enabled) tone else Tone.NEUTRAL, size = 64.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (!enabled) {
                    StatusBadge(
                        text = stringResource(R.string.field_no_permission),
                        tone = Tone.WARNING,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/** Shown while the server has said this phone must sign in again. */
@Composable
fun SessionBanner(onSignInAgain: () -> Unit) {
    val warning = Tavan.colors.warning
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), onClick = onSignInAgain) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.field_session_expired), color = warning.strong, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.field_sign_in_again),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** A thermal route is still recording: the way back to it, from the first screen. */
@Composable
fun RecordingBanner(onOpen: () -> Unit) {
    val success = Tavan.colors.success
    AppCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), onClick = onOpen) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.thermal_banner), color = success.strong, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.thermal_banner_open),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
