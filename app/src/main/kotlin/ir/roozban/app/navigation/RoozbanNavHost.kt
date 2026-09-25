package ir.roozban.app.navigation

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.roozban.app.R
import ir.roozban.core.alarm.AppLinks
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.model.StartScreen
import ir.roozban.feature.focus.FocusMiniBar
import ir.roozban.feature.focus.FocusRoute
import ir.roozban.feature.focus.focusScreen
import ir.roozban.feature.habits.HabitRoute
import ir.roozban.feature.habits.HabitsRoute
import ir.roozban.feature.habits.habitsScreens
import ir.roozban.feature.reports.ReportsRoute
import ir.roozban.feature.reports.ReviewRoute
import ir.roozban.feature.reports.reportsScreens
import ir.roozban.feature.settings.BatteryGuideRoute
import ir.roozban.feature.settings.SettingsRoute
import ir.roozban.feature.settings.settingsScreens
import ir.roozban.feature.tasks.CalendarRoute
import ir.roozban.feature.tasks.InboxRoute
import ir.roozban.feature.tasks.ProjectRoute
import ir.roozban.feature.tasks.ProjectsRoute
import ir.roozban.feature.tasks.TodayRoute
import ir.roozban.feature.tasks.UpcomingRoute
import ir.roozban.feature.tasks.tasksScreens
import kotlin.reflect.KClass

/** Daily destinations; the rest (inbox, projects, focus, reports, reviews, settings) live under «بیشتر». */
private enum class Tab(val route: Any, val routeClass: KClass<*>, @DrawableRes val icon: Int, @StringRes val label: Int) {
    TODAY(TodayRoute, TodayRoute::class, DsR.drawable.ic_today, R.string.tab_today),
    UPCOMING(UpcomingRoute, UpcomingRoute::class, DsR.drawable.ic_upcoming, R.string.tab_upcoming),
    CALENDAR(CalendarRoute, CalendarRoute::class, DsR.drawable.ic_calendar_month, R.string.tab_calendar),
    HABITS(HabitsRoute, HabitsRoute::class, DsR.drawable.ic_habit, R.string.tab_habits),
    MORE(MoreRoute, MoreRoute::class, DsR.drawable.ic_menu, R.string.tab_more),
}

/** Screens reached from «بیشتر» keep that tab highlighted. */
private val moreChildren: List<KClass<*>> = listOf(InboxRoute::class, ProjectsRoute::class)

@Composable
fun RoozbanApp(
    startScreen: StartScreen = StartScreen.TODAY,
    openRequest: String? = null,
    onOpenHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    // Read once: changing the setting applies from the next launch, not by rebuilding the graph.
    val initialStart: Any = remember { if (startScreen == StartScreen.CALENDAR) CalendarRoute else TodayRoute }
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val current = Tab.entries.firstOrNull { tab -> destination?.hasRoute(tab.routeClass) == true }
        ?: Tab.MORE.takeIf { moreChildren.any { destination?.hasRoute(it) == true } }

    LaunchedEffect(openRequest) {
        val target = openRequest ?: return@LaunchedEffect
        when (target) {
            AppLinks.FOCUS -> navController.navigate(FocusRoute()) { launchSingleTop = true }
            AppLinks.HABITS -> navController.navigate(HabitsRoute) { launchSingleTop = true }
            AppLinks.CALENDAR -> navController.navigate(CalendarRoute) { launchSingleTop = true }
            AppLinks.DAILY_REVIEW -> navController.navigate(ReviewRoute(weekly = false)) { launchSingleTop = true }
            AppLinks.WEEKLY_REVIEW -> navController.navigate(ReviewRoute(weekly = true)) { launchSingleTop = true }
        }
        onOpenHandled()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Column {
                if (destination?.hasRoute(FocusRoute::class) != true) {
                    FocusMiniBar(onOpen = { navController.navigate(FocusRoute()) { launchSingleTop = true } })
                }
                if (current != null) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                            .shadow(12.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    ) {
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
                                label = { Text(stringResource(tab.label), fontWeight = if (tab == current) FontWeight.Bold else FontWeight.Normal) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = initialStart,
            modifier = Modifier.padding(padding),
        ) {
            val openSettings = { navController.navigate(SettingsRoute) }
            val back: () -> Unit = { navController.popBackStack() }
            tasksScreens(
                onOpenSettings = openSettings,
                onOpenProject = { navController.navigate(ProjectRoute(it)) },
                onBack = back,
            )
            habitsScreens(
                onOpenHabit = { navController.navigate(HabitRoute(it)) },
                onOpenSettings = openSettings,
                onBack = back,
            )
            focusScreen(onBack = back)
            reportsScreens(onOpenReview = { navController.navigate(ReviewRoute(it)) }, onBack = back)
            composable<MoreRoute> {
                MoreScreen(
                    onFocus = { navController.navigate(FocusRoute()) },
                    onInbox = { navController.navigate(InboxRoute) },
                    onProjects = { navController.navigate(ProjectsRoute) },
                    onReports = { navController.navigate(ReportsRoute) },
                    onReview = { navController.navigate(ReviewRoute(it)) },
                    onSettings = openSettings,
                )
            }
            settingsScreens(
                onBack = back,
                onOpenBatteryGuide = { navController.navigate(BatteryGuideRoute) },
            )
        }
    }
}
