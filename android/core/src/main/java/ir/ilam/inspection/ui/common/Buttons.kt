package ir.ilam.inspection.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone

/**
 * The main action of a screen. Tall enough for a gloved thumb, square enough
 * to look like an instrument's control rather than a toy.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
    /** SUCCESS for the action that completes something, DANGER for one that destroys. */
    tone: Tone = Tone.BRAND
) {
    val colors = when (tone) {
        Tone.BRAND, Tone.NEUTRAL -> ButtonDefaults.buttonColors()
        else -> {
            val t = Tavan.colors.of(tone)
            ButtonDefaults.buttonColors(containerColor = t.strong, contentColor = MaterialTheme.colorScheme.surface)
        }
    }
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.heightIn(min = Spacing.touch),
        shape = MaterialTheme.shapes.medium,
        colors = colors
    ) {
        ButtonContent(text, icon, busy)
    }
}

/** The second choice beside a primary one: back, cancel, the alternative route. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tone: Tone = Tone.BRAND
) {
    val strong = if (tone == Tone.BRAND) MaterialTheme.colorScheme.primary else Tavan.colors.of(tone).strong
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Spacing.touch),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.5.dp, if (enabled) strong.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = strong)
    ) {
        ButtonContent(text, icon, busy = false)
    }
}

@Composable
private fun RowScope.ButtonContent(text: String, icon: ImageVector?, busy: Boolean) {
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Box(modifier = Modifier.size(Spacing.sm))
    } else if (icon != null) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Box(modifier = Modifier.size(Spacing.sm))
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
}

/**
 * The bar of actions pinned to the bottom of a form: always in reach, never
 * scrolled away, with a hairline above it so it reads as part of the frame.
 */
@Composable
fun BottomActionBar(
    modifier: Modifier = Modifier,
    note: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            note?.let { Box(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)) { it() } }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}
