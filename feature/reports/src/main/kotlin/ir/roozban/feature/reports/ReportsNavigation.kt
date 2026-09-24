package ir.roozban.feature.reports

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object ReportsRoute

@Serializable
data class ReviewRoute(val weekly: Boolean)

fun NavGraphBuilder.reportsScreens(onOpenReview: (weekly: Boolean) -> Unit, onBack: () -> Unit) {
    composable<ReportsRoute> { ReportsScreen(onOpenReview = onOpenReview, onBack = onBack) }
    composable<ReviewRoute> { ReviewScreen(onBack = onBack) }
}
