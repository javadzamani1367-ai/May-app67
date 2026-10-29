package ir.minerfinder.service

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import ir.minerfinder.R
import ir.minerfinder.feedback.Alerts
import ir.minerfinder.graph
import ir.minerfinder.ui.common.Fa
import ir.minerfinder.ui.common.PermissionAsker
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Keeps a running scan alive with the screen off. The scan itself lives in
 * the ScanController; this service only holds the process in the foreground
 * (microphone, and location when granted) and shows the live probability.
 */
class ScanService : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            graph.scan.stop()
            return START_NOT_STICKY
        }
        val types = foregroundTypes(this)
        if (types == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(0f), types)
        lifecycleScope.launch {
            graph.scan.hub.detection.map { it.probability.toInt() }.distinctUntilChanged().collectLatest { p ->
                if (PermissionAsker.isGranted(this@ScanService, "android.permission.POST_NOTIFICATIONS") || Build.VERSION.SDK_INT < 33) {
                    runCatching { NotificationManagerCompat.from(this@ScanService).notify(NOTIFICATION_ID, notification(p.toFloat())) }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(probability: Float): Notification {
        val stop = android.app.PendingIntent.getService(
            this, 1, Intent(this, ScanService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Alerts.CHANNEL_SCAN)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.scan_notification_title))
            .setContentText(getString(R.string.scan_notification_text, Fa.num(probability)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(Alerts.openApp(this))
            .addAction(0, getString(R.string.scan_stop), stop)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "ir.minerfinder.STOP"

        /** Service types the granted permissions allow, or null when a foreground service cannot start. */
        fun foregroundTypes(context: Context): Int? {
            var types = 0
            if (PermissionAsker.isGranted(context, Manifest.permission.RECORD_AUDIO)) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (PermissionAsker.isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }
            return if (types == 0) null else types
        }

        fun start(context: Context) {
            if (foregroundTypes(context) == null) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, ScanService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScanService::class.java))
        }
    }
}
