package ir.ilam.inspection.ui.centers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import ir.ilam.inspection.container
import ir.ilam.inspection.ui.common.ContainerViewModelFactory

/** Routes of the comprehensive centre statistics; every screen shares the list's model. */
object CenterRoutes {
    const val LIST = "centers"
    const val ROW = "center-row"
    const val IMPORT = "center-import"
    const val REWARDS = "center-rewards"
    const val RULES = "center-rules"
    const val SETTINGS = "center-settings"
    const val FORM = "center-form/{row}/{kind}"
    fun form(row: String, kind: String) = "center-form/$row/$kind"
}

fun NavGraphBuilder.centerScreens(navController: NavHostController) {
    composable(CenterRoutes.LIST) {
        val viewModel = centersViewModel()
        CentersScreen(viewModel, onBack = { navController.popBackStack() },
            onOpen = { id ->
                viewModel.open(id)
                navController.navigate(CenterRoutes.ROW)
            },
            onImport = { navController.navigate(CenterRoutes.IMPORT) },
            onRewards = { navController.navigate(CenterRoutes.REWARDS) },
            onRules = { navController.navigate(CenterRoutes.RULES) },
            onSettings = { navController.navigate(CenterRoutes.SETTINGS) })
    }
    composable(CenterRoutes.RULES) { CenterRulesScreen(adminViewModel(), onBack = { navController.popBackStack() }) }
    composable(CenterRoutes.SETTINGS) { CenterSettingsScreen(adminViewModel(), onBack = { navController.popBackStack() }) }
    composable(CenterRoutes.ROW) { entry ->
        val parent = remember(entry) { navController.getBackStackEntry(CenterRoutes.LIST) }
        val viewModel = centersViewModel(parent)
        CenterRowScreen(viewModel, onBack = { navController.popBackStack() }, onOpen = { id -> viewModel.open(id) },
            onForm = { row, kind -> navController.navigate(CenterRoutes.form(row, kind)) })
    }
    composable(CenterRoutes.FORM) { entry ->
        val container = LocalContext.current.container
        val form: RewardFormViewModel = viewModel(factory = remember { ContainerViewModelFactory(container) { RewardFormViewModel(it) } })
        val parent = remember(entry) { navController.getBackStackEntry(CenterRoutes.LIST) }
        val list = centersViewModel(parent)
        RewardFormScreen(form, entry.arguments?.getString("row").orEmpty(), entry.arguments?.getString("kind").orEmpty(),
            onBack = {
                // The row's amounts follow the form: read it again on the way back.
                list.state.value.detail?.row?.id?.let(list::open)
                navController.popBackStack()
            })
    }
    composable(CenterRoutes.IMPORT) { entry ->
        val parent = remember(entry) { navController.getBackStackEntry(CenterRoutes.LIST) }
        CenterImportScreen(centersViewModel(parent), onBack = { navController.popBackStack() })
    }
    composable(CenterRoutes.REWARDS) { entry ->
        val parent = remember(entry) { navController.getBackStackEntry(CenterRoutes.LIST) }
        CenterRewardsScreen(centersViewModel(parent), onBack = { navController.popBackStack() })
    }
}

@Composable
private fun centersViewModel(owner: ViewModelStoreOwner? = null): CentersViewModel {
    val container = LocalContext.current.container
    val factory = remember { ContainerViewModelFactory(container) { CentersViewModel(it) } }
    return if (owner != null) viewModel(owner, factory = factory) else viewModel(factory = factory)
}

@Composable
private fun adminViewModel(): CenterAdminViewModel {
    val container = LocalContext.current.container
    return viewModel(factory = remember { ContainerViewModelFactory(container) { CenterAdminViewModel(it) } })
}
