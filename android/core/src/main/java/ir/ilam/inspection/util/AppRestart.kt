package ir.ilam.inspection.util

import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * Starts the app again from scratch. Used after a sign-in switches accounts:
 * every repository holds the previous account's open database, and a fresh
 * process is the one way to be sure none of them is left pointing at it.
 */
object AppRestart {
    fun restart(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        exitProcess(0)
    }
}
