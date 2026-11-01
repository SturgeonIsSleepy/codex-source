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

// Fixed neutral surfaces; no wallpaper-derived or dynamic Material You palette.
val PaperColors = lightColorScheme(
    primary = Color(0xFF252525), onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E6), onPrimaryContainer = Color(0xFF171717),
    secondary = Color(0xFF505050), secondaryContainer = Color(0xFFECECEC), onSecondaryContainer = Color(0xFF202020),
    tertiary = Color(0xFF8B1728), onTertiary = Color.White,
    background = Color(0xFFFAFAFA), onBackground = Color(0xFF161616),
    surface = Color(0xFFFAFAFA), onSurface = Color(0xFF161616),
    surfaceVariant = Color(0xFFE9E9E9), onSurfaceVariant = Color(0xFF555555),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F2F2),
    surfaceContainer = Color(0xFFEDEDED), surfaceContainerHigh = Color(0xFFE5E5E5), surfaceContainerHighest = Color(0xFFDADADA),
    outline = Color(0xFF707070), outlineVariant = Color(0xFFCDCDCD), surfaceTint = Color.Transparent
)
val NightColors = darkColorScheme(
    primary = Color(0xFFF5F5F5), onPrimary = Color(0xFF181818),
    primaryContainer = Color(0xFF353535), onPrimaryContainer = Color.White,
    secondary = Color(0xFFD0D0D0), secondaryContainer = Color(0xFF303030), onSecondaryContainer = Color.White,
    tertiary = Color(0xFFFFB3BF), onTertiary = Color(0xFF4D1420),
    background = Color(0xFF101010), onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF141414), onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF303030), onSurfaceVariant = Color(0xFFC2C2C2),
    surfaceContainerLowest = Color(0xFF101010), surfaceContainerLow = Color(0xFF1B1B1B),
    surfaceContainer = Color(0xFF242424), surfaceContainerHigh = Color(0xFF303030), surfaceContainerHighest = Color(0xFF393939),
    outline = Color(0xFFAAAAAA), outlineVariant = Color(0xFF484848), surfaceTint = Color.Transparent
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
