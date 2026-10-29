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
    primary = Color(0xFFFA233B), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEBEE), onPrimaryContainer = Color(0xFFB41D35),
    secondary = Color(0xFF695448), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E8DD), onSecondaryContainer = Color(0xFF40342D),
    tertiary = Color(0xFFFA233B), tertiaryContainer = Color(0xFFFFEBEE),
    background = Color.White, onBackground = Color(0xFF151518),
    surface = Color.White, onSurface = Color(0xFF151518),
    surfaceVariant = Color(0xFFF2F2F7), onSurfaceVariant = Color(0xFF77777E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFEFDFB),
    surfaceContainer = Color(0xFFF4F4F7), surfaceContainerHigh = Color(0xFFECECF0),
    surfaceContainerHighest = Color(0xFFE5E5EA),
    outline = Color(0xFF8A8278), outlineVariant = Color(0xFFDEDEE3),
    error = Color(0xFFAB493B), errorContainer = Color(0xFFFFE6DF)
)

val NightColors = darkColorScheme(
    primary = Color(0xFFFF375F), onPrimary = Color.White,
    primaryContainer = Color(0xFF40202B), onPrimaryContainer = Color(0xFFFFCED8),
    secondary = Color(0xFFD4D4DC), onSecondary = Color(0xFF151518),
    secondaryContainer = Color(0xFF2A2A31), onSecondaryContainer = Color(0xFFF3F3F7),
    tertiary = Color(0xFFFF5C79), onTertiary = Color.White,
    tertiaryContainer = Color(0xFF40202B), onTertiaryContainer = Color(0xFFFFCED8),
    background = Color(0xFF08080A), onBackground = Color(0xFFF5F5F8),
    surface = Color(0xFF141417), onSurface = Color(0xFFF5F5F8),
    surfaceVariant = Color(0xFF242429), onSurfaceVariant = Color(0xFFB5B5BF),
    surfaceContainerLowest = Color(0xFF0C0C0F), surfaceContainerLow = Color(0xFF1B1B20),
    surfaceContainer = Color(0xFF212127), surfaceContainerHigh = Color(0xFF292930),
    surfaceContainerHighest = Color(0xFF34343D),
    outline = Color(0xFF93939F), outlineVariant = Color(0xFF3B3B43),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1B1B), onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE8E8ED), inverseOnSurface = Color(0xFF242429),
    inversePrimary = Color(0xFFBF1739), surfaceTint = Color.Transparent
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
