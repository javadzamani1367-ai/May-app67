package ir.ilam.inspection.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.ui.common.AppTextField
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.NumberField
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.ui.common.ValueRow
import ir.ilam.inspection.util.PersianNumbers

private const val PIN_LENGTH = 6

/** Expert identity, county area codes, media quality and synchronisation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContainer = context.container
    val viewModel: SettingsViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(appContainer) { SettingsViewModel(it) } }
    )
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val counties by viewModel.counties.collectAsStateWithLifecycle()
    val running by viewModel.syncRunning.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val address by viewModel.address.collectAsStateWithLifecycle()
    val pendingSync by viewModel.pendingSync.collectAsStateWithLifecycle()
    val packagePassword by viewModel.packagePassword.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var expertCode by remember(settings.expertCode) { mutableStateOf(settings.expertCode) }
    var expertName by remember(settings.expertName) { mutableStateOf(settings.expertName) }
    var areaCode by remember(settings.defaultAreaCode) { mutableStateOf(settings.defaultAreaCode) }
    var syncTarget by remember(settings.syncTarget) { mutableStateOf(settings.syncTarget) }
    var quality by remember(settings.mediaQuality) { mutableStateOf(settings.mediaQuality.toString()) }
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2_000)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            message?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            SectionCard(title = stringResource(R.string.settings_title)) {
                Column {
                    AppTextField(
                        stringResource(R.string.settings_expert_name),
                        expertName,
                        { expertName = it }
                    )
                    AppTextField(
                        stringResource(R.string.settings_expert_code),
                        expertCode,
                        { expertCode = it }
                    )
                    NumberField(
                        stringResource(R.string.settings_default_area),
                        areaCode,
                        { areaCode = it.take(2) }
                    )
                    AppTextField(
                        stringResource(R.string.settings_sync_target),
                        syncTarget,
                        { syncTarget = it }
                    )
                    NumberField(
                        stringResource(R.string.settings_media_quality),
                        quality,
                        { quality = it.take(3) }
                    )
                    Button(
                        onClick = {
                            viewModel.setExpert(expertCode, expertName)
                            viewModel.setDefaultArea(areaCode)
                            viewModel.setSyncTarget(syncTarget)
                            PersianNumbers.parseIntOrNull(quality)?.let(viewModel::setMediaQuality)
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }

            SectionCard(title = stringResource(R.string.settings_county_codes)) {
                Column {
                    counties.forEach { county ->
                        var code by remember(county.index, county.code) {
                            mutableStateOf(county.code)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = county.name, modifier = Modifier.weight(1f))
                            NumberField(
                                label = stringResource(R.string.settings_default_area),
                                value = code,
                                onValueChange = {
                                    code = it.take(2)
                                    viewModel.setCountyCode(county, code)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            SectionCard(title = stringResource(R.string.sync_title)) {
                Column {
                    ValueRow(
                        stringResource(R.string.sync_pending_count, PersianNumbers.toPersian(pendingSync)),
                        null
                    )
                    if (running) {
                        address?.let {
                            Text(
                                text = stringResource(R.string.sync_address, it),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.sync_pair_code,
                                PersianNumbers.toPersian(pairingCode)
                            ),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Text(
                        text = stringResource(R.string.sync_usb_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                    Button(
                        onClick = viewModel::toggleServer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (running) R.string.sync_stop_server else R.string.sync_start_server
                            )
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.exportPackage(context) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(stringResource(R.string.sync_export_package))
                    }
                    packagePassword?.let { password ->
                        Text(
                            text = stringResource(R.string.sync_package_password, password),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = stringResource(R.string.sync_package_password_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            SectionCard(title = stringResource(R.string.settings_pin)) {
                Column {
                    NumberField(
                        label = stringResource(R.string.lock_set_pin),
                        value = pin,
                        onValueChange = { if (it.length <= PIN_LENGTH) pin = it }
                    )
                    Button(
                        onClick = {
                            if (pin.length == PIN_LENGTH) {
                                viewModel.changePin(pin)
                                pin = ""
                            }
                        },
                        enabled = pin.length == PIN_LENGTH,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        }
    }
}
