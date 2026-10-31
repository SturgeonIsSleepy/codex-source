package cp.player.app

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.*
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import cp.player.model.Song

internal val MusicRed: Color
    @Composable get() = MaterialTheme.colorScheme.primary
internal val QualityChoices = listOf("标准" to "standard", "较高" to "higher", "极高" to "exhigh", "无损" to "lossless", "Hi-Res" to "hires")
internal fun qualityName(value: String) = QualityChoices.firstOrNull { it.second == value }?.first ?: "标准"

/** Older saved queues used a UI reference image as a placeholder, never an album cover. */
internal fun songArtwork(song: Song?) = song?.albumArtUrl?.takeUnless {
    it.substringBefore('?') == "file:///android_asset/player-reference.jpg"
}

@Composable
internal fun MusicCover(song: Song?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        TrackArtwork(song, Modifier.fillMaxSize(), 128, "file:///android_asset/elaina-layer.png")
    }
}

@Composable
internal fun MusicHeading(title: String, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AutoAwesome, null, Modifier.padding(end = 10.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
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
                .selectable(selected = index == selected, role = Role.Tab, onClick = { choose(index) })
                .testTag("music-segment-$label").padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
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
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text("搜索音乐", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
        if (value.isNotEmpty()) IconButton(onClick = clear) { Icon(Icons.Default.Cancel, "清除搜索", Modifier.size(19.dp)) }
        IconButton(onClick = submit) { Icon(Icons.Default.ArrowForward, "搜索", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp)) }
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
    TravelSheet(onDismissRequest = close, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("音质", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
            QualityChoices.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().clickable { model.setQuality(value); close() }.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (quality == value) Icon(Icons.Default.Check, "已选", tint = MaterialTheme.colorScheme.primary)
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
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 34.dp, bottomEnd = 34.dp)
    Box(modifier.shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = .12f), spotColor = Color.Black.copy(alpha = .12f))
        .clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLow)
        .border(.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f), shape), content = content)
}

@Composable
internal fun MusicDock(state: Playback, page: Int, discovery: Boolean, choose: (Int) -> Unit,
    showPlayer: () -> Unit, playPause: () -> Unit, next: () -> Unit, queue: () -> Unit = {}) {
          FloatingBar(Modifier.fillMaxWidth().testTag("music-dock")) {
           Column(Modifier.fillMaxWidth()) {
            if (state.song != null) {
                Row(Modifier.fillMaxWidth().height(60.dp).testTag("dock-now-playing").clickable { showPlayer() }.padding(start = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MusicCover(state.song, Modifier.size(42.dp))
                    AnimatedContent(state.song, contentKey = { it?.id }, modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "mini-track") { current ->
                        Text(current?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    }
                    IconButton(onClick = playPause) { Icon(if (state.playRequested) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playRequested) "暂停" else "播放") }
                    IconButton(onClick = next) { Icon(Icons.Default.SkipNext, "下一首") }
                    IconButton(onClick = queue) { Icon(Icons.Default.QueueMusic, "当前播放列表") }
                }
                HorizontalDivider(Modifier.padding(horizontal = 22.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
            }
                Row(Modifier.fillMaxWidth().height(64.dp).testTag("dock-navigation").padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("首页" to Icons.Default.Home, "曲库" to Icons.Default.LibraryMusic, "设置" to Icons.Default.Settings).forEachIndexed { index, (label, icon) ->
                        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(40.dp))
                            .background(if (page == index && !discovery) MusicRed.copy(alpha = .08f) else Color.Transparent)
                            .clickable { choose(index) }
                            .semantics { contentDescription = label; role = Role.Tab; selected = page == index && !discovery },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(icon, null, Modifier.size(24.dp), tint = if (page == index && !discovery) MusicRed else MaterialTheme.colorScheme.onSurface)
                            Text(label, fontSize = 11.sp, color = if (page == index && !discovery) MusicRed else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
}

/** The album replaces only the old sky panel; the full character stays in front of it. */
@Composable
internal fun CharacterStage(song: Song?, modifier: Modifier = Modifier, portrait: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(modifier) {
        val scale = minOf(maxWidth.value / 865f, maxHeight.value / 1280f)
        val left = (maxWidth.value - 865f * scale) / 2f
        val top = (maxHeight.value - 1280f * scale) / 2f
        Box(Modifier.fillMaxSize().testTag("character-stage")) {
        Box(Modifier.offset(x = (left + 80f * scale).dp, y = (top + 100f * scale).dp)
            .size((705f * scale).dp).clip(RoundedCornerShape((24f * scale).dp))
            .background(MaterialTheme.colorScheme.primaryContainer).testTag("stage-cover")) {
            TrackArtwork(song, Modifier.matchParentSize(), 512)
        }
        portrait(Modifier.fillMaxSize())
        }
    }
}

private data class ArtworkKey(val id: String?, val url: String?)

/** Only the cover transitions; the live portrait and transport stay mounted. */
@Composable
private fun TrackArtwork(song: Song?, modifier: Modifier, size: Int, fallback: String? = null) {
    val context = LocalContext.current
    AnimatedContent(ArtworkKey(song?.id, songArtwork(song)), modifier = modifier,
        transitionSpec = { (fadeIn(tween(240)) + scaleIn(tween(240), initialScale = .97f)) togetherWith fadeOut(tween(160)) },
        label = "track-artwork") { target ->
        val request = remember(context, target.url, size, fallback) {
            ImageRequest.Builder(context).data(target.url ?: fallback).size(size).build()
        }
        AsyncImage(request, null, Modifier.fillMaxSize(), contentScale = if (target.url != null || fallback == null) ContentScale.Crop else ContentScale.Fit)
    }
}
