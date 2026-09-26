package ir.ilam.inspection.ui.pending

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ir.ilam.inspection.R
import ir.ilam.inspection.data.model.ReportStatus
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.util.PersianNumbers

/** The four places of the home screen. The last three are the case queues. */
enum class HomeTab(val status: ReportStatus?, val label: Int) {
    DASHBOARD(null, R.string.tab_dashboard),
    PENDING(ReportStatus.PENDING, R.string.tab_pending),
    VISITED(ReportStatus.VISITED, R.string.tab_visited),
    ARCHIVE(ReportStatus.ARCHIVED, R.string.tab_archive);

    fun icon(selected: Boolean): ImageVector = when (this) {
        DASHBOARD -> if (selected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
        PENDING -> if (selected) Icons.Filled.PendingActions else Icons.Outlined.PendingActions
        VISITED -> if (selected) Icons.Filled.TaskAlt else Icons.Outlined.TaskAlt
        ARCHIVE -> if (selected) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2
    }

    companion object {
        fun of(status: ReportStatus): HomeTab = entries.first { it.status == status }
    }
}

/**
 * The bottom bar. The pending tab carries its count, amber when some of those
 * cases have waited more than a week — the one number worth seeing from any
 * screen of the app.
 */
@Composable
fun HomeNavigationBar(
    selected: HomeTab,
    pending: Int,
    overdue: Int,
    onSelect: (HomeTab) -> Unit
) {
    val colors = Tavan.colors
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        HomeTab.entries.forEach { entry ->
            val isSelected = entry == selected
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(entry) },
                icon = {
                    if (entry == HomeTab.PENDING && pending > 0) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = if (overdue > 0) colors.warning.strong else colors.info.strong,
                                    contentColor = MaterialTheme.colorScheme.surface
                                ) { Text(PersianNumbers.toPersian(if (pending > 99) "99+" else pending.toString())) }
                            }
                        ) { Icon(entry.icon(isSelected), contentDescription = null) }
                    } else {
                        Icon(entry.icon(isSelected), contentDescription = null)
                    }
                },
                label = {
                    Text(
                        stringResource(entry.label),
                        style = if (isSelected) MaterialTheme.typography.labelMedium.copy(
                            fontWeight = MaterialTheme.typography.labelLarge.fontWeight
                        ) else MaterialTheme.typography.labelMedium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accent.strong,
                    selectedTextColor = colors.accent.strong,
                    indicatorColor = colors.accent.container,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
