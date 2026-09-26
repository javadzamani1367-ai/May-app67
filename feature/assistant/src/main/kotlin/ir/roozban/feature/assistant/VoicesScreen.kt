package ir.roozban.feature.assistant

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.ai.models.ModelCatalog
import ir.roozban.ai.models.ModelSpec
import ir.roozban.ai.runtime.DownloadState
import ir.roozban.ai.tts.SpeakState
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.SectionTitle
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.model.SpeechSettings
import java.util.Locale

/** Choosing the voice that reads text aloud: Roozban's downloadable voices or the phone's own. */
@Composable
internal fun VoicesScreen(onBack: () -> Unit, viewModel: VoicesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val confirmMobile by viewModel.confirmMobile.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<ModelSpec?>(null) }
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    val selected = state.speech.voice
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text("گوینده و خواندن متن") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), "بازگشت") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ReadingCard(state.speech, viewModel) }
            item {
                VoiceRow(
                    name = "انتخاب خودکار",
                    detail = "اولین گوینده‌ی موجود: گوینده‌های روزبان، و اگر نبود صدای خود گوشی",
                    selected = selected == null,
                    onSelect = { viewModel.select(null) },
                )
            }
            item { SectionTitle("گوینده‌های روزبان (آفلاین)", icon = DsR.drawable.ic_mic, color = Roozban.colors.focus.color) }
            item {
                Text(
                    "صدای طبیعی فارسی که کاملاً روی گوشی ساخته می‌شود. هر گوینده یک بار دانلود می‌شود. پیش از انتخاب، «نمونه» را بزن تا صدایش را بشنوی.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            items(ModelCatalog.voices, key = { it.id }) { spec ->
                if (spec.id in state.models.voicesInstalled) {
                    val key = "piper:${spec.id}"
                    VoiceRow(
                        name = spec.name,
                        detail = spec.description,
                        selected = selected == key,
                        onSelect = { viewModel.select(key) },
                        playing = state.speaking.isPreview(key),
                        onPreview = { viewModel.preview(key) },
                        onDelete = { confirmDelete = spec },
                    )
                } else {
                    CatalogCard(
                        spec,
                        recommended = spec.id == ModelCatalog.voices.first().id,
                        download = state.models.downloads[spec.id],
                        onDownload = { viewModel.download(spec) },
                        onCancel = { viewModel.cancel(spec) },
                        onDiscard = { viewModel.discard(spec) },
                    )
                }
            }
            item { SectionTitle("صدای خود گوشی", icon = DsR.drawable.ic_settings, color = Roozban.colors.info.color) }
            val system = state.systemVoices
            when {
                system == null -> item { CircularProgressIndicator(Modifier.size(24.dp)) }
                system.isEmpty() -> item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "موتور صدای گوشی‌ات صدای فارسی آفلاین ندارد. می‌توانی از تنظیمات گوشی صدای فارسی را نصب کنی، یا یکی از گوینده‌های روزبان را دانلود کنی.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(onClick = {
                                try {
                                    context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (e: ActivityNotFoundException) {
                                    // No such screen on this phone.
                                }
                            }) { Text("تنظیمات صدای گوشی") }
                        }
                    }
                }
                else -> items(system, key = { it.key }) { v ->
                    VoiceRow(
                        name = v.name,
                        detail = "موتور صدای گوشی، بدون اینترنت",
                        selected = selected == v.key,
                        onSelect = { viewModel.select(v.key) },
                        playing = state.speaking.isPreview(v.key),
                        onPreview = { viewModel.preview(v.key) },
                    )
                }
            }
        }
    }
    confirmMobile?.let { spec ->
        val left = spec.sizeBytes - ((state.models.downloads[spec.id] as? DownloadState.Failed)?.partialBytes ?: 0L)
        AlertDialog(
            onDismissRequest = viewModel::dismissMobile,
            title = { Text("دانلود با اینترنت همراه") },
            text = { Text("به وای‌فای وصل نیستی. حدود ${formatSize(left)} از بستهٔ اینترنت همراهت مصرف می‌شود. ادامه می‌دهی؟") },
            confirmButton = { TextButton(onClick = { viewModel.download(spec, confirmedMobile = true) }) { Text("دانلود") } },
            dismissButton = { TextButton(onClick = viewModel::dismissMobile) { Text("انصراف") } },
        )
    }
    confirmDelete?.let { spec ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("حذف گوینده‌ی ${spec.name}؟") },
            text = { Text("حدود ${formatSize(spec.sizeBytes)} از حافظهٔ گوشی آزاد می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(spec.id)
                    confirmDelete = null
                }) { Text("حذف", color = Roozban.colors.error.color) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("انصراف") } },
        )
    }
}

private fun SpeakState.isPreview(key: String): Boolean {
    val id = VoicesViewModel.PREVIEW + key
    return this is SpeakState.Speaking && this.id == id || this is SpeakState.Preparing && this.id == id
}

@Composable
private fun ReadingCard(s: SpeechSettings, vm: VoicesViewModel) {
    var rate by remember(s.rate) { mutableFloatStateOf(s.rate) }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("خواندن پاسخ‌های دستیار", fontWeight = FontWeight.Bold)
                    Text(
                        "هر پاسخ دستیار بلند خوانده می‌شود. کنار هر پاسخ و هر یادداشت هم دکمه‌ی خواندن هست.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.readReplies, onCheckedChange = vm::setReadReplies)
            }
            Text("سرعت خواندن: ×" + String.format(Locale.US, "%.1f", rate).map { if (it.isDigit()) '۰' + (it - '0') else if (it == '.') '٫' else it }.joinToString(""))
            Slider(
                value = rate,
                onValueChange = { rate = it },
                onValueChangeFinished = { vm.setRate(rate) },
                valueRange = SpeechSettings.MIN_RATE..SpeechSettings.MAX_RATE,
                steps = 9,
            )
        }
    }
}

@Composable
private fun VoiceRow(
    name: String,
    detail: String,
    selected: Boolean,
    onSelect: () -> Unit,
    playing: Boolean = false,
    onPreview: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    AppCard(modifier = Modifier.fillMaxWidth(), accent = if (selected) Roozban.colors.focus.color else null) {
        Row(Modifier.clickable(onClick = onSelect).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onPreview != null) {
                TextButton(onClick = onPreview) {
                    Icon(painterResource(if (playing) DsR.drawable.ic_stop else DsR.drawable.ic_play), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (playing) "توقف" else "نمونه")
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(painterResource(DsR.drawable.ic_delete), "حذف") }
            }
        }
    }
}
