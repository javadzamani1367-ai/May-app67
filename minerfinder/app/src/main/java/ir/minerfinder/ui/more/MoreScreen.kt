package ir.minerfinder.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.minerfinder.R
import ir.minerfinder.ui.common.Header
import ir.minerfinder.ui.common.Section
import ir.minerfinder.ui.sessions.SessionsScreen
import ir.minerfinder.ui.settings.SettingsScreen
import ir.minerfinder.ui.signatures.SignaturesScreen
import ir.minerfinder.ui.theme.Palette

enum class MorePage(val title: Int, val help: Int) {
    SESSIONS(R.string.sessions_title, R.string.sessions_help),
    SIGNATURES(R.string.signatures_title, R.string.signatures_help),
    SETTINGS(R.string.tab_settings, R.string.settings_help),
}

/** Everything that does not need a tab of its own. */
@Composable
fun MoreScreen(onShowMap: () -> Unit) {
    var page by rememberSaveable { mutableStateOf<MorePage?>(null) }
    BackHandler(enabled = page != null) { page = null }

    when (page) {
        null -> Column(Modifier.fillMaxSize()) {
            Header(stringResource(R.string.tab_more))
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MorePage.entries.forEach { p ->
                    Section(stringResource(p.title), Modifier.clickable { page = p }) {
                        Text(stringResource(p.help), color = Palette.Muted)
                    }
                }
            }
        }
        MorePage.SETTINGS -> SettingsScreen()
        MorePage.SIGNATURES -> Column(Modifier.fillMaxSize()) {
            Header(stringResource(R.string.signatures_title))
            TextButton(onClick = { page = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
            SignaturesScreen()
        }
        MorePage.SESSIONS -> Column(Modifier.fillMaxSize()) {
            Header(stringResource(R.string.sessions_title))
            TextButton(onClick = { page = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.back)) }
            SessionsScreen(onShowMap)
        }
    }
}
