package ir.roozban.feature.tools.pdf

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.designsystem.R as DsR
import ir.roozban.core.designsystem.components.AppCard
import ir.roozban.core.designsystem.components.RoozbanTopBar
import ir.roozban.core.designsystem.theme.Roozban
import javax.inject.Inject

@HiltViewModel
class PdfViewModel @Inject constructor(val jobs: PdfJobs) : ViewModel()

/** «تبدیل PDF به ورد»: any number of pages; scanned pages are read with OCR. */
@Composable
internal fun PdfScreen(onOpenOcrData: () -> Unit, onBack: () -> Unit, viewModel: PdfViewModel = hiltViewModel()) {
    val job by viewModel.jobs.current.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf<String?>(null) }
    var inputName by rememberSaveable { mutableStateOf("") }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { out ->
        val src = input
        if (out != null && src != null) viewModel.jobs.start(context, Uri.parse(src), out, inputName)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            input = uri.toString()
            inputName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "document.pdf"
            save.launch(inputName.substringBeforeLast('.') + ".docx")
        }
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            RoozbanTopBar(
                title = { Text("تبدیل PDF به ورد") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(DsR.drawable.ic_arrow_back), "بازگشت") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "فایل PDF را انتخاب کن و جای ذخیرهٔ فایل ورد را بگو. متن فارسی با ترتیب درست، پاراگراف‌ها، عنوان‌ها و درشتی نوشته‌ها منتقل می‌شود؛ محدودیت تعداد صفحه ندارد. صفحه‌های اسکن‌شده (عکس) با «تبدیل تصویر به متن» خوانده می‌شوند. همه‌چیز روی خود گوشی.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val j = job) {
                is PdfJob.Running -> AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(j.name, fontWeight = FontWeight.SemiBold)
                        val p = j.progress
                        if (p == null) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("در حال آماده‌سازی…")
                        } else {
                            LinearProgressIndicator(progress = { p.page / p.pages.toFloat() }, modifier = Modifier.fillMaxWidth())
                            Text("صفحهٔ ${PersianDigits.format(p.page)} از ${PersianDigits.format(p.pages)}")
                        }
                        Text("می‌توانی از این صفحه بیرون بروی؛ تبدیل ادامه دارد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { viewModel.jobs.cancel(context) }) { Text("لغو") }
                    }
                }
                is PdfJob.Done -> AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("آماده شد: ${PersianDigits.format(j.pages)} صفحه", fontWeight = FontWeight.Bold, color = Roozban.colors.success.color)
                        if (j.scannedPages > 0 && !j.ocrMissing) Text("${PersianDigits.format(j.scannedPages)} صفحهٔ اسکن‌شده با تشخیص متن خوانده شد.")
                        if (j.ocrMissing) {
                            Text("${PersianDigits.format(j.scannedPages)} صفحه اسکن‌شده بود و خالی ماند؛ برای خواندن آن‌ها داده‌های «تبدیل تصویر به متن» را دانلود کن و دوباره تبدیل کن.", color = Roozban.colors.warning.color)
                            OutlinedButton(onClick = onOpenOcrData) { Text("تبدیل تصویر به متن") }
                        }
                        Button(onClick = {
                            val view = Intent(Intent.ACTION_VIEW).setDataAndType(j.output, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            runCatching { context.startActivity(Intent.createChooser(view, null)) }
                        }) { Text("باز کردن فایل") }
                        OutlinedButton(onClick = { viewModel.jobs.reset(); open.launch(arrayOf("application/pdf")) }) { Text("فایل دیگر") }
                    }
                }
                is PdfJob.Failed -> {
                    Text(j.message, color = Roozban.colors.error.color)
                    Button(onClick = { viewModel.jobs.reset(); open.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب فایل PDF") }
                }
                PdfJob.Idle -> Button(onClick = { open.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) { Text("انتخاب فایل PDF") }
            }
        }
    }
}
