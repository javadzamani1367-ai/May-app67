package ir.roozban.feature.tools

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.EmptyState
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.domain.NoteUseCases
import ir.roozban.core.model.Note
import java.time.ZoneId

@Composable
internal fun NotesScreen(onOpenNote: (String?) -> Unit, onBack: () -> Unit, viewModel: NotesViewModel = hiltViewModel()) {
    val (query, notes) = viewModel.state.collectAsStateWithLifecycle().value
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text("یادداشت‌های صوتی") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), "بازگشت") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onOpenNote(null) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(painterResource(DsR.drawable.ic_add), "یادداشت تازه", tint = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (notes.isNotEmpty() || query.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::search,
                        placeholder = { Text("جست‌وجو در یادداشت‌ها") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (notes.isEmpty() && query.isEmpty()) {
                item {
                    EmptyState(
                        DsR.drawable.ic_mic,
                        "هنوز یادداشتی نداری",
                        "دکمهٔ + را بزن، بعد تایپ کن یا میکروفون را بزن و حرف بزن. متن هر قدر طولانی باشد نوشته می‌شود.",
                        Roozban.colors.focus,
                    )
                }
            }
            items(notes, key = { it.id }) { note -> NoteCard(note, onClick = { onOpenNote(note.id) }) }
        }
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit) {
    AppCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                NoteUseCases.displayTitle(note).ifBlank { "بی‌عنوان" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.title.isNotBlank() && note.body.isNotBlank()) {
                Text(note.body.replace('\n', ' '), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(dateText(note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

private fun dateText(note: Note): String {
    val t = note.updatedAt.atZone(ZoneId.systemDefault())
    return PersianDateFormatter.dayMonth(t.toLocalDate().toJalali()) + "، " + PersianDateFormatter.time(t.toLocalTime())
}

/** One note: type, or tap the microphone and talk for as long as you like; read it back aloud. */
@Composable
internal fun NoteEditorScreen(
    noteId: String?,
    onOpenSpeechModels: () -> Unit,
    onOpenVoices: () -> Unit,
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val needModel by viewModel.needModel.collectAsStateWithLifecycle()
    val noVoice by viewModel.noVoice.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggleDictation()
    }
    val onMic = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted || state.dictation != DictationUi.Idle) viewModel.toggleDictation() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text(if (noteId == null) "یادداشت تازه" else "یادداشت") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), "بازگشت") } },
                actions = {
                    IconButton(onClick = viewModel::toggleReading, enabled = state.body.isNotBlank() || state.title.isNotBlank()) {
                        Icon(painterResource(if (state.reading) DsR.drawable.ic_stop else DsR.drawable.ic_play), if (state.reading) "توقف خواندن" else "خواندن متن")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(painterResource(DsR.drawable.ic_more_vert), "بیشتر") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("کپی متن") }, onClick = {
                                menu = false
                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("note", fullText(state)))
                            })
                            DropdownMenuItem(text = { Text("اشتراک‌گذاری") }, onClick = {
                                menu = false
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, fullText(state))
                                context.startActivity(Intent.createChooser(send, null))
                            })
                            DropdownMenuItem(text = { Text("انتخاب گوینده") }, onClick = {
                                menu = false
                                onOpenVoices()
                            })
                            DropdownMenuItem(text = { Text("حذف یادداشت", color = Roozban.colors.error.color) }, onClick = {
                                menu = false
                                confirmDelete = true
                            })
                        }
                    }
                },
            )
        },
        bottomBar = { DictationBar(state, onMic) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 8.dp)) {
            val colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
            TextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                placeholder = { Text("عنوان") },
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                singleLine = true,
                colors = colors,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.loaded,
            )
            TextField(
                value = state.body,
                onValueChange = viewModel::setBody,
                placeholder = { Text("بنویس، یا میکروفون را بزن و حرف بزن…") },
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = colors,
                modifier = Modifier.fillMaxWidth().weight(1f),
                enabled = state.loaded,
            )
            val words = state.body.split(Regex("\\s+")).count { it.isNotBlank() }
            Text(
                "${PersianDigits.format(words)} واژه",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    if (needModel) {
        AlertDialog(
            onDismissRequest = { viewModel.needModel.value = false },
            title = { Text("مدل گفتار فارسی لازم است") },
            text = { Text("برای تبدیل صدا به متن، یک بار مدل «گفتار فارسی» (حدود ۶۰ مگابایت) را دانلود کن. همه‌چیز روی خود گوشی انجام می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.needModel.value = false
                    onOpenSpeechModels()
                }) { Text("دانلود مدل") }
            },
            dismissButton = { TextButton(onClick = { viewModel.needModel.value = false }) { Text("بعداً") } },
        )
    }
    if (noVoice) {
        AlertDialog(
            onDismissRequest = { viewModel.noVoice.value = false },
            title = { Text("گوینده‌ای نصب نیست") },
            text = { Text("گوشی‌ات صدای فارسی آفلاین ندارد. برای خواندن متن، یکی از گوینده‌های روزبان را دانلود کن.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.noVoice.value = false
                    onOpenVoices()
                }) { Text("انتخاب گوینده") }
            },
            dismissButton = { TextButton(onClick = { viewModel.noVoice.value = false }) { Text("بعداً") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("این یادداشت حذف شود؟") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onBack)
                }) { Text("حذف", color = Roozban.colors.error.color) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } },
        )
    }
}

private fun fullText(s: NoteUiState) = listOf(s.title, s.body).filter { it.isNotBlank() }.joinToString("\n\n")

@Composable
private fun DictationBar(state: NoteUiState, onMic: () -> Unit) {
    val d = state.dictation
    AppCard(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            val pending = when (d) {
                is DictationUi.Listening -> d.pending
                is DictationUi.Finishing -> d.pending
                DictationUi.Idle -> 0
            }
            if (pending > 0 || d is DictationUi.Finishing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val level = (d as? DictationUi.Listening)?.level ?: 0f
                val scale by animateFloatAsState(1f + level * 0.35f, label = "mic")
                val listening = d is DictationUi.Listening
                Box(
                    Modifier.size(56.dp).scale(if (listening) scale else 1f).clip(CircleShape)
                        .background(if (listening) Roozban.colors.error.color else MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(onClick = onMic) {
                        Icon(
                            painterResource(if (listening) DsR.drawable.ic_stop else DsR.drawable.ic_mic),
                            if (listening) "پایان" else "شروع گفتن",
                            tint = Color.White,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    when (d) {
                        DictationUi.Idle -> "میکروفون را بزن و حرف بزن؛ متن هم‌زمان نوشته می‌شود."
                        is DictationUi.Listening -> if (d.speaking) "در حال گوش دادن…" else "گوش می‌دهم؛ هر وقت تمام شد دوباره بزن."
                        is DictationUi.Finishing -> "در حال نوشتن بخش‌های آخر…"
                    } + if (pending > 0) " (${PersianDigits.format(pending)} بخش در صف)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
