package ir.roozban.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import ir.roozban.core.designsystem.R

/** Vazirmatn (SIL OFL 1.1) — see VAZIRMATN_OFL.txt. */
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun TextStyle.persian(weight: FontWeight? = null): TextStyle =
    copy(fontFamily = Vazirmatn, fontWeight = weight ?: fontWeight)

internal val RoozbanTypography: Typography = Typography().run {
    copy(
        displayLarge = displayLarge.persian(FontWeight.Bold),
        displayMedium = displayMedium.persian(FontWeight.Bold),
        displaySmall = displaySmall.persian(FontWeight.Bold),
        headlineLarge = headlineLarge.persian(FontWeight.SemiBold),
        headlineMedium = headlineMedium.persian(FontWeight.SemiBold),
        headlineSmall = headlineSmall.persian(FontWeight.SemiBold),
        titleLarge = titleLarge.persian(FontWeight.SemiBold),
        titleMedium = titleMedium.persian(FontWeight.Medium),
        titleSmall = titleSmall.persian(FontWeight.Medium),
        bodyLarge = bodyLarge.persian(),
        bodyMedium = bodyMedium.persian(),
        bodySmall = bodySmall.persian(),
        labelLarge = labelLarge.persian(FontWeight.Medium),
        labelMedium = labelMedium.persian(FontWeight.Medium),
        labelSmall = labelSmall.persian(FontWeight.Medium),
    )
}
