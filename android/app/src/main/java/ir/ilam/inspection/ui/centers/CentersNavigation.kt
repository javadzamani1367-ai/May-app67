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
            onRewards = { navController.navigate(CenterRoutes.REWARDS) })
    }
    composable(CenterRoutes.ROW) { entry ->
        val parent = remember(entry) { navController.getBackStackEntry(CenterRoutes.LIST) }
        val viewModel = centersViewModel(parent)
        CenterRowScreen(viewModel, onBack = { navController.popBackStack() }, onOpen = { id -> viewModel.open(id) })
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
