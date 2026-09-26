package ir.ilam.inspection.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.ilam.inspection.R
import ir.ilam.inspection.ui.theme.Navy700
import ir.ilam.inspection.ui.theme.Navy950
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan

/**
 * The header of every screen: deep navy in both themes, so the app is
 * recognisably itself from across a room, with the screen's title, a line of
 * context under it, and its actions.
 */
@Composable
fun TavanTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    /** The home screen shows the brand mark where other screens have "back". */
    showBrand: Boolean = false,
    /** Anything that belongs to the header under the title row: tabs, a search box, KPIs. */
    below: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = Tavan.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(colors.header, colors.headerDeep)))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = colors.onHeader
                    )
                }
                showBrand -> BrandMark(size = 40.dp, modifier = Modifier.padding(horizontal = 8.dp))
                else -> Box(modifier = Modifier.size(Spacing.sm))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.onHeader,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onHeaderMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
        below?.invoke()
    }
}

/** An action in the header, with a count when something is waiting behind it. */
@Composable
fun HeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    badge: Int = 0
) {
    Box {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription, tint = Tavan.colors.onHeader)
        }
        if (badge > 0) {
            CountBadge(count = badge, modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 2.dp))
        }
    }
}

/**
 * The TavanKav mark on its navy tile. Drawn from the launcher icon's own
 * foreground, so the icon, the splash and the header can never drift apart.
 */
@Composable
fun BrandMark(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.linearGradient(listOf(Navy700, Navy950))),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}
