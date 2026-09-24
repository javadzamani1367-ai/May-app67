package ir.roozban.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.domain.RestoreMode
import ir.roozban.core.domain.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val MIN_PASSWORD = 6

/** Create an encrypted backup file, or restore one, through the system file picker. */
@Composable
internal fun BackupCard(viewModel: SettingsViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askPasswordForCreate by remember { mutableStateOf(false) }
    var pendingPassword by remember { mutableStateOf<CharArray?>(null) }
    var fileToRestore by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }

    val saved = stringResource(R.string.backup_saved)
    val failed = stringResource(R.string.backup_failed)
    val wrongPassword = stringResource(R.string.backup_wrong_password)
    val invalid = stringResource(R.string.backup_invalid)

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = pendingPassword
        pendingPassword = null
        if (uri == null || password == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = runCatching {
                val bytes = viewModel.createBackup(password)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("no stream")
                }
            }.isSuccess
            password.fill(' ')
            busy = false
            snackbar.showSnackbar(if (ok) saved else failed)
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) fileToRestore = uri
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.backup_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { askPasswordForCreate = true }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.backup_create))
                }
                OutlinedButton(onClick = { openLauncher.launch(arrayOf("*/*")) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.backup_restore))
                }
            }
            if (busy) Text(stringResource(R.string.backup_working), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (askPasswordForCreate) {
        PasswordDialog(
            confirmTwice = true,
            showMode = false,
            onConfirm = { password, _ ->
                askPasswordForCreate = false
                pendingPassword = password
                val j = LocalDate.now().toJalali()
                createLauncher.launch("roozban-${j.year}-${"%02d".format(j.month)}-${"%02d".format(j.day)}.rzb")
            },
            onDismiss = { askPasswordForCreate = false },
        )
    }
    fileToRestore?.let { uri ->
        PasswordDialog(
            confirmTwice = false,
            showMode = true,
            onConfirm = { password, mode ->
                fileToRestore = null
                busy = true
                scope.launch {
                    val message = runCatching {
                        val bytes = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("no stream")
                        }
                        when (val result = viewModel.restore(bytes, password, mode)) {
                            is RestoreResult.Success ->
                                context.getString(R.string.backup_restored, PersianDigits.format(result.tasks), PersianDigits.format(result.projects))
                            RestoreResult.WrongPassword -> wrongPassword
                            is RestoreResult.Invalid -> invalid
                        }
                    }.getOrElse { invalid }
                    password.fill(' ')
                    busy = false
                    snackbar.showSnackbar(message)
                }
            },
            onDismiss = { fileToRestore = null },
        )
    }
}

@Composable
private fun PasswordDialog(
    confirmTwice: Boolean,
    showMode: Boolean,
    onConfirm: (CharArray, RestoreMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(RestoreMode.REPLACE) }
    val valid = password.length >= MIN_PASSWORD && (!confirmTwice || password == repeat)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (showMode) R.string.backup_restore else R.string.backup_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    supportingText = { Text(stringResource(R.string.backup_password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (confirmTwice) {
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        label = { Text(stringResource(R.string.backup_password_repeat)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = repeat.isNotEmpty() && repeat != password,
                    )
                }
                if (showMode) {
                    listOf(RestoreMode.REPLACE to R.string.backup_mode_replace, RestoreMode.MERGE to R.string.backup_mode_merge).forEach { (m, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == m, onClick = { mode = m })
                            Text(stringResource(label))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password.toCharArray(), mode) }, enabled = valid) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
