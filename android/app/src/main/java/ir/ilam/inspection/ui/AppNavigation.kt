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
import ir.ilam.inspection.ui.persons.PersonScreen
import ir.ilam.inspection.ui.persons.PersonsScreen
import ir.ilam.inspection.ui.persons.PersonsViewModel
import ir.ilam.inspection.ui.persons.RequestsScreen
import ir.ilam.inspection.ui.stats.StatsScreen
import ir.ilam.inspection.ui.visit.VisitScreen
import ir.ilam.inspection.ui.flow.BoardScreen
import ir.ilam.inspection.ui.flow.CaseFlowScreen
import ir.ilam.inspection.ui.flow.DevicesScreen
import ir.ilam.inspection.ui.flow.FlowViewModel
import ir.ilam.inspection.ui.flow.ManageViewModel
import ir.ilam.inspection.ui.flow.NotificationsScreen
import ir.ilam.inspection.ui.flow.RegisterScreen
import ir.ilam.inspection.ui.flow.ScoresScreen

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
    const val PERSON = "person/{id}"
    const val PERSON_NEW = "person-new"
    const val PERSON_REQUESTS = "person-requests"
    const val APPROVALS = "approvals"
    const val PERFORMANCE = "performance"
    const val MAP = "map"
    const val REFERRALS = "referrals"
    const val REFERRAL = "referral/{id}"
    const val FIELD_ITEMS = "field-items"
    const val FIELD_ITEM = "field-item/{id}"
    const val CREW = "crew"
    const val BOARD = "board"
    const val CASE_FLOW = "case-flow/{id}"
    const val REGISTER = "register"
    const val SCORES = "scores"
    const val DEVICES = "devices"
    const val NOTIFICATIONS = "notifications"

    fun caseFlow(id: String) = "case-flow/$id"
    fun person(id: String) = "person/$id"

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
                onFieldItems = { navController.navigate(Routes.FIELD_ITEMS) },
                onBoard = { navController.navigate(Routes.BOARD) },
                onNotifications = { navController.navigate(Routes.NOTIFICATIONS) }
            )
        }
        composable(Routes.BOARD) {
            BoardScreen(flowViewModel(), onBack = { navController.popBackStack() },
                onOpen = { navController.navigate(Routes.caseFlow(it)) },
                onIntake = { navController.navigate(Routes.REGISTER) })
        }
        composable(Routes.CASE_FLOW, arguments = listOf(navArgument(ARG_ID) { type = NavType.StringType })) { entry ->
            CaseFlowScreen(entry.arguments?.getString(ARG_ID).orEmpty(), flowViewModel(), onBack = { navController.popBackStack() },
                onOpenCase = { navController.navigate(Routes.detail(it)) })
        }
        composable(Routes.REGISTER) {
            RegisterScreen(flowViewModel(), onBack = { navController.popBackStack() }, onCreated = { id ->
                navController.popBackStack()
                navController.navigate(Routes.caseFlow(id))
            })
        }
        composable(Routes.SCORES) { ScoresScreen(manageViewModel(), onBack = { navController.popBackStack() }) }
        composable(Routes.DEVICES) { DevicesScreen(manageViewModel(), onBack = { navController.popBackStack() }) }
        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(manageViewModel(), onBack = { navController.popBackStack() }, onOpenCase = { id ->
                // The manager and an executor follow a case's journey; an expert opens their own copy.
                navController.navigate(if (UserRole.isManager) Routes.caseFlow(id) else Routes.detail(id))
            })
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
                onDispatch = { navController.navigate(Routes.dispatch(it)) },
                onFlow = { navController.navigate(Routes.caseFlow(it)) }
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
                onCrew = { navController.navigate(Routes.CREW) },
                onScores = { navController.navigate(Routes.SCORES) },
                onDevices = { navController.navigate(Routes.DEVICES) },
                onMap = { navController.navigate(Routes.MAP) }
            )
        }
        composable(Routes.USERS) {
            PersonsScreen(personsViewModel(), onBack = { navController.popBackStack() },
                onOpen = { navController.navigate(Routes.person(it)) }, onNew = { navController.navigate(Routes.PERSON_NEW) },
                onRequests = { navController.navigate(Routes.PERSON_REQUESTS) })
        }
        composable(Routes.PERSON, arguments = listOf(navArgument(ARG_ID) { type = NavType.StringType })) { entry ->
            PersonScreen(personsViewModel(), entry.arguments?.getString(ARG_ID), onBack = { navController.popBackStack() }, onCreated = {})
        }
        composable(Routes.PERSON_NEW) {
            PersonScreen(personsViewModel(), null, onBack = { navController.popBackStack() }, onCreated = {
                navController.navigate(Routes.person(it)) { popUpTo(Routes.PERSON_NEW) { inclusive = true } }
            })
        }
        composable(Routes.PERSON_REQUESTS) {
            RequestsScreen(personsViewModel(), onBack = { navController.popBackStack() },
                onPerson = { navController.navigate(Routes.person(it)) })
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
private fun flowViewModel(): FlowViewModel {
    val container = LocalContext.current.container
    return viewModel(factory = remember { ContainerViewModelFactory(container) { FlowViewModel(it) } })
}

@Composable
private fun manageViewModel(): ManageViewModel {
    val container = LocalContext.current.container
    return viewModel(factory = remember { ContainerViewModelFactory(container) { ManageViewModel(it) } })
}

@Composable
private fun fieldItemsViewModel(): FieldItemsViewModel {
    val container = LocalContext.current.container
    return viewModel(factory = remember { ContainerViewModelFactory(container) { FieldItemsViewModel(it) } })
}

@Composable
private fun personsViewModel(): PersonsViewModel {
    val container = LocalContext.current.container
    return viewModel(factory = remember { ContainerViewModelFactory(container) { PersonsViewModel(it) } })
}
