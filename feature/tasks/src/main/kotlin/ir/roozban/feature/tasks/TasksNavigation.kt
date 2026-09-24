package ir.roozban.feature.tasks

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object UpcomingRoute

@Serializable
data object InboxRoute

/** The three lists; each destination gets its own ViewModel and saved state. */
fun NavGraphBuilder.tasksScreens(onOpenSettings: () -> Unit) {
    composable<TodayRoute> { TaskListRoute(ListMode.TODAY, onOpenSettings) }
    composable<UpcomingRoute> { TaskListRoute(ListMode.UPCOMING, onOpenSettings) }
    composable<InboxRoute> { TaskListRoute(ListMode.INBOX, onOpenSettings) }
}
