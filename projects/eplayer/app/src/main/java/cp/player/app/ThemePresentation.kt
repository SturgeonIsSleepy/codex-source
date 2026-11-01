package cp.player.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cp.player.R
import cp.player.ui.theme.LocalElainaTheme

@Composable
internal fun ThemeBackdrop(modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    Box(modifier.background(base)) {
        if (LocalElainaTheme.current) {
            Image(painterResource(R.drawable.elaina_theme_wallpaper), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop,
                alignment = Alignment.BottomEnd)
            // Even over a white/black pixel, normal text retains its contrast. No live blur.
            Box(Modifier.matchParentSize().background(base.copy(alpha = if (base.luminance() < .3f) .90f else .94f)))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(base.copy(alpha = .32f), base.copy(alpha = .04f)))))
        }
    }
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp), content = content)
    }
}

@Composable
internal fun SettingsPanel(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    TravelDialog(close) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = close) { Text("完成") }
                }
                content()
            }
        }
    }
}

@Composable
internal fun ThemeSettings(model: EPlayerViewModel, close: () -> Unit) {
    val enabled by model.elainaTheme.collectAsState()
    SettingsPanel("主题", close) {
        Surface(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().height(220.dp)) {
            Image(painterResource(R.drawable.elaina_theme_wallpaper), null, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, alignment = Alignment.Center)
        }
        Spacer(Modifier.height(20.dp))
        SettingsCard {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("伊蕾娜", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Switch(enabled, model::setElainaTheme)
            }
        }
    }
}
