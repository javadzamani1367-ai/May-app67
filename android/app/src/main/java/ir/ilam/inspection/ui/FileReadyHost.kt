package ir.ilam.inspection.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.ilam.inspection.R
import ir.ilam.inspection.export.FileOutbox
import ir.ilam.inspection.export.ShareUtil
import ir.ilam.inspection.ui.common.PrimaryButton
import ir.ilam.inspection.ui.common.SecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks what to do with a file the app has just built: save it on the phone where
 * the user chooses (the system's own save dialog — Downloads, a memory card, a
 * cloud folder), or send it to WhatsApp, Telegram, Eitaa, Bale, e-mail or
 * Bluetooth. One host for every export in the app.
 */
@Composable
fun FileReadyHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file by FileOutbox.ready.collectAsStateWithLifecycle()
    val current = file
    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(current?.let { ShareUtil.mimeFor(it) } ?: "application/octet-stream")
    ) { uri ->
        val source = FileOutbox.ready.value
        if (uri != null && source != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } } != null
                }.getOrDefault(false)
            }
            Toast.makeText(context, if (ok) R.string.file_saved else R.string.file_save_failed, Toast.LENGTH_LONG).show()
        }
        FileOutbox.done()
    }
    if (current == null) return
    AlertDialog(
        onDismissRequest = { FileOutbox.done() },
        title = { Text(stringResource(R.string.file_ready_title)) },
        text = {
            Column {
                Text(current.name, modifier = Modifier.padding(bottom = 12.dp))
                PrimaryButton(stringResource(R.string.file_save_phone), onClick = { saver.launch(current.name) },
                    icon = Icons.Filled.SaveAlt, modifier = Modifier.fillMaxWidth())
                SecondaryButton(stringResource(R.string.file_send), onClick = {
                    ShareUtil.share(context, current)
                    FileOutbox.done()
                }, icon = Icons.Filled.Share, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { FileOutbox.done() }) { Text(stringResource(R.string.action_cancel)) } }
    )
}
