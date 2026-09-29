package ir.roozban.feature.tools.pdf

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PdfJob {
    data object Idle : PdfJob
    data class Running(val name: String, val progress: PdfProgress?) : PdfJob
    data class Done(val name: String, val output: Uri, val pages: Int, val scannedPages: Int, val ocrMissing: Boolean) : PdfJob
    data class Failed(val message: String) : PdfJob
}

/** The conversion in progress (one at a time), shared by the service and the screen. */
@Singleton
class PdfJobs @Inject constructor() {
    val state = MutableStateFlow<PdfJob>(PdfJob.Idle)
    val current: StateFlow<PdfJob> = state.asStateFlow()

    fun start(context: Context, input: Uri, output: Uri, name: String) {
        state.value = PdfJob.Running(name, null)
        ContextCompat.startForegroundService(
            context,
            Intent(context, PdfConvertService::class.java).setData(input).putExtra(EXTRA_OUTPUT, output).putExtra(EXTRA_NAME, name),
        )
    }

    fun cancel(context: Context) = context.startService(Intent(context, PdfConvertService::class.java).setAction(ACTION_CANCEL))

    fun reset() {
        if (state.value !is PdfJob.Running) state.value = PdfJob.Idle
    }

    internal companion object {
        const val EXTRA_OUTPUT = "output"
        const val EXTRA_NAME = "name"
        const val ACTION_CANCEL = "cancel"
    }
}

/** Runs a PDF → Word conversion in the foreground with a progress notification. */
@AndroidEntryPoint
class PdfConvertService : Service() {
    @Inject lateinit var converter: PdfConverter
    @Inject lateinit var jobs: PdfJobs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == PdfJobs.ACTION_CANCEL) {
            job?.cancel()
            return START_NOT_STICKY
        }
        val input = intent?.data
        val output = intent?.let { androidx.core.content.IntentCompat.getParcelableExtra(it, PdfJobs.EXTRA_OUTPUT, Uri::class.java) }
        val name = intent?.getStringExtra(PdfJobs.EXTRA_NAME).orEmpty()
        if (input == null || output == null || job?.isActive == true) {
            if (job?.isActive != true) stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification(name, null),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        job = scope.launch {
            try {
                val ocrMissing = !converterHasOcr()
                val result = converter.convert(input, output) { p ->
                    jobs.state.value = PdfJob.Running(name, p)
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(name, p))
                }
                jobs.state.value = PdfJob.Done(name, output, result.pages, result.scannedPages, ocrMissing && result.scannedPages > 0)
            } catch (e: CancellationException) {
                jobs.state.value = PdfJob.Failed("لغو شد.")
                contentResolver.delete(output, null, null)
            } catch (e: Throwable) {
                jobs.state.value = PdfJob.Failed(
                    when {
                        e is OutOfMemoryError -> "حافظهٔ گوشی برای این فایل کافی نبود."
                        e.message?.contains("password", ignoreCase = true) == true -> "این PDF رمز دارد."
                        else -> e.message ?: "تبدیل نشد."
                    },
                )
            } finally {
                ServiceCompat.stopForeground(this@PdfConvertService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun converterHasOcr(): Boolean = runCatching { converter.hasOcr() }.getOrDefault(false)

    override fun onDestroy() {
        scope.cancel()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "تبدیل فایل", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun notification(name: String, p: PdfProgress?) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("تبدیل PDF به ورد")
        .setContentText(if (p == null) name else "$name — صفحهٔ ${p.page} از ${p.pages}")
        .setProgress(p?.pages ?: 0, p?.page ?: 0, p == null)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .build()

    private companion object {
        const val CHANNEL = "document_convert"
        const val NOTIFICATION_ID = 0x0D0C
    }
}
