package ir.ilam.inspection.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

/**
 * A status in a word or two: "بازدید شده", "در انتظار تأیید", "۱۲ روز".
 * Tinted, never solid: a page of solid badges shouts, and the one that
 * matters stops standing out.
 */
@Composable
fun StatusBadge(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** Solid fill, for the one badge on a screen that must be seen first. */
    solid: Boolean = false
) {
    val colors = Tavan.colors.of(tone)
    val background = if (solid) colors.strong else colors.container
    val foreground = if (solid) MaterialTheme.colorScheme.surface else colors.onContainer
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (solid) foreground else colors.strong,
                modifier = Modifier.size(15.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * A tag in a fixed colour of its own — the report type. Tinted from that
 * colour, so six types stay six distinct colours without six loud blocks.
 */
@Composable
fun ColorTag(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (Tavan.colors.dark) 0.22f else 0.12f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1
        )
    }
}

/** A small count on a tab or an icon: how many are waiting. */
@Composable
fun CountBadge(count: Int, tone: Tone = Tone.DANGER, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val colors = Tavan.colors.of(tone)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(CircleShape)
            .background(colors.strong)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = PersianNumbers.toPersian(if (count > 99) "99+" else count.toString()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.surface
        )
    }
}

/** An icon on a tinted square: the visual anchor of a card, a KPI or a list row. */
@Composable
fun ToneIcon(
    icon: ImageVector,
    tone: Tone,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val colors = Tavan.colors.of(tone)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(colors.container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = colors.strong, modifier = Modifier.size(size * 0.55f))
    }
}
