package ir.roozban.ai.runtime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import ir.roozban.ai.models.DownloadException
import ir.roozban.ai.models.ModelCatalog
import ir.roozban.ai.models.ModelDownloader
import ir.roozban.ai.models.ModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Downloads one model in the foreground with a progress notification. Retries network errors
 * a few times; a later start resumes from the partial file.
 */
@AndroidEntryPoint
class ModelDownloadService : Service() {
    @Inject
    lateinit var manager: ModelManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var current: ModelSpec? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            stop()
            return START_NOT_STICKY
        }
        val spec = intent?.getStringExtra(EXTRA_ID)?.let(ModelCatalog::get)
        if (spec == null || job?.isActive == true) {
            if (job?.isActive != true) stop()
            return START_NOT_STICKY
        }
        current = spec
        ensureChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, progress(spec, 0, spec.sizeBytes),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        job = scope.launch { run(spec) }
        return START_NOT_STICKY
    }

    private suspend fun run(spec: ModelSpec) {
        val nm = getSystemService(NotificationManager::class.java)
        var error: String? = null
        if (manager.freeSpaceBytes() + manager.store.partialBytes(spec) < spec.sizeBytes + ModelManager.SPACE_MARGIN) {
            error = "فضای کافی روی گوشی نیست."
        } else if (!manager.networkAllowed()) {
            error = if (manager.state.value.wifiOnly) "به وای‌فای وصل نیستی." else "اینترنت در دسترس نیست."
        } else {
            var attempt = 0
            while (true) {
                try {
                    var lastPercent = -1
                    ModelDownloader().download(spec.url, manager.store.fileFor(spec), spec.sizeBytes, spec.sha256) { p ->
                        manager.onProgress(spec, p.downloadedBytes, p.totalBytes)
                        val percent = (p.fraction * 100).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            nm.notify(NOTIFICATION_ID, progress(spec, p.downloadedBytes, p.totalBytes))
                        }
                    }
                    error = null
                    break
                } catch (e: CancellationException) {
                    manager.onFinished(spec, "متوقف شد")
                    throw e
                } catch (e: DownloadException) {
                    error = if (e.message == "checksum mismatch") "فایل دانلودشده سالم نبود؛ دوباره تلاش کن." else "دانلود قطع شد."
                    if (!e.retryable || ++attempt >= MAX_ATTEMPTS || !manager.networkAllowed()) break
                    delay(RETRY_DELAY_MILLIS * attempt)
                } catch (e: Exception) {
                    error = "دانلود قطع شد."
                    if (++attempt >= MAX_ATTEMPTS || !manager.networkAllowed()) break
                    delay(RETRY_DELAY_MILLIS * attempt)
                }
            }
        }
        manager.onFinished(spec, error)
        nm.notify(
            RESULT_ID,
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_download)
                .setContentTitle(getString(if (error == null) R.string.ai_download_done else R.string.ai_download_failed, spec.name))
                .setContentText(error)
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build(),
        )
        stop()
    }

    private fun progress(spec: ModelSpec, done: Long, total: Long) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_download)
        .setContentTitle(getString(R.string.ai_download_title, spec.name))
        .setContentText("${done / MB} / ${total / MB} MB")
        .setProgress(100, if (total > 0) (done * 100 / total).toInt() else 0, total <= 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openApp())
        .addAction(0, getString(R.string.ai_download_cancel), PendingIntent.getService(this, 1, cancelIntent(this), PendingIntent.FLAG_IMMUTABLE))
        .build()

    private fun openApp(): PendingIntent? = packageManager.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.ai_channel_downloads), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "model_downloads"
        private const val NOTIFICATION_ID = 7301
        private const val RESULT_ID = 7302
        private const val EXTRA_ID = "model_id"
        private const val ACTION_CANCEL = "ir.roozban.ai.CANCEL_DOWNLOAD"
        private const val MAX_ATTEMPTS = 4
        private const val RETRY_DELAY_MILLIS = 5_000L
        private const val MB = 1024L * 1024

        fun startIntent(context: Context, id: String) = Intent(context, ModelDownloadService::class.java).putExtra(EXTRA_ID, id)

        fun cancelIntent(context: Context) = Intent(context, ModelDownloadService::class.java).setAction(ACTION_CANCEL)
    }
}
