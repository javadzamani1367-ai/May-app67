package ir.ilam.inspection.ui.users

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.ilam.inspection.R
import ir.ilam.inspection.container
import ir.ilam.inspection.data.db.UserEntity
import ir.ilam.inspection.ui.common.ConfirmDeleteButton
import ir.ilam.inspection.ui.common.ContainerViewModelFactory
import ir.ilam.inspection.ui.common.SectionCard
import ir.ilam.inspection.util.PersianNumbers
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import ir.ilam.inspection.sync.ServerApi

/**
 * Manager only. An expert reads out the code their installation shows and
 * their mobile number; both are recorded here against a name, a county and the
 * user code that will appear on every report they file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(onBack: () -> Unit) {
    val appContainer = LocalContext.current.container
    val viewModel: UsersViewModel = viewModel(
        factory = remember { ContainerViewModelFactory(appContainer) { UsersViewModel(it) } }
    )
    val users by viewModel.users.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.messageRes, state.serverMessage) {
        if (state.messageRes != null || state.serverMessage != null) {
            kotlinx.coroutines.delay(5_000)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.users_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::startNew) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.users_add))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }
            state.messageRes?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            state.serverMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            if (state.offline) {
                Text(
                    text = stringResource(R.string.users_offline),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            editing?.let { user ->
                UserEditor(
                    user = user,
                    password = password,
                    onChange = viewModel::change,
                    onPasswordChange = viewModel::setPassword,
                    onSave = viewModel::save,
                    onCancel = viewModel::cancel
                )
            }

            // The waiting installations come first: registering one is the
            // reason the manager opened this screen.
            SectionCard(title = stringResource(R.string.users_requests)) {
                Column {
                    if (state.requests.isEmpty()) {
                        Text(
                            text = stringResource(R.string.users_requests_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.requests.forEach { request ->
                        DeviceRequestRow(request = request, onAdopt = { viewModel.adopt(request) })
                    }
                    TextButton(onClick = viewModel::refresh) {
                        Text(stringResource(R.string.users_refresh))
                    }
                }
            }

            SectionCard(title = stringResource(R.string.users_registered)) {
                Column {
                    if (users.isEmpty()) {
                        Text(
                            text = stringResource(R.string.users_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    users.forEachIndexed { index, user ->
                        if (index > 0) HorizontalDivider()
                        UserRow(
                            user = user,
                            onEdit = { viewModel.edit(user) },
                            onDelete = { viewModel.delete(user) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(user: UserEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit)) {
            Text(text = user.fullName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOfNotNull(
                    PersianNumbers.toPersian(user.userCode).ifBlank { null },
                    user.county,
                    PersianNumbers.toPersian(user.phone).ifBlank { null }
                ).joinToString(" — "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            user.deviceCode?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.users_device, PersianNumbers.toPersian(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        ConfirmDeleteButton(itemName = user.fullName, onConfirm = onDelete)
    }
}

/** One installation waiting to be registered, and the button that adopts it. */
@Composable
private fun DeviceRequestRow(request: ServerApi.DeviceRequest, onAdopt: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.fullName.ifBlank { stringResource(R.string.users_full_name) },
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = listOfNotNull(
                    PersianNumbers.toPersian(request.phone).ifBlank { null },
                    request.county.ifBlank { null }
                ).joinToString(" — "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.users_device,
                    PersianNumbers.toPersian(request.deviceCode.chunked(4).joinToString("-"))
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onAdopt) { Text(stringResource(R.string.users_adopt)) }
    }
}
