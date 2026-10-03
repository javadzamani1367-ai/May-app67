package ir.ilam.inspection.field.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.ilam.inspection.field.R
import ir.ilam.inspection.field.data.Permission
import ir.ilam.inspection.field.data.db.QueueCounts
import ir.ilam.inspection.field.field
import ir.ilam.inspection.field.ui.queue.QueueSummary
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import kotlinx.coroutines.launch

/** The two groups the home screen opens, each with its own two choices. */
enum class ChoiceGroup { INSPECT, REPORT }

/**
 * Two large options in the middle of the screen — inspect, report — each on
 * or off according to what the manager granted this account. The server
 * enforces the same permission on every item, so this is guidance, not the
 * lock.
 */
@Composable
fun HomeScreen(onOpen: (ChoiceGroup) -> Unit, onQueue: () -> Unit, onSignInAgain: () -> Unit) {
    val container = LocalContext.current.field
    val counts by container.database.dao().observeCounts().collectAsState(initial = QueueCounts(0, 0, 0))
    var permissions by remember { mutableIntStateOf(container.prefs.permissions) }
    var expired by remember { mutableStateOf(container.prefs.sessionExpired) }
    var name by remember { mutableStateOf(container.prefs.fullName) }
    val reread = {
        permissions = container.prefs.permissions
        expired = container.prefs.sessionExpired
        name = container.prefs.fullName
    }
    // Read again whenever the queue changes: a sync that just ran may have
    // brought new permissions or ended the session.
    LaunchedEffect(counts) { reread() }
    // And ask the server every time the screen comes to the front, so a
    // permission the manager has just granted shows without waiting for the
    // hourly sync. Offline this leaves the last known state in place.
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            container.sync.refreshProfile()
            reread()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TavanTopBar(
            title = stringResource(R.string.app_name),
            subtitle = name.takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.field_home_greeting, it) },
            showBrand = true
        ) {
            HeaderAction(
                icon = Icons.Filled.CloudUpload,
                contentDescription = stringResource(R.string.field_queue),
                onClick = onQueue,
                badge = counts.pending + counts.failed
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen)
        ) {
            if (expired) SessionBanner(onSignInAgain)
            OptionCard(
                title = stringResource(R.string.field_home_inspect),
                hint = stringResource(R.string.field_home_inspect_hint),
                icon = Icons.Filled.Thermostat,
                tone = Tone.ACCENT,
                enabled = Permission.has(permissions, Permission.INSPECT),
                onClick = { onOpen(ChoiceGroup.INSPECT) }
            )
            OptionCard(
                title = stringResource(R.string.field_home_report),
                hint = stringResource(R.string.field_home_report_hint),
                icon = Icons.AutoMirrored.Filled.Assignment,
                tone = Tone.DANGER,
                enabled = Permission.has(permissions, Permission.REPORT),
                onClick = { onOpen(ChoiceGroup.REPORT) }
            )
            QueueSummary(counts = counts, onOpen = onQueue)
        }
    }
}
