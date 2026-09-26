package ir.roozban.feature.assistant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.ai.core.DeviceTier
import ir.roozban.ai.models.InstalledModel
import ir.roozban.ai.models.ModelCatalog
import ir.roozban.ai.models.ModelSource
import ir.roozban.ai.models.ModelSpec
import ir.roozban.ai.runtime.DownloadState
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.IconBadge
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.components.SectionTitle
import ir.roozban.core.designsystem.components.StatusPill
import ir.roozban.core.designsystem.theme.Roozban
import ir.roozban.core.designsystem.R as DsR

@Composable
internal fun ModelsScreen(onBack: () -> Unit, viewModel: ModelsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val confirmMobile by viewModel.confirmMobile.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<InstalledModel?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::import) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text("مدل‌های دستیار") },
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
            item { DeviceCard(state.tier, state.wifiOnly, viewModel::setWifiOnly) }
            if (state.installed.isNotEmpty()) {
                item { SectionTitle("دستیار: نصب‌شده", icon = DsR.drawable.ic_check, color = Roozban.colors.completed.color) }
                items(state.installed, key = { "i-" + it.id }) { m ->
                    InstalledCard(m, active = state.active?.id == m.id, onActivate = { viewModel.activate(m.id) }, onDelete = { confirmDelete = m })
                }
            }
            val available = state.available.filter { spec -> state.installed.none { it.id == spec.id } }
            if (available.isNotEmpty()) {
                item { SectionTitle("دستیار: قابل دانلود", icon = DsR.drawable.ic_download) }
                items(available, key = { "c-" + it.id }) { spec ->
                    CatalogCard(
                        spec,
                        recommended = spec.id == ModelCatalog.recommendedIdFor(state.tier),
                        download = state.downloads[spec.id],
                        onDownload = { viewModel.download(spec) },
                        onCancel = { viewModel.cancel(spec) },
                        onDiscard = { viewModel.discard(spec) },
                    )
                }
            }
            item {
                SectionTitle("ورودی صوتی (تبدیل صدا به متن)", icon = DsR.drawable.ic_mic, color = Roozban.colors.info.color)
                Text(
                    "با دکمهٔ میکروفون در افزودن سریع و گفتگو، به فارسی بگو؛ متن را قبل از ثبت می‌بینی و ویرایش می‌کنی.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            items(state.speechInstalled, key = { "si-" + it.id }) { m ->
                InstalledCard(m, active = state.activeSpeech?.id == m.id, onActivate = { viewModel.activateSpeech(m.id) }, onDelete = { confirmDelete = m })
            }
            items(state.speechAvailable.filter { spec -> state.speechInstalled.none { it.id == spec.id } }, key = { "sc-" + it.id }) { spec ->
                CatalogCard(
                    spec,
                    recommended = spec.id == ModelCatalog.recommendedSpeechIdFor(state.tier),
                    download = state.downloads[spec.id],
                    onDownload = { viewModel.download(spec) },
                    onCancel = { viewModel.cancel(spec) },
                    onDiscard = { viewModel.discard(spec) },
                )
            }
            item {
                AppCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(DsR.drawable.ic_file_open, Roozban.colors.info)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("وارد کردن فایل GGUF", fontWeight = FontWeight.Bold)
                            Text(
                                "اگر مدل را جای دیگری دانلود کرده‌ای، از حافظهٔ گوشی انتخابش کن.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (importing) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        } else {
                            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("انتخاب") }
                        }
                    }
                }
            }
            item {
                Text(
                    "مدل‌ها فقط یک بار دانلود می‌شوند و بعد از آن دستیار کاملاً آفلاین کار می‌کند. هیچ متنی از گفتگوها یا کارهایت از گوشی خارج نمی‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
    confirmMobile?.let { spec ->
        val left = spec.sizeBytes - ((state.downloads[spec.id] as? DownloadState.Failed)?.partialBytes ?: 0L)
        AlertDialog(
            onDismissRequest = viewModel::dismissMobile,
            title = { Text("دانلود با اینترنت همراه") },
            text = { Text("به وای‌فای وصل نیستی. حدود ${formatSize(left)} از بستهٔ اینترنت همراهت مصرف می‌شود. ادامه می‌دهی؟") },
            confirmButton = { TextButton(onClick = { viewModel.download(spec, confirmedMobile = true) }) { Text("دانلود") } },
            dismissButton = { TextButton(onClick = viewModel::dismissMobile) { Text("انصراف") } },
        )
    }
    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("حذف ${m.name}؟") },
            text = { Text("${formatSize(m.sizeBytes)} از حافظهٔ گوشی آزاد می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(m.id)
                    confirmDelete = null
                }) { Text("حذف", color = Roozban.colors.error.color) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun DeviceCard(tier: DeviceTier, wifiOnly: Boolean, onWifiOnly: (Boolean) -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(DsR.drawable.ic_memory, if (tier.supported) Roozban.colors.focus else Roozban.colors.warning)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("توان این گوشی", fontWeight = FontWeight.Bold)
                    Text(
                        when (tier) {
                            DeviceTier.UNSUPPORTED -> "رم کمتر از ۳ گیگابایت: دستیار آفلاین قابل اجرا نیست."
                            DeviceTier.LIGHT -> "سبک: مدل‌های کوچک پیشنهاد می‌شوند."
                            DeviceTier.STANDARD -> "استاندارد: مدل پیشنهادی به‌خوبی اجرا می‌شود."
                            DeviceTier.HIGH -> "قوی: مدل‌های بزرگ‌تر هم قابل استفاده‌اند."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("دانلود فقط با وای‌فای", Modifier.weight(1f))
                Switch(checked = wifiOnly, onCheckedChange = onWifiOnly)
            }
        }
    }
}

@Composable
private fun InstalledCard(model: InstalledModel, active: Boolean, onActivate: () -> Unit, onDelete: () -> Unit) {
    AppCard(onClick = onActivate, accent = if (active) Roozban.colors.completed.color else null) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = active, onClick = onActivate)
            Column(Modifier.weight(1f)) {
                Text(model.name, fontWeight = FontWeight.Bold)
                Text(
                    formatSize(model.sizeBytes) + if (model.source == ModelSource.IMPORTED) " · واردشده" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (active) StatusPill("فعال", Roozban.colors.completed)
            IconButton(onClick = onDelete) { Icon(painterResource(DsR.drawable.ic_delete), "حذف", tint = Roozban.colors.error.color) }
        }
    }
}

@Composable
internal fun CatalogCard(
    spec: ModelSpec,
    recommended: Boolean,
    download: DownloadState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDiscard: () -> Unit,
) {
    AppCard(accent = if (recommended) Roozban.colors.focus.color else null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(spec.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (recommended) StatusPill("پیشنهادی", Roozban.colors.focus)
            }
            Text(spec.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                "حجم: ${formatSize(spec.sizeBytes)} · مجوز: ${spec.license}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            when (download) {
                is DownloadState.Running -> {
                    LinearProgressIndicator(progress = { download.fraction }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatSize(download.downloaded)} از ${formatSize(download.total)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onCancel) { Text("توقف") }
                    }
                }
                is DownloadState.Failed -> {
                    if (download.message.isNotEmpty()) Text(download.message, color = Roozban.colors.error.color, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onDownload) { Text("ادامهٔ دانلود (${formatSize(download.partialBytes)} دریافت شده)") }
                        TextButton(onClick = onDiscard) { Text("حذف") }
                    }
                }
                null -> Button(onClick = onDownload) {
                    Icon(painterResource(DsR.drawable.ic_download), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("دانلود ${formatSize(spec.sizeBytes)}")
                }
            }
        }
    }
}
