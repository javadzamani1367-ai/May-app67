package ir.roozban.app.entry

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import ir.roozban.app.R

/**
 * Launcher shortcut «کار جدید». Dynamic rather than static so it works for every build flavor
 * (static shortcuts need a fixed package name).
 */
object Shortcuts {
    fun publish(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, "new_task")
            .setShortLabel(context.getString(R.string.shortcut_new_task))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_add))
            .setIntent(Intent(context, QuickAddActivity::class.java).setAction(Intent.ACTION_VIEW))
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}
