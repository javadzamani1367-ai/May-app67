package ir.ilam.inspection.field.ui.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan

/**
 * The rest of a page while what must come first — the position — is missing:
 * dimmed and untouchable, with the reason above it. A tap on it does what the
 * reason asks ([onTap]: the map), so a locked field is never a dead end.
 */
@Composable
fun LockGate(
    locked: Boolean,
    reason: String,
    onTap: () -> Unit,
    /** False for a second locked block on the same page, whose reason is already shown. */
    showNote: Boolean = true,
    content: @Composable () -> Unit
) {
    val focus = LocalFocusManager.current
    Column(modifier = Modifier.fillMaxWidth()) {
        if (locked && showNote) LockNote(reason)
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().alpha(if (locked) LOCKED_ALPHA else 1f)) { content() }
            if (locked) {
                Box(
                    modifier = Modifier.matchParentSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        // Leaving the plate field is what brings the map up; a tap here leaves it too.
                        focus.clearFocus()
                        onTap()
                    }
                )
            }
        }
    }
}

@Composable
fun LockNote(reason: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = Tavan.colors.warning.strong, modifier = Modifier.size(20.dp))
        Text(
            reason,
            style = MaterialTheme.typography.titleSmall,
            color = Tavan.colors.warning.strong,
            modifier = Modifier.padding(start = Spacing.sm)
        )
    }
}

/**
 * The form's tiles with those that must wait locked: while [reason] stands,
 * every tile but the ones in [open] says why and does not open.
 */
fun List<HubSection>.gated(reason: String?, open: Set<String>): List<HubSection> =
    if (reason == null) this else map { if (it.key in open) it else it.copy(lockReason = reason) }

private const val LOCKED_ALPHA = 0.35f
