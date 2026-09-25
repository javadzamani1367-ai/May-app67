package ir.roozban.feature.tasks

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import ir.roozban.feature.tasks.calendar.CalendarScreen
import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object UpcomingRoute

@Serializable
data object InboxRoute

@Serializable
data object CalendarRoute

@Serializable
data object ProjectsRoute

@Serializable
data class ProjectRoute(val projectId: String)

/** The lists and projects; each destination gets its own ViewModel and saved state. */
fun NavGraphBuilder.tasksScreens(
    onOpenSettings: () -> Unit,
    onOpenProject: (String) -> Unit,
    onBack: () -> Unit,
    onOpenAssistant: (() -> Unit)? = null,
    onOpenVoiceModels: (() -> Unit)? = null,
) {
    composable<TodayRoute> { TaskListRoute(ListMode.TODAY, onOpenSettings, onOpenAssistant = onOpenAssistant, onOpenVoiceModels = onOpenVoiceModels) }
    composable<UpcomingRoute> { TaskListRoute(ListMode.UPCOMING, onOpenSettings, onOpenVoiceModels = onOpenVoiceModels) }
    composable<InboxRoute> { TaskListRoute(ListMode.INBOX, onOpenSettings, onOpenVoiceModels = onOpenVoiceModels) }
    composable<CalendarRoute> { CalendarScreen(onOpenSettings = onOpenSettings) }
    composable<ProjectsRoute> { ProjectsScreen(onOpenProject = onOpenProject, onOpenSettings = onOpenSettings) }
    composable<ProjectRoute> { entry ->
        val route = entry.toRoute<ProjectRoute>()
        TaskListRoute(ListMode.PROJECT, onOpenSettings, projectId = route.projectId, onBack = onBack, onOpenVoiceModels = onOpenVoiceModels)
    }
}
