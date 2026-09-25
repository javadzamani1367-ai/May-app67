package ir.roozban.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.roozban.core.calendar.IranOccasions
import ir.roozban.core.calendar.PersianDateFormatter
import ir.roozban.core.calendar.PersianDigits
import ir.roozban.core.calendar.toJalali
import ir.roozban.core.domain.SettingsRepository
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import ir.roozban.core.designsystem.R as DsR

/**
 * The ongoing «today» notification: the day number as the status-bar icon, and in the shade the
 * app logo, the day number in large type, the Jalali date and, below it, the Gregorian and Hijri
 * dates (plus today's occasion). Refreshed at midnight, on boot and on clock changes.
 */
@Singleton
class DateNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) {
    private val manager = NotificationManagerCompat.from(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun refresh() {
        val s = settings.current()
        if (!s.dateNotification) {
            manager.cancel(NOTIFICATION_ID)
            cancelMidnight()
            return
        }
        scheduleMidnight()
        if (!notifier.canPost()) return
        val today = LocalDate.now(clock)
        val jalali = today.toJalali()
        val occasion = IranOccasions.on(today, s.hijriOffset).firstOrNull()
        val gregorian = PersianDateFormatter.gregorian(today)
        val hijri = PersianDateFormatter.hijri(today, s.hijriOffset)
        val other = listOfNotNull(gregorian, hijri).joinToString("  |  ")

        fun views(): RemoteViews = RemoteViews(context.packageName, R.layout.notification_date).apply {
            setImageViewResource(R.id.date_logo, context.applicationInfo.icon)
            setTextViewText(R.id.date_day, PersianDigits.format(jalali.day))
            setTextViewText(R.id.date_jalali, PersianDateFormatter.fullDate(jalali))
            setTextViewText(R.id.date_other, other)
            if (occasion != null) {
                setViewVisibility(R.id.date_occasion, View.VISIBLE)
                setTextViewText(R.id.date_occasion, occasion.title)
                setTextColor(R.id.date_occasion, ContextCompat.getColor(context, if (occasion.holiday) R.color.date_holiday else R.color.date_accent))
            }
            if (occasion?.holiday == true || today.dayOfWeek == java.time.DayOfWeek.FRIDAY) {
                setTextColor(R.id.date_day, ContextCompat.getColor(context, R.color.date_holiday))
            }
        }

        val notification = NotificationCompat.Builder(context, ReminderNotifier.CHANNEL_DATE)
            .setSmallIcon(dayIcon(jalali.day))
            .setContentTitle(PersianDateFormatter.fullDate(jalali))
            .setContentText(other)
            .setCustomContentView(views())
            .setCustomBigContentView(views())
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(AppLinks.open(context, AppLinks.CALENDAR))
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked.
        }
    }

    /** The day number drawn in white, used as the status-bar icon (the system tints it). */
    private fun dayIcon(day: Int): IconCompat {
        val size = 96
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val typeface = runCatching { ResourcesCompat.getFont(context, DsR.font.vazirmatn_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        val text = PersianDigits.format(day)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
            textSize = if (text.length > 1) 78f else 92f
        }
        val canvas = Canvas(bitmap)
        val bounds = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, size / 2f, size / 2f - bounds.exactCenterY(), paint)
        return IconCompat.createWithBitmap(bitmap)
    }

    private fun midnightIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RoutineReceiver::class.java).setAction(RoutineReceiver.ACTION_DATE_REFRESH),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleMidnight() {
        val at = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli() + 2_000
        val operation = midnightIntent(PendingIntent.FLAG_UPDATE_CURRENT)!!
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    private fun cancelMidnight() {
        midnightIntent(PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private companion object {
        const val NOTIFICATION_ID = 0x0DA7E
    }
}
