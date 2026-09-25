package ir.roozban.core.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Screens a notification can open; MainActivity reads [EXTRA_OPEN] and navigates. */
object AppLinks {
    const val EXTRA_OPEN = "ir.roozban.extra.OPEN"
    const val FOCUS = "focus"
    const val HABITS = "habits"
    const val DAILY_REVIEW = "review_daily"
    const val WEEKLY_REVIEW = "review_weekly"
    const val CALENDAR = "calendar"
    const val PLANNER = "planner"

    fun open(context: Context, target: String): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            it.putExtra(EXTRA_OPEN, target).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(context, target.hashCode(), it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
}
