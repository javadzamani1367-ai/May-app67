package ir.ilam.inspection.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.model.UserRole
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tavan
import ir.ilam.inspection.ui.theme.Tone
import ir.ilam.inspection.util.PersianNumbers

private const val MIN_PASSWORD = 6

/** Expert identity, county area codes, media quality and synchronisation. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onUsers: () -> Unit) {
    val context = LocalContext.current
    val appContainer = context.container
    val viewModel: SettingsViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(appContainer) { SettingsViewModel(it) } }
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val running by viewModel.syncRunning.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val address by viewModel.address.collectAsStateWithLifecycle()
    val pendingSync by viewModel.pendingSync.collectAsStateWithLifecycle()
    val pendingServer by viewModel.pendingServer.collectAsStateWithLifecycle()
    val serverBusy by viewModel.serverBusy.collectAsStateWithLifecycle()
    val serverOutcome by viewModel.serverOutcome.collectAsStateWithLifecycle()
    val serverLastRun by viewModel.serverLastRun.collectAsStateWithLifecycle()
    val packagePassword by viewModel.packagePassword.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val expertCode = viewModel.userCode.ifBlank { settings.expertCode }
    val deviceCode = viewModel.deviceCode
    var expertName by remember(settings.expertName) { mutableStateOf(settings.expertName) }
    var areaCode by remember(settings.defaultAreaCode) { mutableStateOf(settings.defaultAreaCode) }
    var syncTarget by remember(settings.syncTarget) { mutableStateOf(settings.syncTarget) }
    var quality by remember(settings.mediaQuality) { mutableStateOf(settings.mediaQuality.toString()) }
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(4_000)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TavanTopBar(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
        ) {
            AccountCard(expertCode = expertCode, deviceCode = deviceCode)
            AppearanceCard()

            SectionCard(title = stringResource(R.string.settings_work), icon = Icons.Filled.Tune) {
                Column {
                    // The name behind a code is the manager's record. On an
                    // expert's phone it is not shown at all, and the reports
                    // that leave the phone carry only the code.
                    if (UserRole.isManager) {
                        AppTextField(stringResource(R.string.settings_expert_name), expertName, { expertName = it })
                    }
                    // Three digits: every official area code is 401 to 419.
                    NumberField(stringResource(R.string.settings_default_area), areaCode, { areaCode = it.take(3) })
                    AppTextField(
                        label = stringResource(R.string.settings_sync_target),
                        value = syncTarget,
                        onValueChange = { syncTarget = it },
                        keyboardType = KeyboardType.Uri,
                        ltr = true
                    )
                    NumberField(
                        label = stringResource(R.string.settings_media_quality),
                        value = quality,
                        onValueChange = { quality = it.take(3) },
                        imeAction = ImeAction.Done
                    )
                    PrimaryButton(
                        text = stringResource(R.string.action_save),
                        onClick = {
                            viewModel.setExpert(expertCode, expertName)
                            viewModel.setDefaultArea(areaCode)
                            viewModel.setSyncTarget(syncTarget)
                            PersianNumbers.parseIntOrNull(quality)?.let(viewModel::setMediaQuality)
                        },
                        icon = Icons.Filled.Save,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    // Confirmation belongs next to the button that caused it;
                    // at the top of a scrolling page nobody sees it.
                    message?.let {
                        Text(
                            text = stringResource(it),
                            color = Tavan.colors.success.strong,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (UserRole.isManager) {
                        SecondaryButton(
                            text = stringResource(R.string.users_title),
                            onClick = onUsers,
                            icon = Icons.Filled.ManageAccounts,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                    Hint(stringResource(R.string.settings_optional_hint))
                }
            }

            ServerSyncCard(
                configured = settings.syncTarget.isNotBlank(),
                busy = serverBusy,
                pending = pendingServer,
                lastRun = serverLastRun,
                outcome = serverOutcome,
                autoSync = settings.autoSync,
                onAutoSyncChange = { viewModel.setAutoSync(context, it) },
                onSync = viewModel::syncWithServer
            )

            SectionCard(
                title = stringResource(R.string.sync_title),
                subtitle = stringResource(R.string.sync_direct_hint),
                icon = Icons.Filled.Computer,
                tone = Tone.INFO,
                trailing = {
                    if (pendingSync > 0) StatusBadge(PersianNumbers.toPersian(pendingSync), tone = Tone.WARNING)
                }
            ) {
                Column {
                    Text(
                        stringResource(R.string.sync_pending_count, PersianNumbers.toPersian(pendingSync)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (running) {
                        address?.let {
                            ValueRow(stringResource(R.string.lock_server_address), it, ltr = true)
                        }
                        Text(
                            text = stringResource(R.string.sync_pair_code, PersianNumbers.toPersian(pairingCode)),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Tavan.colors.accent.strong,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    Hint(stringResource(R.string.sync_usb_hint))
                    PrimaryButton(
                        text = stringResource(if (running) R.string.sync_stop_server else R.string.sync_start_server),
                        onClick = viewModel::toggleServer,
                        icon = if (running) Icons.Filled.WifiOff else Icons.Filled.Wifi,
                        tone = if (running) Tone.DANGER else Tone.BRAND,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    SecondaryButton(
                        text = stringResource(R.string.sync_export_package),
                        onClick = { viewModel.exportPackage(context) },
                        icon = Icons.Filled.Inventory2,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    packagePassword?.let { password ->
                        Text(
                            text = stringResource(R.string.sync_package_password, password),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Hint(stringResource(R.string.sync_package_password_hint))
                    }
                }
            }

            SectionCard(title = stringResource(R.string.settings_security), icon = Icons.Filled.Lock, tone = Tone.NEUTRAL) {
                Column {
                    if (settings.syncTarget.isBlank()) {
                        AppTextField(
                            label = stringResource(R.string.lock_set_password),
                            value = pin,
                            onValueChange = { pin = it },
                            imeAction = ImeAction.Done,
                            password = true
                        )
                        PrimaryButton(
                            text = stringResource(R.string.action_save),
                            onClick = {
                                if (pin.length >= MIN_PASSWORD) {
                                    viewModel.changePin(pin)
                                    pin = ""
                                }
                            },
                            enabled = pin.length >= MIN_PASSWORD,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    } else {
                        // With a server configured the password lives there and
                        // the next online sign-in would overwrite anything set
                        // here, so offering the field would be a lie.
                        Text(
                            text = stringResource(R.string.settings_password_on_server),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AboutCard()
        }
    }
}
