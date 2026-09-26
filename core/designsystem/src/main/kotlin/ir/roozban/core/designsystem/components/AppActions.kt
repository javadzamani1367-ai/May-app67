package ir.roozban.core.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ir.roozban.core.designsystem.R
import ir.roozban.core.designsystem.theme.Roozban

/** App-wide destinations every screen can reach from its top bar, provided once by the app shell. */
data class AppActions(
    val onAssistant: (() -> Unit)? = null,
    val onTools: (() -> Unit)? = null,
    val onSettings: (() -> Unit)? = null,
)

val LocalAppActions = staticCompositionLocalOf { AppActions() }

/** The assistant shortcut for a top bar's actions; nothing when the app shell gives none. */
@Composable
fun AssistantAction() {
    val onAssistant = LocalAppActions.current.onAssistant ?: return
    IconButton(onClick = onAssistant) {
        Icon(painterResource(R.drawable.ic_assistant), stringResource(R.string.ds_assistant), tint = Roozban.colors.focus.color)
    }
}

/** «⋮» at the start of the bar (the right side in Persian): tools, assistant and settings. */
@Composable
fun AppMenuButton() {
    val actions = LocalAppActions.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.ds_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.onTools?.let { go ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ds_tools)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_mic), null) },
                    onClick = { open = false; go() },
                )
            }
            actions.onAssistant?.let { go ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ds_assistant)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_assistant), null) },
                    onClick = { open = false; go() },
                )
            }
            actions.onSettings?.let { go ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ds_settings)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_settings), null) },
                    onClick = { open = false; go() },
                )
            }
        }
    }
}
