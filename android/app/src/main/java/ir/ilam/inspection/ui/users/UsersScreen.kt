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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import ir.ilam.inspection.ui.common.HeaderAction
import ir.ilam.inspection.ui.common.StatusBadge
import ir.ilam.inspection.ui.common.TavanTopBar
import ir.ilam.inspection.ui.common.ToneIcon
import ir.ilam.inspection.ui.theme.Spacing
import ir.ilam.inspection.ui.theme.Tone
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.text.style.TextDirection
import ir.ilam.inspection.util.PersianNumbers
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import ir.ilam.inspection.sync.ServerApi

/**
 * Manager only. An expert reads out the code their installation shows and
 * their mobile number; both are recorded here against a name, a county and the
 * user code that will appear on every report they file.
 */
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
            TavanTopBar(
                title = stringResource(R.string.users_title),
                subtitle = stringResource(R.string.users_subtitle),
                onBack = onBack
            ) {
                HeaderAction(Icons.Filled.Refresh, stringResource(R.string.users_refresh), viewModel::refresh)
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::startNew,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text(stringResource(R.string.users_add)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = 96.dp)
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
            SectionCard(
                title = stringResource(R.string.users_requests),
                icon = Icons.Filled.PhonelinkSetup,
                tone = if (state.requests.isEmpty()) Tone.NEUTRAL else Tone.WARNING,
                trailing = {
                    if (state.requests.isNotEmpty()) {
                        StatusBadge(PersianNumbers.toPersian(state.requests.size), tone = Tone.WARNING, solid = true)
                    }
                }
            ) {
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
                }
            }

            SectionCard(
                title = stringResource(R.string.users_registered),
                icon = Icons.Filled.Groups,
                trailing = { StatusBadge(PersianNumbers.toPersian(users.size), tone = Tone.BRAND) }
            ) {
                Column {
                    if (users.isEmpty()) {
                        Text(
                            text = stringResource(R.string.users_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    users.forEachIndexed { index, user ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToneIcon(icon = Icons.Filled.Person, tone = if (user.active == 1) Tone.ACCENT else Tone.NEUTRAL, size = 40.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(text = user.fullName, style = MaterialTheme.typography.titleSmall)
            Text(
                text = listOfNotNull(
                    PersianNumbers.toPersian(user.userCode).ifBlank { null },
                    user.county,
                    PersianNumbers.toPersian(user.phone).ifBlank { null }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            user.deviceCode?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.users_device, it.chunked(4).joinToString("-")),
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToneIcon(icon = Icons.Filled.PhoneAndroid, tone = Tone.WARNING, size = 40.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(
                text = request.fullName.ifBlank { stringResource(R.string.users_full_name) },
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = listOfNotNull(
                    PersianNumbers.toPersian(request.phone).ifBlank { null },
                    request.county.ifBlank { null }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.users_device, request.deviceCode.chunked(4).joinToString("-")),
                style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(onClick = onAdopt) { Text(stringResource(R.string.users_adopt)) }
    }
}
