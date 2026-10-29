package ir.minerfinder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ir.minerfinder.R
import ir.minerfinder.ui.common.AppIcons
import ir.minerfinder.ui.magnetic.MagneticScreen
import ir.minerfinder.ui.search.SearchScreen
import ir.minerfinder.ui.settings.SettingsScreen
import ir.minerfinder.ui.spectrum.SpectrumScreen

enum class Tab(val label: Int, val icon: ImageVector) {
    SEARCH(R.string.tab_search, Icons.Filled.Search),
    SPECTRUM(R.string.tab_spectrum, AppIcons.Wave),
    MAGNETIC(R.string.tab_magnetic, AppIcons.Magnet),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableStateOf(Tab.SEARCH) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.SEARCH -> SearchScreen()
                Tab.SPECTRUM -> SpectrumScreen()
                Tab.MAGNETIC -> MagneticScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}
