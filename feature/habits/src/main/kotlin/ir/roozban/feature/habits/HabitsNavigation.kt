package ir.roozban.feature.habits

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object HabitsRoute

@Serializable
data class HabitRoute(val habitId: String)

fun NavGraphBuilder.habitsScreens(onOpenHabit: (String) -> Unit, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    composable<HabitsRoute> { HabitsScreen(onOpenHabit = onOpenHabit, onOpenSettings = onOpenSettings) }
    composable<HabitRoute> { HabitDetailScreen(onBack = onBack) }
}
