package cp.player.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cp.player.R

@Composable
internal fun ThemeBackdrop(modifier: Modifier = Modifier) { Box(modifier.background(MaterialTheme.colorScheme.background)) }

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), content = content)
}

@Composable
internal fun SettingsPanel(title: String, close: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    TravelDialog(close) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) { Icon(androidx.compose.material.icons.Icons.Default.Close, "关闭") }
                    Text(title, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.headlineSmall)
                }
                content()
            }
        }
    }
}

@Composable
internal fun ThemeSettings(model: EPlayerViewModel, close: () -> Unit) {
    val appearance by model.appearance.collectAsState()
    SettingsPanel("显示模式", close) {
        listOf("深色" to "dark", "浅色" to "light", "跟随系统" to "system").forEach { (label, value) ->
            SettingsRow(label, if (appearance == value) "✓" else null) { model.setAppearance(value) }
        }
    }
}
