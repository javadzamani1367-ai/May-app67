package ir.roozban.feature.assistant

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object AssistantRoute

@Serializable
data object AssistantModelsRoute

@Serializable
data object AssistantVoicesRoute

fun NavGraphBuilder.assistantScreens(onOpenModels: () -> Unit, onOpenVoices: () -> Unit, onBack: () -> Unit) {
    composable<AssistantRoute> { AssistantScreen(onBack = onBack, onOpenModels = onOpenModels, onOpenVoices = onOpenVoices) }
    composable<AssistantModelsRoute> { ModelsScreen(onBack = onBack) }
    composable<AssistantVoicesRoute> { VoicesScreen(onBack = onBack) }
}
