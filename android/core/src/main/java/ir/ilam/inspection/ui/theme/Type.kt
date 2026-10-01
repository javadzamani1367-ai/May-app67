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
 * Vazirmatn in every weight that is bundled, the platform font otherwise, so
 * the project always builds even before the ttf files are dropped into assets.
 *
 * A missing optional weight is not an error: Compose falls back to the
 * nearest weight that is there, so ExtraBold numbers become Bold ones.
 */
object AppFontFamilies {

    fun load(context: Context): FontFamily {
        if (!AppFonts.isBundled(context)) return FontFamily.Default
        return runCatching {
            val fonts = buildList {
                add(Font(AppFonts.ASSET_PATH, context.assets, weight = FontWeight.Normal))
                add(Font(AppFonts.BOLD_ASSET_PATH, context.assets, weight = FontWeight.Bold))
                if (AppFonts.has(context, AppFonts.MEDIUM_ASSET_PATH)) {
                    add(Font(AppFonts.MEDIUM_ASSET_PATH, context.assets, weight = FontWeight.Medium))
                }
                if (AppFonts.has(context, AppFonts.EXTRA_BOLD_ASSET_PATH)) {
                    add(Font(AppFonts.EXTRA_BOLD_ASSET_PATH, context.assets, weight = FontWeight.ExtraBold))
                }
            }
            FontFamily(fonts)
        }.getOrElse { FontFamily.Default }
    }
}

/**
 * A formal, legible scale, larger than Material's defaults: the target is a
 * gloved thumb in sunlight.
 *
 * Hierarchy comes from weight as much as size. Figures that answer a question
 * at a glance — a count, a tracking code, a wattage — are ExtraBold; titles
 * are Bold; labels are Medium; body text is Regular. A screen of one size and
 * one weight is a screen nobody can scan.
 */
fun appTypography(family: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.f(size: Int, weight: FontWeight, line: Double = 1.55, tracking: Double = 0.0) =
        copy(
            fontFamily = family,
            fontSize = size.sp,
            lineHeight = (size * line).sp,
            fontWeight = weight,
            letterSpacing = tracking.sp
        )
    return Typography(
        displayLarge = base.displayLarge.f(40, FontWeight.ExtraBold, 1.2),
        displayMedium = base.displayMedium.f(34, FontWeight.ExtraBold, 1.2),
        displaySmall = base.displaySmall.f(28, FontWeight.ExtraBold, 1.25),
        headlineLarge = base.headlineLarge.f(26, FontWeight.Bold, 1.35),
        headlineMedium = base.headlineMedium.f(23, FontWeight.Bold, 1.4),
        headlineSmall = base.headlineSmall.f(20, FontWeight.Bold, 1.4),
        titleLarge = base.titleLarge.f(19, FontWeight.Bold, 1.45),
        titleMedium = base.titleMedium.f(17, FontWeight.Bold, 1.5),
        titleSmall = base.titleSmall.f(15, FontWeight.Bold, 1.5),
        bodyLarge = base.bodyLarge.f(17, FontWeight.Normal, 1.65),
        bodyMedium = base.bodyMedium.f(15, FontWeight.Normal, 1.65),
        bodySmall = base.bodySmall.f(13, FontWeight.Normal, 1.6),
        labelLarge = base.labelLarge.f(16, FontWeight.Bold, 1.4),
        labelMedium = base.labelMedium.f(14, FontWeight.Medium, 1.4),
        labelSmall = base.labelSmall.f(12, FontWeight.Medium, 1.4, 0.2)
    )
}
