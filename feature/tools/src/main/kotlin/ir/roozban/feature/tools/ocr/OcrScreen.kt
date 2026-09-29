package ir.roozban.feature.tools.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.theme.Roozban
import java.io.File

/** «تبدیل تصویر به متن»: a photo or a picture from the gallery → editable Persian text. */
@Composable
internal fun OcrScreen(onOpenNote: (String) -> Unit, onBack: () -> Unit, viewModel: OcrViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val data by viewModel.data.collectAsStateWithLifecycle()
    val reading by viewModel.reading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(viewModel::read) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photoUri?.let { viewModel.read(Uri.parse(it)) }
    }
    fun takePhoto() {
        val dir = File(context.cacheDir, "ocr").apply { mkdirs() }
        val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".tools.files", file)
        photoUri = uri.toString()
        camera.launch(uri)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text("تبدیل تصویر به متن") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), "بازگشت") } },
                actions = {
                    if (state.text.isNotBlank()) {
                        IconButton(onClick = viewModel::toggleReading) {
                            Icon(painterResource(if (reading) DsR.drawable.ic_stop else DsR.drawable.ic_play), if (reading) "توقف" else "خواندن متن")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val d = data) {
                OcrData.Ready -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = ::takePhoto, enabled = !state.working, modifier = Modifier.weight(1f)) { Text("عکس بگیر") }
                        OutlinedButton(
                            onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = !state.working,
                            modifier = Modifier.weight(1f),
                        ) { Text("از گالری") }
                    }
                    Text(
                        "برای بهترین نتیجه: صفحه صاف و پرنور باشد و کل متن در کادر. هر تصویر تازه به انتهای متن اضافه می‌شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("برای خواندن متن فارسی، یک بار داده‌های زبان را دانلود کن (حدود ${PersianDigits.toPersian("%.1f".format(viewModel.downloadSizeBytes / 1_000_000.0)).replace('.', '٫')} مگابایت). بعد از آن همه‌چیز روی خود گوشی انجام می‌شود.")
                        when (d) {
                            is OcrData.Downloading -> LinearProgressIndicator(progress = { d.fraction }, modifier = Modifier.fillMaxWidth())
                            is OcrData.Failed -> {
                                Text(d.message.ifBlank { "دانلود نشد." }, color = Roozban.colors.error.color)
                                Button(onClick = viewModel::download) { Text("دوباره") }
                            }
                            else -> Button(onClick = viewModel::download) { Text("دانلود") }
                        }
                    }
                }
            }
            if (state.working) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = Roozban.colors.error.color) }
            if (state.text.isNotBlank() || state.working) {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = viewModel::setText,
                    label = { Text("متن (قابل ویرایش)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 6,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("text", state.text))
                    }, modifier = Modifier.weight(1f)) { Text("کپی") }
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, state.text)
                        context.startActivity(Intent.createChooser(send, null))
                    }, modifier = Modifier.weight(1f)) { Text("اشتراک") }
                    OutlinedButton(onClick = viewModel::saveAsNote, modifier = Modifier.weight(1f)) { Text("یادداشت") }
                }
                state.savedNoteId?.let { id ->
                    OutlinedButton(onClick = { onOpenNote(id) }, modifier = Modifier.fillMaxWidth()) { Text("در یادداشت‌ها ذخیره شد؛ باز کن") }
                }
                OutlinedButton(onClick = viewModel::clear, modifier = Modifier.fillMaxWidth()) { Text("پاک کردن و شروع دوباره") }
            }
        }
    }
}
