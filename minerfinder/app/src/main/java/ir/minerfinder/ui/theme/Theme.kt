package ir.minerfinder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

object Palette {
    val Navy = Color(0xFF0E2A47)
    val Teal = Color(0xFF13A89E)
    val Cold = Color(0xFF2F6FDE)
    val Warm = Color(0xFFF2A516)
    val Hot = Color(0xFFE5392B)
    val Ok = Color(0xFF2E9E5B)
    val Muted = Color(0xFF6B7785)
    val Surface = Color(0xFFFFFFFF)
    val Background = Color(0xFFF4F6F9)
}

private val colors = lightColorScheme(
    primary = Palette.Teal,
    onPrimary = Color.White,
    secondary = Palette.Navy,
    onSecondary = Color.White,
    background = Palette.Background,
    surface = Palette.Surface,
    error = Palette.Hot,
)

private val type = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

/** Light, high-contrast and right-to-left everywhere, whatever the phone's language. */
@Composable
fun MinerFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = type) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
