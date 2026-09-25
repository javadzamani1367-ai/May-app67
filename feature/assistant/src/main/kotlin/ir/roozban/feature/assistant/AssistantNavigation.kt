package ir.roozban.feature.assistant

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object AssistantRoute

@Serializable
data object AssistantModelsRoute

fun NavGraphBuilder.assistantScreens(onOpenModels: () -> Unit, onBack: () -> Unit) {
    composable<AssistantRoute> { AssistantScreen(onBack = onBack, onOpenModels = onOpenModels) }
    composable<AssistantModelsRoute> { ModelsScreen(onBack = onBack) }
}
