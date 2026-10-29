package cp.player.app

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import cp.player.model.Song

internal val MusicRed: Color
    @Composable get() = MaterialTheme.colorScheme.primary
internal val QualityChoices = listOf("标准" to "standard", "较高" to "higher", "极高" to "exhigh", "无损" to "lossless", "Hi-Res" to "hires")
internal fun qualityName(value: String) = QualityChoices.firstOrNull { it.second == value }?.first ?: "标准"

@Composable
internal fun MusicCover(song: Song?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        AsyncImage(song?.albumArtUrl ?: "file:///android_asset/elaina-layer.png", null,
            Modifier.fillMaxSize(), contentScale = if (song?.albumArtUrl != null) ContentScale.Crop else ContentScale.Fit)
    }
}

@Composable
internal fun MusicHeading(title: String, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
        action?.invoke(this)
    }
}

@Composable
internal fun MusicSegments(labels: List<String>, selected: Int, choose: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(3.dp)) {
        labels.forEachIndexed { index, label ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                .background(if (index == selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                .clickable { choose(index) }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                Text(label, style = MaterialTheme.typography.labelLarge,
                    color = if (index == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun MusicSearch(value: String, change: (String) -> Unit, submit: () -> Unit, clear: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
        BasicTextField(value, change, Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MusicRed), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text("搜索音乐", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
        if (value.isNotEmpty()) IconButton(onClick = clear) { Icon(Icons.Default.Cancel, "清除搜索", Modifier.size(19.dp)) }
        IconButton(onClick = submit) { Icon(Icons.Default.ArrowForward, "搜索", tint = MusicRed, modifier = Modifier.size(21.dp)) }
    }
}

@Composable
internal fun SettingsRow(title: String, value: String? = null, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 4.dp).size(20.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QualitySheet(model: EPlayerViewModel, close: () -> Unit) {
    val quality by model.quality.collectAsState()
    ModalBottomSheet(onDismissRequest = close, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("音质", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
            QualityChoices.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().clickable { model.setQuality(value); close() }.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (quality == value) Icon(Icons.Default.Check, "已选", tint = MusicRed)
                }
            }
        }
    }
}

@Composable
internal fun QualitySetting(model: EPlayerViewModel) {
    val value by model.quality.collectAsState()
    var open by remember { mutableStateOf(false) }
    SettingsRow("音质", qualityName(value)) { open = true }
    if (open) QualitySheet(model) { open = false }
}

/** Opaque floating controls: no blur, optical shader or captured backdrop. */
@Composable
internal fun FloatingBar(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(100.dp)
    Box(modifier.shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .12f))
        .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)
        .border(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f), shape), content = content)
}
