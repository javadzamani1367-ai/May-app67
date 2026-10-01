package ir.ilam.inspection.field.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ir.ilam.inspection.field.ui.home.ChoiceGroup
import ir.ilam.inspection.field.ui.home.ChoiceScreen
import ir.ilam.inspection.field.ui.home.HomeScreen
import ir.ilam.inspection.field.ui.home.SoonScreen
import ir.ilam.inspection.field.ui.queue.QueueScreen

private object Routes {
    const val HOME = "home"
    const val CHOICE = "choice/{group}"
    const val QUEUE = "queue"
    const val SOON = "soon"

    fun choice(group: ChoiceGroup) = "choice/${group.name}"
}

/** Home, the two option screens under it, and the queue — never more than two taps from anything. */
@Composable
fun FieldNavigation(onSignInAgain: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpen = { nav.navigate(Routes.choice(it)) },
                onQueue = { nav.navigate(Routes.QUEUE) },
                onSignInAgain = onSignInAgain
            )
        }
        composable(Routes.CHOICE) { entry ->
            val group = ChoiceGroup.valueOf(entry.arguments?.getString("group") ?: ChoiceGroup.REPORT.name)
            ChoiceScreen(group = group, onBack = { nav.popBackStack() }, onPick = { nav.navigate(Routes.SOON) })
        }
        composable(Routes.QUEUE) {
            QueueScreen(onBack = { nav.popBackStack() }, onSignInAgain = onSignInAgain)
        }
        composable(Routes.SOON) {
            SoonScreen(onBack = { nav.popBackStack() })
        }
    }
}
