package ir.ilam.inspection.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.ilam.inspection.container
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.fielditems.FieldItemScreen
import ir.ilam.inspection.ui.fielditems.FieldItemsScreen
import ir.ilam.inspection.ui.fielditems.FieldItemsViewModel
import ir.ilam.inspection.ui.settings.SettingsScreen

/**
 * The illegal-power executor's app: the illegal-power reports the server lets
 * this account see, each one handed to an area's crypto executor or referred
 * to an expert, and followed to its result. Nothing is kept on the phone.
 */
@Composable
fun IllegalNavigation(navController: NavHostController = rememberNavController()) {
    val container = LocalContext.current.container
    val items: FieldItemsViewModel = viewModel(factory = remember { ContainerViewModelFactory(container) { FieldItemsViewModel(it) } })
    NavHost(navController = navController, startDestination = Routes.FIELD_ITEMS) {
        composable(Routes.FIELD_ITEMS) {
            FieldItemsScreen(
                viewModel = items,
                onBack = null,
                onOpen = { navController.navigate("field-item/$it") },
                onSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.FIELD_ITEM, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            FieldItemScreen(id = entry.arguments?.getString("id").orEmpty(), viewModel = items,
                onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() }, onUsers = {})
        }
    }
}
