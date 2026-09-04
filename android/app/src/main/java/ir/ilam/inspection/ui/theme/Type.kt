package ir.ilam.inspection.ui.theme

import android.content.Context
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ir.ilam.inspection.util.AppFonts

/**
 * Vazirmatn when the font is bundled, the platform font otherwise, so the
 * project always builds even before the ttf is dropped into assets.
 */
fun vazirFamily(context: Context): FontFamily {
    if (!AppFonts.isBundled(context)) return FontFamily.Default
    return runCatching {
        FontFamily(
            Font(AppFonts.ASSET_PATH, context.assets, weight = FontWeight.Normal),
            Font(AppFonts.BOLD_ASSET_PATH, context.assets, weight = FontWeight.Bold)
        )
    }.getOrElse { FontFamily.Default }
}

/** Larger than Material defaults: the target is a gloved thumb in sunlight. */
fun appTypography(family: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.f(size: Int, weight: FontWeight = FontWeight.Normal) =
        copy(fontFamily = family, fontSize = size.sp, lineHeight = (size * 1.6).sp, fontWeight = weight)
    return Typography(
        displaySmall = base.displaySmall.f(30, FontWeight.Bold),
        headlineMedium = base.headlineMedium.f(24, FontWeight.Bold),
        headlineSmall = base.headlineSmall.f(21, FontWeight.Bold),
        titleLarge = base.titleLarge.f(20, FontWeight.Bold),
        titleMedium = base.titleMedium.f(18, FontWeight.Bold),
        titleSmall = base.titleSmall.f(16, FontWeight.Bold),
        bodyLarge = base.bodyLarge.f(17),
        bodyMedium = base.bodyMedium.f(16),
        bodySmall = base.bodySmall.f(14),
        labelLarge = base.labelLarge.f(16, FontWeight.Bold),
        labelMedium = base.labelMedium.f(14),
        labelSmall = base.labelSmall.f(13)
    )
}
