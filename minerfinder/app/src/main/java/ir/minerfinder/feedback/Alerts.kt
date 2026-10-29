package ir.minerfinder.feedback

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.minerfinder.R
import ir.minerfinder.ui.MainActivity
import ir.minerfinder.ui.common.Fa
import ir.minerfinder.ui.common.PermissionAsker

/**
 * Notification channels and the threshold alert. An alert fires when the
 * probability crosses the threshold, then stays quiet until it has dropped
 * [REARM_MARGIN] points below it and at least [COOLDOWN_MS] has passed, so
 * hovering around the threshold does not ring the phone every second.
 */
class Alerts(private val context: Context) {
    private var armed = true
    private var lastAlertMs = 0L

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_SCAN, context.getString(R.string.channel_scan), NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, context.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
            },
        )
    }

    @Synchronized
    fun onProbability(probability: Float, threshold: Float, nowMs: Long) {
        if (probability < threshold - REARM_MARGIN) armed = true
        if (!armed || probability < threshold || nowMs - lastAlertMs < COOLDOWN_MS) return
        armed = false
        lastAlertMs = nowMs
        post(probability)
    }

    @Synchronized
    fun reset() {
        armed = true
    }

    private fun post(probability: Float) {
        if (Build.VERSION.SDK_INT >= 33 && !PermissionAsker.isGranted(context, Manifest.permission.POST_NOTIFICATIONS)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.alert_title))
            .setContentText(context.getString(R.string.alert_text, Fa.num(probability)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ALERT_ID, notification) }
    }

    companion object {
        const val CHANNEL_SCAN = "scan"
        const val CHANNEL_ALERT = "alert"
        const val ALERT_ID = 2
        const val REARM_MARGIN = 10f
        const val COOLDOWN_MS = 30_000L

        fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
