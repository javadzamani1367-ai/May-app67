package ir.ilam.inspection.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Surface,
    primaryContainer = Teal100,
    onPrimaryContainer = Teal900,
    secondary = Teal900,
    onSecondary = Surface,
    tertiary = Amber700,
    onTertiary = Surface,
    error = Red700,
    onError = Surface,
    errorContainer = Red100,
    onErrorContainer = Red700,
    background = Grey50,
    onBackground = Grey900,
    surface = Surface,
    onSurface = Grey900,
    surfaceVariant = Grey200,
    onSurfaceVariant = Grey700,
    outline = Grey700
)

/**
 * The whole app is laid out right to left; individual screens never have to
 * think about direction.
 */
@Composable
fun InspectionTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val typography = remember(context) { appTypography(vazirFamily(context)) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = LightColors,
            typography = typography,
            content = content
        )
    }
}
