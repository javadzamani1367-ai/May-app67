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
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(4_000)
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
            message?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            editing?.let { user ->
                UserEditor(
                    user = user,
                    onChange = viewModel::change,
                    onSave = viewModel::save,
                    onCancel = viewModel::cancel
                )
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
