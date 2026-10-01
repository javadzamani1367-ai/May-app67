package ir.ilam.inspection.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** `LocalContext.current` is usually a theme wrapper rather than the activity. */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
