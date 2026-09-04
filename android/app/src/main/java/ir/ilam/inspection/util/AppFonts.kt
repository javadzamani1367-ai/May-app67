package ir.ilam.inspection.util

import android.content.Context
import android.graphics.Typeface
import android.util.Base64
import java.io.File

/**
 * Vazirmatn is loaded from `assets/fonts` rather than a font resource so the
 * project builds with or without the binary present, and so the same bytes can
 * be inlined into the HTML that becomes a PDF.
 */
object AppFonts {

    const val ASSET_PATH = "fonts/Vazirmatn-Regular.ttf"
    const val BOLD_ASSET_PATH = "fonts/Vazirmatn-Bold.ttf"

    @Volatile
    private var cachedTypeface: Typeface? = null

    @Volatile
    private var cachedBase64: String? = null

    fun isBundled(context: Context): Boolean = context.assets.exists(ASSET_PATH)

    /** Null when the font has not been dropped into assets; callers fall back. */
    fun typeface(context: Context): Typeface? {
        cachedTypeface?.let { return it }
        if (!isBundled(context)) return null
        return runCatching { Typeface.createFromAsset(context.assets, ASSET_PATH) }
            .getOrNull()
            ?.also { cachedTypeface = it }
    }

    /**
     * The font as a `data:` payload for `@font-face`. Embedding it means the
     * print pipeline never depends on a font being installed on the device.
     */
    fun base64(context: Context): String? {
        cachedBase64?.let { return it }
        if (!isBundled(context)) return null
        return runCatching {
            context.assets.open(ASSET_PATH).use { input ->
                Base64.encodeToString(input.readBytes(), Base64.NO_WRAP)
            }
        }.getOrNull()?.also { cachedBase64 = it }
    }

    private fun android.content.res.AssetManager.exists(path: String): Boolean =
        runCatching { open(path).close(); true }.getOrDefault(false)

    /** Used by the build documentation to explain where the file belongs. */
    fun expectedLocation(context: Context): String =
        File(context.applicationInfo.sourceDir).name + "/assets/" + ASSET_PATH
}
