package ir.ilam.inspection.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.ui.archive.CaseDetailScreen
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.crew.CrewScreen
import ir.ilam.inspection.ui.fielditems.FieldItemScreen
import ir.ilam.inspection.ui.fielditems.FieldItemsScreen
import ir.ilam.inspection.ui.fielditems.FieldItemsViewModel
import ir.ilam.inspection.ui.dispatch.DispatchScreen
import ir.ilam.inspection.ui.intake.IntakeScreen
import ir.ilam.inspection.ui.map.CasesMapScreen
import ir.ilam.inspection.ui.referrals.ReferralDetailScreen
import ir.ilam.inspection.ui.referrals.ReferralsScreen
import ir.ilam.inspection.ui.pending.HomeScreen
import ir.ilam.inspection.ui.settings.SettingsScreen
import ir.ilam.inspection.ui.approvals.ApprovalsScreen
import ir.ilam.inspection.ui.performance.PerformanceScreen
import ir.ilam.inspection.ui.users.UsersScreen
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
    const val USERS = "users"
    const val APPROVALS = "approvals"
    const val PERFORMANCE = "performance"
    const val MAP = "map"
    const val REFERRALS = "referrals"
    const val REFERRAL = "referral/{id}"
    const val FIELD_ITEMS = "field-items"
    const val FIELD_ITEM = "field-item/{id}"
    const val CREW = "crew"

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
                onApprovals = { navController.navigate(Routes.APPROVALS) },
                onStats = { navController.navigate(Routes.STATS) },
                onMap = { navController.navigate(Routes.MAP) },
                onReferrals = { navController.navigate(Routes.REFERRALS) },
                onFieldItems = { navController.navigate(Routes.FIELD_ITEMS) }
            )
        }
        composable(Routes.FIELD_ITEMS) {
            FieldItemsScreen(
                viewModel = fieldItemsViewModel(),
                onBack = { navController.popBackStack() },
                onOpen = { navController.navigate("field-item/$it") }
            )
        }
        composable(Routes.FIELD_ITEM, arguments = listOf(navArgument(ARG_ID) { type = NavType.StringType })) { entry ->
            FieldItemScreen(
                id = entry.arguments?.getString(ARG_ID).orEmpty(),
                viewModel = fieldItemsViewModel(),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.REFERRALS) {
            ReferralsScreen(
                onBack = { navController.popBackStack() },
                onOpen = { navController.navigate("referral/$it") }
            )
        }
        composable(Routes.REFERRAL, arguments = listOf(navArgument(ARG_ID) { type = NavType.StringType })) { entry ->
            ReferralDetailScreen(
                id = entry.arguments?.getString(ARG_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenCase = { navController.navigate(Routes.visit(it)) }
            )
        }
        composable(Routes.MAP) {
            CasesMapScreen(
                onBack = { navController.popBackStack() },
                onOpenCase = { id, pending ->
                    // The manager reviews cases; only the expert who owns one continues its visit.
                    navController.navigate(if (pending && !UserRole.isManager) Routes.visit(id) else Routes.detail(id))
                }
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
                onContinueVisit = { if (!UserRole.isManager) navController.navigate(Routes.visit(it)) },
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
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onUsers = { navController.navigate(Routes.USERS) }
            )
        }
        composable(Routes.STATS) {
            StatsScreen(
                onBack = { navController.popBackStack() },
                onPerformance = { navController.navigate(Routes.PERFORMANCE) },
                onCrew = { navController.navigate(Routes.CREW) }
            )
        }
        composable(Routes.USERS) {
            UsersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CREW) {
            CrewScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PERFORMANCE) {
            PerformanceScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.APPROVALS) {
            ApprovalsScreen(
                onBack = { navController.popBackStack() },
                onOpenCase = { navController.navigate(Routes.detail(it)) }
            )
        }
    }
}

@Composable
private fun fieldItemsViewModel(): FieldItemsViewModel {
    val container = LocalContext.current.container
    return viewModel(factory = remember { ContainerViewModelFactory(container) { FieldItemsViewModel(it) } })
}
