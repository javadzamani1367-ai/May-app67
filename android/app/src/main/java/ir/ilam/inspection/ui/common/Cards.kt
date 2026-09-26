package ir.ilam.inspection.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone

/**
 * The surface every card in the app is drawn on: a hairline border instead of
 * a shadow, so cards stay crisp in sunlight and do not turn to grey smudges in
 * the dark theme.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    val color = if (Tavan.colors.dark) MaterialTheme.colorScheme.surfaceContainer
    else MaterialTheme.colorScheme.surfaceContainerLowest
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) { content() }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border) { content() }
    }
}

/**
 * A titled block of fields — the visual unit every form step is built from.
 * The icon says what the block is about before the title is read.
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    tone: Tone = Tone.BRAND,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    AppCard(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    ToneIcon(icon = icon, tone = tone, size = 34.dp)
                    Box(modifier = Modifier.size(Spacing.md))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                trailing?.invoke(this)
            }
            Box(modifier = Modifier.size(Spacing.sm))
            content()
        }
    }
}

/** A heading between groups of cards on a screen. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke(this)
    }
}

/**
 * Label at the start, value at the end — a read-only row of the case summary.
 * A long value wraps under itself instead of pushing the label off screen.
 */
@Composable
fun ValueRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    emphasize: Boolean = false,
    /** Codes, coordinates and URLs in Latin script read left to right. */
    ltr: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        val shown = value?.takeIf { it.isNotBlank() }
        val base = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium
        Text(
            text = shown ?: "—",
            // Explicit direction, not the text's own guess: a URL or a code
            // that starts with a symbol otherwise renders back to front.
            style = if (ltr) base.copy(textDirection = TextDirection.Ltr) else base,
            color = if (shown == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.55f)
        )
    }
}

/** A labelled figure in a grid: the case summary's "at a glance" block. */
@Composable
fun InfoTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: Tone = Tone.NEUTRAL
) {
    val colors = Tavan.colors.of(tone)
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (tone == Tone.NEUTRAL) MaterialTheme.colorScheme.surfaceContainerLow else colors.container
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = colors.strong, modifier = Modifier.size(16.dp))
                    Box(modifier = Modifier.size(6.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = if (tone == Tone.NEUTRAL) MaterialTheme.colorScheme.onSurface else colors.onContainer
            )
        }
    }
}

/** Nothing to show — said plainly, with what to do about it when there is something. */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Inbox,
    title: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Box(modifier = modifier.fillMaxSize().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ToneIcon(icon = icon, tone = Tone.NEUTRAL, size = 64.dp)
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.lg)
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm)
            )
            if (action != null) {
                Box(modifier = Modifier.padding(top = Spacing.lg)) { action() }
            }
        }
    }
}

/** The form for adding one more, set apart from the list on a tinted panel. */
@Composable
fun AddPanel(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
