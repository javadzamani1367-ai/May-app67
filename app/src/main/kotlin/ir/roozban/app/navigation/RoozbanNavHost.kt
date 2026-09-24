package ir.roozban.app.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.roozban.app.R
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.feature.settings.BatteryGuideRoute
import ir.roozban.feature.settings.SettingsRoute
import ir.roozban.feature.settings.settingsScreens
import ir.roozban.feature.tasks.InboxRoute
import ir.roozban.feature.tasks.ProjectRoute
import ir.roozban.feature.tasks.ProjectsRoute
import ir.roozban.feature.tasks.TodayRoute
import ir.roozban.feature.tasks.UpcomingRoute
import ir.roozban.feature.tasks.tasksScreens
import kotlin.reflect.KClass

private enum class Tab(val route: Any, val routeClass: KClass<*>, @DrawableRes val icon: Int, @StringRes val label: Int) {
    TODAY(TodayRoute, TodayRoute::class, DsR.drawable.ic_today, R.string.tab_today),
    UPCOMING(UpcomingRoute, UpcomingRoute::class, DsR.drawable.ic_upcoming, R.string.tab_upcoming),
    INBOX(InboxRoute, InboxRoute::class, DsR.drawable.ic_inbox, R.string.tab_inbox),
    PROJECTS(ProjectsRoute, ProjectsRoute::class, DsR.drawable.ic_folder, R.string.tab_projects),
}

@Composable
fun RoozbanApp(navController: NavHostController = rememberNavController()) {
    val entry by navController.currentBackStackEntryAsState()
    val current = Tab.entries.firstOrNull { tab -> entry?.destination?.hasRoute(tab.routeClass) == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (current != null) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == current,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(padding),
        ) {
            tasksScreens(
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenProject = { navController.navigate(ProjectRoute(it)) },
                onBack = { navController.popBackStack() },
            )
            settingsScreens(
                onBack = { navController.popBackStack() },
                onOpenBatteryGuide = { navController.navigate(BatteryGuideRoute) },
            )
        }
    }
}
