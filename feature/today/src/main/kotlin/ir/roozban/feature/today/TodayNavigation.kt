package ir.roozban.feature.today

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

fun NavGraphBuilder.todayScreen() {
    composable<TodayRoute> { TodayRoute() }
}
