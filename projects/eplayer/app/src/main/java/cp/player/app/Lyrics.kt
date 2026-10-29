package cp.player.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
internal fun LyricsScreen(text: LyricsText?, model: EPlayerViewModel, close: () -> Unit) {
    val sampled by model.position.collectAsState()
    val playback by model.playback.collectAsState()
    val quality by model.quality.collectAsState()
    val context = LocalContext.current
    val duration = playback.duration.takeIf { it > 0 } ?: playback.song?.durationMs ?: 0
    val lines by produceState<List<TimedLine>>(emptyList(), text, duration) {
        value = withContext(Dispatchers.Default) { text?.let { LyricParser.document(it, duration) }.orEmpty() }
    }
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var follow by remember(playback.song?.id) { mutableStateOf(true) }
    var translate by remember { mutableStateOf(true) }
    var romanized by remember { mutableStateOf(false) }
    var qualityOpen by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    val clock = remember(playback.song?.id) { mutableLongStateOf(sampled) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val coarseFocus = remember(lines, sampled) { LyricParser.focus(lines, sampled) }
    val animateVisible by remember(list, coarseFocus) { derivedStateOf {
        list.layoutInfo.visibleItemsInfo.any { it.index in maxOf(0, coarseFocus - 1)..(coarseFocus + 1) }
    } }
    val fineTiming = lines.subList(maxOf(0, coarseFocus - 1).coerceAtMost(lines.size), (coarseFocus + 2).coerceIn(0, lines.size))
        .any { it.instrumental || it.words.any(TimedWord::timed) }
    val moving = playback.playing && foreground && animateVisible && fineTiming && !qualityOpen && !options
    // The MediaController already estimates the audio clock locally. No new service IPC or decoder polling.
    LaunchedEffect(playback.song?.id, sampled, moving) {
        clock.longValue = sampled
        if (moving) while (isActive) { clock.longValue = model.lyricPosition(); delay(32) }
    }
    val activeState = remember(lines, clock) { derivedStateOf { LyricParser.focus(lines, clock.longValue) } }
    val active by activeState
    val timed = remember(lines) { lines.any { it.time > 0 || it.words.any(TimedWord::timed) } }
    val duet = remember(lines) { lines.any { it.right } }
    LaunchedEffect(dragged) { if (dragged) follow = false }
    LaunchedEffect(active, follow) {
        if (follow && timed && active >= 0) list.animateScrollToItem(active, -(list.layoutInfo.viewportEndOffset / 3))
    }
    TravelDialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val ink = MaterialTheme.colorScheme.onBackground
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("lyrics-page")) {
            LyricFrostBackground(playback.song, Modifier.matchParentSize())
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) { Icon(Icons.Default.KeyboardArrowDown, "关闭歌词", tint = ink) }
                    MusicCover(playback.song, Modifier.size(42.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(playback.song?.name.orEmpty(), color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(playback.song?.artist.orEmpty(), color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (lines.any { it.translation.isNotBlank() }) TextButton(onClick = { translate = !translate }) {
                        Text("译", color = ink.copy(alpha = if (translate) 1f else .5f))
                    }
                    IconButton(onClick = { options = true }) { Icon(Icons.Default.MoreHoriz, "歌词选项", tint = ink) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (text == null || lines.isEmpty()) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = ink, strokeWidth = 2.dp)
                    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = 70.dp, bottom = 160.dp)) {
                        itemsIndexed(lines, key = { index, line -> "${line.time}:${line.speaker}:$index" }) { index, line ->
                            val highlighted by remember(line, index, clock, timed, activeState) { derivedStateOf {
                                val now = clock.longValue
                                if (!timed) true else now >= line.time && (line.end?.let { now < it } ?: (index == activeState.value))
                            } }
                            LyricVerse(line, clock, highlighted, duet, translate, romanized, ink,
                                Modifier.clip(RoundedCornerShape(10.dp))
                                    .then(if (timed && !line.credit) Modifier.clickable { model.seek(line.time); follow = true } else Modifier)
                                    .padding(horizontal = 6.dp, vertical = if (line.background) 7.dp else 15.dp))
                        }
                    }
                    if (!follow && timed) TextButton(onClick = { follow = true }, modifier = Modifier.align(Alignment.BottomEnd)) {
                        Icon(Icons.Default.ArrowDownward, null, tint = ink, modifier = Modifier.size(16.dp)); Text("回到当前", color = ink)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(playerTime(sampled), color = ink.copy(alpha = .7f), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = model::previous) { Icon(Icons.Default.SkipPrevious, "上一首", tint = ink, modifier = Modifier.size(28.dp)) }
                        IconButton(onClick = model::playPause, modifier = Modifier.size(60.dp)) { Icon(if (playback.playRequested) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playback.playRequested) "暂停" else "播放", tint = ink, modifier = Modifier.size(38.dp)) }
                        IconButton(onClick = model::next) { Icon(Icons.Default.SkipNext, "下一首", tint = ink, modifier = Modifier.size(28.dp)) }
                    }
                    TextButton(onClick = { qualityOpen = true }) { Text(qualityName(quality), color = ink.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
    if (qualityOpen) QualitySheet(model) { qualityOpen = false }
    TravelMenu(options, { options = false }) {
        if (lines.any { it.romanized.isNotBlank() }) DropdownMenuItem(text = { Text(if (romanized) "隐藏音译" else "显示音译") },
            onClick = { romanized = !romanized; options = false })
        if (!text?.ttmlSource.isNullOrBlank()) DropdownMenuItem(text = { Text("歌词来源 · AMLL TTML") }, onClick = {
            val url = text!!.ttmlSource
            if (url.startsWith("https://github.com/amll-dev/amll-ttml-db/")) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            options = false
        })
        DropdownMenuItem(text = { Text("回到当前歌词") }, onClick = { follow = true; options = false })
    }
}
