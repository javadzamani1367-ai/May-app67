package ir.roozban.feature.focus

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data class FocusRoute(val taskId: String? = null)

fun NavGraphBuilder.focusScreen(onBack: () -> Unit) {
    composable<FocusRoute> { entry ->
        FocusScreen(taskId = entry.toRoute<FocusRoute>().taskId, onBack = onBack)
    }
}
