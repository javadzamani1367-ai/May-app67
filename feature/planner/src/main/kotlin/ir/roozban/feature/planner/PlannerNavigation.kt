package ir.roozban.feature.planner

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object PlannerRoute

@Serializable
data object MemoryRoute

fun NavGraphBuilder.plannerScreens(onOpenMemory: () -> Unit, onBack: () -> Unit) {
    composable<PlannerRoute> { PlannerScreen(onOpenMemory = onOpenMemory, onBack = onBack) }
    composable<MemoryRoute> { MemoryScreen(onBack = onBack) }
}
