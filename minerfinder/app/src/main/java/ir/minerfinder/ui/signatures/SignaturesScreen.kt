package ir.minerfinder.ui.signatures

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import ir.minerfinder.R
import ir.minerfinder.core.signature.Signature
import ir.minerfinder.core.util.Jalali
import ir.minerfinder.export.Share
import ir.minerfinder.graph
import ir.minerfinder.ui.common.BigButton
import ir.minerfinder.ui.common.Fa
import ir.minerfinder.ui.common.Field
import ir.minerfinder.ui.common.SecondaryButton
import ir.minerfinder.ui.common.Section
import ir.minerfinder.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val RECORD_SECONDS = 30

@Composable
fun SignaturesScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val library = graph.signatures
    val running by graph.scan.running.collectAsState()
    val signatures by library.signatures.collectAsState()
    val frames by library.recordedFrames.collectAsState()
    val scope = rememberCoroutineScope()
    var remaining by remember { mutableIntStateOf(0) }
    var askName by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Signature?>(null) }
    val chooser = stringResource(R.string.share_title)
    val importedText = stringResource(R.string.signatures_imported)
    val importFailed = stringResource(R.string.signatures_import_failed)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val message = runCatching { library.import(uri) }
                .fold({ importedText.format(Fa.int(it)) }, { importFailed })
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    var recording by remember { mutableStateOf(false) }
    // Cancelling flips `recording`, which cancels this countdown before it can ask for a name.
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        remaining = RECORD_SECONDS
        while (remaining > 0) {
            delay(1_000)
            remaining--
        }
        recording = false
        askName = true
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section(stringResource(R.string.signatures_record)) {
            Text(stringResource(R.string.signatures_record_help), color = Palette.Muted)
            if (recording) {
                Text(stringResource(R.string.signatures_recording, Fa.int(remaining), Fa.int(frames)), color = Palette.Warm)
                SecondaryButton(stringResource(R.string.cancel), onClick = {
                    recording = false
                    library.cancelRecording()
                })
            } else {
                BigButton(stringResource(R.string.signatures_record_button), enabled = running, onClick = {
                    library.startRecording()
                    recording = true
                })
                if (!running) Text(stringResource(R.string.calibration_needs_scan), color = Palette.Muted)
            }
        }

        MatchSection()

        Section(stringResource(R.string.signatures_library, Fa.int(signatures.size))) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(stringResource(R.string.signatures_import), onClick = { picker.launch("*/*") })
                SecondaryButton(stringResource(R.string.signatures_export_all), enabled = signatures.isNotEmpty(), onClick = {
                    Share.text(context, "minerfinder-signatures.json", "application/json", library.exportJson(), chooser)
                })
            }
            if (signatures.isEmpty()) Text(stringResource(R.string.signatures_empty), color = Palette.Muted)
        }
        signatures.forEach { s ->
            Section(s.label.ifBlank { stringResource(R.string.signatures_unnamed) }) {
                if (s.model.isNotBlank()) Field(stringResource(R.string.signatures_model), s.model)
                Field(stringResource(R.string.raw_f0), s.fundamentalHz?.let { Fa.num(it, 1) + " " + stringResource(R.string.unit_hz) } ?: "—")
                Field(stringResource(R.string.raw_partials), Fa.int(s.harmonicsHz.size))
                Field(stringResource(R.string.signatures_date), Fa.digits(Jalali.format(s.createdAtMs)))
                if (s.note.isNotBlank()) Text(s.note)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(stringResource(R.string.signatures_share), onClick = {
                        Share.text(context, "minerfinder-signature.json", "application/json", library.exportJson(s), chooser)
                    })
                    TextButton(onClick = { deleting = s }) { Text(stringResource(R.string.delete), color = Palette.Hot) }
                }
            }
        }
    }

    if (askName) {
        NameDialog(
            onSave = { label, model, note ->
                askName = false
                val signature = library.finishRecording(label, model, note)
                if (signature == null) {
                    Toast.makeText(context, R.string.calibration_failed, Toast.LENGTH_LONG).show()
                } else {
                    scope.launch { library.save(signature) }
                }
            },
            onCancel = {
                askName = false
                library.cancelRecording()
            },
        )
    }
    deleting?.let { s ->
        ConfirmDelete(s.label, onConfirm = {
            deleting = null
            scope.launch { library.delete(s.id) }
        }, onCancel = { deleting = null })
    }
}

/** Live comparison of what the microphone hears with the library. */
@Composable
fun MatchSection() {
    val graph = LocalContext.current.graph
    val matches by graph.signatures.matches.collectAsState()
    val signatures by graph.signatures.signatures.collectAsState()
    if (signatures.isEmpty()) return
    Section(stringResource(R.string.signatures_match)) {
        if (matches.isEmpty()) {
            Text(stringResource(R.string.spectrum_waiting), color = Palette.Muted)
        } else {
            matches.take(3).forEach { m ->
                val name = listOf(m.signature.label, m.signature.model).filter { it.isNotBlank() }.joinToString(" — ")
                ir.minerfinder.ui.common.ScoreBar(name.ifBlank { stringResource(R.string.signatures_unnamed) }, m.score.toFloat(),
                    if (m.score >= 80) Palette.Hot else Palette.Teal)
            }
        }
    }
}
