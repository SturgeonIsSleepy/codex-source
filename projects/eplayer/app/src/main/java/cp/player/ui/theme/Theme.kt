package cp.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import cp.player.R

val TravelHand = FontFamily(Font(R.font.travel_hand, weight = FontWeight.Medium))

// Shared with nested dialogs, sheets and cover wrappers: one palette throughout ePlayer.
val PaperColors = lightColorScheme(
    primary = Color(0xFF74538F), onPrimary = Color.White,
    primaryContainer = Color(0xFFEADFF4), onPrimaryContainer = Color(0xFF443052),
    secondary = Color(0xFF686275), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE3EE), onSecondaryContainer = Color(0xFF373040),
    tertiary = Color(0xFF8A697A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3DFE9), onTertiaryContainer = Color(0xFF513342),
    background = Color(0xFFF6F2F8), onBackground = Color(0xFF302A38),
    surface = Color(0xFFFBF8FD), onSurface = Color(0xFF302A38),
    surfaceVariant = Color(0xFFECE6F0), onSurfaceVariant = Color(0xFF686071),
    surfaceContainerLowest = Color(0xFFFFFCFF), surfaceContainerLow = Color(0xFFF0EAF5),
    surfaceContainer = Color(0xFFECE6F0), surfaceContainerHigh = Color(0xFFE7DEED),
    surfaceContainerHighest = Color(0xFFE0D7E7),
    outline = Color(0xFF81758C), outlineVariant = Color(0xFFD5CADA),
    error = Color(0xFFA4444F), errorContainer = Color(0xFFFFDADD)
)

val NightColors = darkColorScheme(
    primary = Color(0xFFD1B8EC), onPrimary = Color(0xFF382544),
    primaryContainer = Color(0xFF51405F), onPrimaryContainer = Color(0xFFF1E1FF),
    secondary = Color(0xFFD0C5DB), onSecondary = Color(0xFF332B3D),
    secondaryContainer = Color(0xFF4B4355), onSecondaryContainer = Color(0xFFF0E8F6),
    tertiary = Color(0xFFE4BDCF), onTertiary = Color(0xFF472C3A),
    tertiaryContainer = Color(0xFF604450), onTertiaryContainer = Color(0xFFFFDEE9),
    background = Color(0xFF292632), onBackground = Color(0xFFF6EFFB),
    surface = Color(0xFF34303E), onSurface = Color(0xFFF6EFFB),
    surfaceVariant = Color(0xFF484252), onSurfaceVariant = Color(0xFFD2C8DC),
    surfaceContainerLowest = Color(0xFF2D2937), surfaceContainerLow = Color(0xFF393341),
    surfaceContainer = Color(0xFF403A49), surfaceContainerHigh = Color(0xFF484150),
    surfaceContainerHighest = Color(0xFF514958),
    outline = Color(0xFFAB9CB8), outlineVariant = Color(0xFF60546B),
    error = Color(0xFFFFB4BD), onError = Color(0xFF650F23),
    errorContainer = Color(0xFF77303E), onErrorContainer = Color(0xFFFFDADD),
    inverseSurface = Color(0xFFEDE4F2), inverseOnSurface = Color(0xFF342D3D),
    inversePrimary = Color(0xFF74538F), surfaceTint = Color.Transparent
)

val LocalElainaTheme = staticCompositionLocalOf { false }

// MTZ accent #D59DC2 is retained in containers; text uses a darker AA accent in daylight.
val ElainaPaperColors = PaperColors.copy(
    primary = Color(0xFF893F70), onPrimary = Color.White,
    primaryContainer = Color(0xFFF4DDEA), onPrimaryContainer = Color(0xFF51233F),
    secondary = Color(0xFF625674), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE3F3), onSecondaryContainer = Color(0xFF382E48),
    tertiary = Color(0xFF445F8B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDFE9FB), onTertiaryContainer = Color(0xFF243958),
    background = Color(0xFFFAF5FA), onBackground = Color(0xFF302430),
    surface = Color(0xFFFFFAFD), onSurface = Color(0xFF302430),
    surfaceVariant = Color(0xFFF0E5EE), onSurfaceVariant = Color(0xFF655565),
    surfaceContainerLowest = Color(0xFFFFFCFF), surfaceContainerLow = Color(0xFFF8EDF4),
    surfaceContainer = Color(0xFFF2E7EF), surfaceContainerHigh = Color(0xFFEDDFEA),
    surfaceContainerHighest = Color(0xFFE5D5E2),
    outline = Color(0xFF827081), outlineVariant = Color(0xFFD7C4D2)
)
val ElainaNightColors = NightColors.copy(
    primary = Color(0xFFE8B4D1), onPrimary = Color(0xFF442239),
    primaryContainer = Color(0xFF604052), onPrimaryContainer = Color(0xFFFFE0F1),
    secondary = Color(0xFFD4C3EA), onSecondary = Color(0xFF332743),
    secondaryContainer = Color(0xFF4E405E), onSecondaryContainer = Color(0xFFF3E6FF),
    tertiary = Color(0xFFB5CCF2), onTertiary = Color(0xFF233A5B),
    tertiaryContainer = Color(0xFF3A4F70), onTertiaryContainer = Color(0xFFDEEAFF),
    background = Color(0xFF302B3A), onBackground = Color(0xFFF8EFF7),
    surface = Color(0xFF39313F), onSurface = Color(0xFFF8EFF7),
    surfaceVariant = Color(0xFF504352), onSurfaceVariant = Color(0xFFDECCDA),
    surfaceContainerLowest = Color(0xFF322B39), surfaceContainerLow = Color(0xFF3D3342),
    surfaceContainer = Color(0xFF443949), surfaceContainerHigh = Color(0xFF4D4051),
    surfaceContainerHighest = Color(0xFF57485A),
    outline = Color(0xFFB8A3B4), outlineVariant = Color(0xFF705F73)
)

@Composable
fun EPlayerTheme(darkTheme: Boolean = isSystemInDarkTheme(), elainaTheme: Boolean = false, content: @Composable () -> Unit) {
    val dark = darkTheme
    val colors = if (elainaTheme) { if (dark) ElainaNightColors else ElainaPaperColors } else { if (dark) NightColors else PaperColors }
    val base = Typography()
    val type = remember {
        Typography(
            displayLarge = base.displayLarge.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            displayMedium = base.displayMedium.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            displaySmall = base.displaySmall.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            headlineLarge = base.headlineLarge.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            headlineMedium = base.headlineMedium.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            headlineSmall = base.headlineSmall.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            titleLarge = base.titleLarge.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            titleMedium = base.titleMedium.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            titleSmall = base.titleSmall.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            bodyLarge = base.bodyLarge.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            bodyMedium = base.bodyMedium.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            bodySmall = base.bodySmall.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            labelLarge = base.labelLarge.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            labelMedium = base.labelMedium.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium),
            labelSmall = base.labelSmall.copy(fontFamily = TravelHand, fontWeight = FontWeight.Medium)
        )
    }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = type,
        shapes = Shapes(RoundedCornerShape(3.dp), RoundedCornerShape(5.dp),
            RoundedCornerShape(9.dp), RoundedCornerShape(12.dp), RoundedCornerShape(16.dp)),
        content = {
            // Dialogs and layouts that draw their own background still need themed ink.
            CompositionLocalProvider(LocalContentColor provides colors.onBackground, LocalElainaTheme provides elainaTheme, content = content)
        }
    )
}
