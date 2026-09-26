package ir.roozban.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

@Serializable
data object BatteryGuideRoute

fun NavGraphBuilder.settingsScreens(onBack: () -> Unit, onOpenBatteryGuide: () -> Unit, onOpenVoices: () -> Unit, onOpenModels: () -> Unit) {
    composable<SettingsRoute> { SettingsScreen(onBack = onBack, onOpenBatteryGuide = onOpenBatteryGuide, onOpenVoices = onOpenVoices, onOpenModels = onOpenModels) }
    composable<BatteryGuideRoute> { BatteryGuideScreen(onBack = onBack) }
}
