package ir.roozban.app.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ir.roozban.app.R
import ir.roozban.core.designsystem.R as DsR
import kotlinx.serialization.Serializable

@Serializable
data object MoreRoute

/** Everything that is not a daily tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoreScreen(
    onFocus: () -> Unit,
    onInbox: () -> Unit,
    onProjects: () -> Unit,
    onReports: () -> Unit,
    onReview: (weekly: Boolean) -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Entry(DsR.drawable.ic_timer, R.string.more_focus, R.string.more_focus_desc, onFocus)
            Entry(DsR.drawable.ic_inbox, R.string.more_inbox, R.string.more_inbox_desc, onInbox)
            Entry(DsR.drawable.ic_folder, R.string.more_projects, null, onProjects)
            HorizontalDivider()
            Entry(DsR.drawable.ic_bar_chart, R.string.more_reports, R.string.more_reports_desc, onReports)
            Entry(DsR.drawable.ic_review, R.string.more_daily_review, null) { onReview(false) }
            Entry(DsR.drawable.ic_review, R.string.more_weekly_review, null) { onReview(true) }
            HorizontalDivider()
            Entry(DsR.drawable.ic_settings, R.string.more_settings, null, onSettings)
        }
    }
}

@Composable
private fun Entry(@DrawableRes icon: Int, title: Int, subtitle: Int?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = subtitle?.let { { Text(stringResource(it)) } },
        leadingContent = { Icon(painterResource(icon), null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
