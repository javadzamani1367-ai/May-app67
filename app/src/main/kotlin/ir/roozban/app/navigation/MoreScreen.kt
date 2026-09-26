package ir.roozban.app.navigation

import ir.roozban.core.designsystem.components.AppMenuButton
import ir.roozban.core.designsystem.components.AssistantAction
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.roozban.app.R
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.IconBadge
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.SectionTitle
import ir.roozban.core.designsystem.theme.Role
import ir.roozban.core.designsystem.theme.Roozban
import kotlinx.serialization.Serializable

@Serializable
data object MoreRoute

/** Everything that is not a daily tab, grouped, each entry with its own color. */
@Composable
internal fun MoreScreen(
    onAssistant: () -> Unit,
    onPlanner: () -> Unit,
    onMemory: () -> Unit,
    onFocus: () -> Unit,
    onInbox: () -> Unit,
    onProjects: () -> Unit,
    onReports: () -> Unit,
    onReview: (weekly: Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    val c = Roozban.colors
    val primary = Role(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text(stringResource(R.string.more_title)) },
                navigationIcon = { AppMenuButton() },
                actions = { AssistantAction() },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionTitle(stringResource(R.string.more_group_work))
            Entry(DsR.drawable.ic_assistant, R.string.more_assistant, R.string.more_assistant_desc, c.focus, onAssistant)
            Entry(DsR.drawable.ic_schedule, R.string.more_planner, R.string.more_planner_desc, c.success, onPlanner)
            Entry(DsR.drawable.ic_timer, R.string.more_focus, R.string.more_focus_desc, c.focus, onFocus)
            Entry(DsR.drawable.ic_inbox, R.string.more_inbox, R.string.more_inbox_desc, c.info, onInbox)
            Entry(DsR.drawable.ic_folder, R.string.more_projects, R.string.more_projects_desc, c.warning, onProjects)
            SectionTitle(stringResource(R.string.more_group_insight), modifier = Modifier.padding(top = 8.dp))
            Entry(DsR.drawable.ic_bar_chart, R.string.more_reports, R.string.more_reports_desc, c.success, onReports)
            Entry(DsR.drawable.ic_review, R.string.more_daily_review, R.string.more_daily_review_desc, c.completed) { onReview(false) }
            Entry(DsR.drawable.ic_review, R.string.more_weekly_review, R.string.more_weekly_review_desc, c.streak) { onReview(true) }
            Entry(DsR.drawable.ic_memory, R.string.more_memory, R.string.more_memory_desc, c.info, onMemory)
            SectionTitle(stringResource(R.string.more_group_app), modifier = Modifier.padding(top = 8.dp))
            Entry(DsR.drawable.ic_settings, R.string.more_settings, R.string.more_settings_desc, primary, onSettings)
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun Entry(@DrawableRes icon: Int, title: Int, subtitle: Int, role: Role, onClick: () -> Unit) {
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, role, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(DsR.drawable.ic_chevron_left), null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
