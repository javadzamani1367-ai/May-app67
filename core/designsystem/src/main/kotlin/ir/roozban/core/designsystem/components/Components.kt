package ir.roozban.core.designsystem.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.roozban.core.designsystem.theme.Role

/**
 * The app's top bar: floats over the backdrop, title in strong ink, navigation and actions in
 * the accent color so they read as controls rather than decoration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoozbanTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)) { title() } },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
            actionIconContentColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** Primary action: filled accent so it is the one obvious thing to press on a screen. */
@Composable
fun RoozbanFab(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = icon,
        text = text,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
    )
}

/**
 * The standard surface for grouped content: white (or raised dark) with a hairline border.
 * [accent] draws a colored bar on the start edge to carry state (priority, habit color, ...).
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val colors = CardDefaults.cardColors(containerColor = container)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    val body: @Composable ColumnScope.() -> Unit = {
        if (accent == null) {
            content()
        } else {
            Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(accent))
                Column(Modifier.weight(1f)) { content() }
            }
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, elevation = elevation, content = body)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, elevation = elevation, content = body)
    }
}

/** A soft rounded square with a tinted icon: gives lists and sections a recognizable color each. */
@Composable
fun IconBadge(@DrawableRes icon: Int, role: Role, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(role.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = role.color, modifier = Modifier.size(size * 0.55f))
    }
}

/** A role built from any color (e.g. a habit's or project's own color). */
@Composable
fun roleOf(color: Color): Role = Role(color, Color.White, color.copy(alpha = 0.16f), color)

/** Section heading with an optional colored icon. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null, color: Color = MaterialTheme.colorScheme.primary) {
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = color)
    }
}

/** A small rounded label in a semantic role (e.g. «عقب‌افتاده» in red, «انجام شد» in teal). */
@Composable
fun StatusPill(text: String, role: Role, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = role.onContainer,
        modifier = modifier.clip(CircleShape).background(role.container).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Big empty-state block with a colored badge. */
@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, body: String, role: Role, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, role, size = 72.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
