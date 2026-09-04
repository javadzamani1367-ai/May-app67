package ir.ilam.inspection.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Field phones are not attached to a debugger. When the app dies, the stack
 * trace is written next to the data so the next launch can show it and the
 * expert can send it on; without this a crash in the field is just "it closed".
 */
class CrashReporter(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val previous: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, error: Throwable) {
        runCatching { reportFile().writeText(describe(thread, error)) }
        previous?.uncaughtException(thread, error)
    }

    /** The report left by the previous run, or null when it exited cleanly. */
    fun pending(): String? = runCatching {
        val file = reportFile()
        if (file.exists()) file.readText() else null
    }.getOrNull()

    fun clear() {
        runCatching { reportFile().delete() }
    }

    private fun describe(thread: Thread, error: Throwable): String {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        return buildString {
            appendLine("app: " + versionName())
            appendLine("device: " + Build.MANUFACTURER + " " + Build.MODEL)
            appendLine("android: " + Build.VERSION.RELEASE + " (sdk " + Build.VERSION.SDK_INT + ")")
            appendLine("thread: " + thread.name)
            appendLine("time: " + PersianDate.formatWithTime(System.currentTimeMillis()))
            appendLine()
            append(stack.toString())
        }
    }

    private fun versionName(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName + " (" + info.longVersionCode() + ")"
    }.getOrDefault("unknown")

    @Suppress("DEPRECATION")
    private fun android.content.pm.PackageInfo.longVersionCode(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()

    private fun reportFile(): File = File(context.filesDir, FILE_NAME)

    private companion object {
        const val FILE_NAME = "last-crash.txt"
    }
}
