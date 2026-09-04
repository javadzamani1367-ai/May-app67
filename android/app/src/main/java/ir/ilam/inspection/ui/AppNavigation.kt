package ir.ilam.inspection.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.ilam.inspection.ui.archive.CaseDetailScreen
import ir.ilam.inspection.ui.dispatch.DispatchScreen
import ir.ilam.inspection.ui.intake.IntakeScreen
import ir.ilam.inspection.ui.pending.HomeScreen
import ir.ilam.inspection.ui.settings.SettingsScreen
import ir.ilam.inspection.ui.stats.StatsScreen
import ir.ilam.inspection.ui.visit.VisitScreen

/** Route names kept in one place so no screen has to guess a string. */
object Routes {
    const val HOME = "home"
    const val INTAKE = "intake"
    const val VISIT = "visit/{id}"
    const val DETAIL = "case/{id}"
    const val DISPATCH = "dispatch/{id}"
    const val SETTINGS = "settings"
    const val STATS = "stats"

    fun visit(id: String) = "visit/$id"
    fun detail(id: String) = "case/$id"
    fun dispatch(id: String) = "dispatch/$id"
}

private const val ARG_ID = "id"

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNewReport = { navController.navigate(Routes.INTAKE) },
                onOpenCase = { navController.navigate(Routes.detail(it)) },
                onContinueVisit = { navController.navigate(Routes.visit(it)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onStats = { navController.navigate(Routes.STATS) }
            )
        }
        composable(Routes.INTAKE) {
            IntakeScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.popBackStack()
                    navController.navigate(Routes.visit(id))
                }
            )
        }
        composable(Routes.VISIT, arguments = listOf(navArgument(ARG_ID) { type = NavType.StringType })) { entry ->
            VisitScreen(
                reportId = entry.arguments?.getString(ARG_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                onFinished = { id ->
                    navController.popBackStack()
                    navController.navigate(Routes.detail(id))
                }
            )
        }
        composable(Routes.DETAIL, arguments = listOf(navArgument(ARG_ID) { type = NavType.StringType })) { entry ->
            CaseDetailScreen(
                reportId = entry.arguments?.getString(ARG_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                onContinueVisit = { navController.navigate(Routes.visit(it)) },
                onDispatch = { navController.navigate(Routes.dispatch(it)) }
            )
        }
        composable(Routes.DISPATCH, arguments = listOf(navArgument(ARG_ID) { type = NavType.StringType })) { entry ->
            DispatchScreen(
                reportId = entry.arguments?.getString(ARG_ID).orEmpty(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.STATS) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
    }
}
