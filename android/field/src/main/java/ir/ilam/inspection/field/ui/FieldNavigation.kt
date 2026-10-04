package ir.ilam.inspection.field.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.ilam.inspection.field.data.FieldKind
import ir.ilam.inspection.field.ui.feeder.FeederScreen
import ir.ilam.inspection.field.ui.home.ChoiceGroup
import ir.ilam.inspection.field.ui.home.ChoiceScreen
import ir.ilam.inspection.field.ui.home.HomeScreen
import ir.ilam.inspection.field.ui.map.MapScreen
import ir.ilam.inspection.field.ui.queue.QueueScreen
import ir.ilam.inspection.field.ui.report.ReportScreen
import ir.ilam.inspection.field.ui.thermal.ThermalScreen

private object Routes {
    const val HOME = "home"
    const val CHOICE = "choice/{group}"
    const val QUEUE = "queue"
    const val MAP = "map"
    const val REPORT = "report/{kind}?id={id}"
    const val FEEDER = "feeder?id={id}"
    const val THERMAL = "thermal?id={id}"

    fun choice(group: ChoiceGroup) = "choice/${group.name}"
    fun report(kind: FieldKind, id: String? = null) = "report/${kind.code}" + (id?.let { "?id=$it" } ?: "")
    fun feeder(id: String? = null) = "feeder" + (id?.let { "?id=$it" } ?: "")
    fun thermal(id: String? = null) = "thermal" + (id?.let { "?id=$it" } ?: "")
}

private val optionalId = navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }

/** Home, the two option screens under it, the forms, and the queue — never more than two taps from anything. */
@Composable
fun FieldNavigation(onSignInAgain: () -> Unit) {
    val nav = rememberNavController()
    fun open(kind: FieldKind, id: String? = null) = nav.navigate(
        when (kind) {
            FieldKind.CRYPTO, FieldKind.ILLEGAL -> Routes.report(kind, id)
            FieldKind.FEEDER -> Routes.feeder(id)
            FieldKind.THERMAL -> Routes.thermal(id)
        }
    )
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpen = { nav.navigate(Routes.choice(it)) },
                onQueue = { nav.navigate(Routes.QUEUE) },
                onRecording = { open(FieldKind.THERMAL, it) },
                onMap = { nav.navigate(Routes.MAP) },
                onSignInAgain = onSignInAgain
            )
        }
        composable(Routes.CHOICE) { entry ->
            val group = ChoiceGroup.valueOf(entry.arguments?.getString("group") ?: ChoiceGroup.REPORT.name)
            ChoiceScreen(group = group, onBack = { nav.popBackStack() }, onPick = { open(it) })
        }
        composable(Routes.REPORT, arguments = listOf(navArgument("kind") { type = NavType.IntType }, optionalId)) { entry ->
            ReportScreen(
                kind = FieldKind.of(entry.arguments?.getInt("kind") ?: FieldKind.CRYPTO.code),
                itemId = entry.arguments?.getString("id"),
                onClose = { nav.popBackStack() }
            )
        }
        composable(Routes.FEEDER, arguments = listOf(optionalId)) { entry ->
            FeederScreen(itemId = entry.arguments?.getString("id"), onClose = { nav.popBackStack() })
        }
        composable(Routes.MAP) {
            MapScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.QUEUE) {
            QueueScreen(
                onBack = { nav.popBackStack() },
                onSignInAgain = onSignInAgain,
                onOpenDraft = { item -> open(FieldKind.of(item.kind), item.id) }
            )
        }
        composable(Routes.THERMAL, arguments = listOf(optionalId)) { entry ->
            ThermalScreen(itemId = entry.arguments?.getString("id"), onClose = { nav.popBackStack() })
        }
    }
}
