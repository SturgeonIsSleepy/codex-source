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
    primary = Color(0xFF245F7D), onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EFF9), onPrimaryContainer = Color(0xFF183D52),
    secondary = Color(0xFF526674), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5EEF2), onSecondaryContainer = Color(0xFF293F4C),
    tertiary = Color(0xFF486E76), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDEFF1), onTertiaryContainer = Color(0xFF21474D),
    background = Color.White, onBackground = Color(0xFF252D33),
    surface = Color(0xFFFCFDFE), onSurface = Color(0xFF252D33),
    surfaceVariant = Color(0xFFE7EFF3), onSurfaceVariant = Color(0xFF50616D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F7FA),
    surfaceContainer = Color(0xFFEAF2F6), surfaceContainerHigh = Color(0xFFE3EDF2),
    surfaceContainerHighest = Color(0xFFDBE7EE),
    outline = Color(0xFF718793), outlineVariant = Color(0xFFCCDCE5),
    error = Color(0xFFA4444F), errorContainer = Color(0xFFFFDADD)
)

val NightColors = darkColorScheme(
    primary = Color(0xFFA9D7ED), onPrimary = Color(0xFF173D52),
    primaryContainer = Color(0xFF294B60), onPrimaryContainer = Color(0xFFD8EFFB),
    secondary = Color(0xFFBCCED9), onSecondary = Color(0xFF253A48),
    secondaryContainer = Color(0xFF344B5A), onSecondaryContainer = Color(0xFFE1EDF5),
    tertiary = Color(0xFFB2D6DA), onTertiary = Color(0xFF204248),
    tertiaryContainer = Color(0xFF34555B), onTertiaryContainer = Color(0xFFD5F0F3),
    background = Color(0xFF17212A), onBackground = Color(0xFFEDF3F8),
    surface = Color(0xFF1E2B35), onSurface = Color(0xFFEDF3F8),
    surfaceVariant = Color(0xFF334550), onSurfaceVariant = Color(0xFFC4D4DF),
    surfaceContainerLowest = Color(0xFF141E26), surfaceContainerLow = Color(0xFF22313C),
    surfaceContainer = Color(0xFF293B47), surfaceContainerHigh = Color(0xFF304450),
    surfaceContainerHighest = Color(0xFF394F5C),
    outline = Color(0xFF97B2C2), outlineVariant = Color(0xFF4E6573),
    error = Color(0xFFFFB4BD), onError = Color(0xFF650F23),
    errorContainer = Color(0xFF77303E), onErrorContainer = Color(0xFFFFDADD),
    inverseSurface = Color(0xFFE9F2F8), inverseOnSurface = Color(0xFF263A48),
    inversePrimary = Color(0xFF245F7D), surfaceTint = Color.Transparent
)

@Composable
fun EPlayerTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val dark = darkTheme
    val colors = if (dark) NightColors else PaperColors
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
            CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
        }
    )
}
