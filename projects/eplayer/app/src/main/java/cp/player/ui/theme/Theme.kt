package cp.player.ui.theme

import android.app.Activity
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
    primary = Color(0xFF292622), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8EEF5), onPrimaryContainer = Color(0xFF24373F),
    secondary = Color(0xFF695448), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF2E8DD), onSecondaryContainer = Color(0xFF40342D),
    tertiary = Color(0xFF3F7F9C), tertiaryContainer = Color(0xFFE1F2F9),
    background = Color.White, onBackground = Color(0xFF272520),
    surface = Color.White, onSurface = Color(0xFF272520),
    surfaceVariant = Color(0xFFF5F3EF), onSurfaceVariant = Color(0xFF6E6961),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFEFDFB),
    surfaceContainer = Color(0xFFF8F6F2), surfaceContainerHigh = Color(0xFFF2EFE9),
    surfaceContainerHighest = Color(0xFFECE8E1),
    outline = Color(0xFF8A8278), outlineVariant = Color(0xFFD5CEC3),
    error = Color(0xFFAB493B), errorContainer = Color(0xFFFFE6DF)
)

@Composable
fun createCustomColorScheme(seedColor: Int, isDark: Boolean, pureBlack: Boolean = false): ColorScheme = PaperColors

@Composable
fun EPlayerTheme(content: @Composable () -> Unit) {
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
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    MaterialExpressiveTheme(
        colorScheme = PaperColors,
        typography = type,
        shapes = Shapes(RoundedCornerShape(3.dp), RoundedCornerShape(5.dp),
            RoundedCornerShape(9.dp), RoundedCornerShape(12.dp), RoundedCornerShape(16.dp)),
        content = content
    )
}
