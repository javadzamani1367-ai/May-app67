package ir.roozban.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import ir.roozban.feature.today.TodayRoute
import ir.roozban.feature.today.todayScreen

@Composable
fun RoozbanNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = TodayRoute) {
        todayScreen()
    }
}
