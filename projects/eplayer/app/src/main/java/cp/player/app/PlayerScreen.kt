package cp.player.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cp.player.model.Song
import cp.player.ui.artwork.WitchPlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Playback presentation owns no decoder, queue persistence or source resolution. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerScreen(model: EPlayerViewModel, cloud: CloudViewModel, state: Playback,
                          more: () -> Unit, comments: () -> Unit, close: () -> Unit) {
    val animate by model.animate.collectAsState()
    val context = LocalContext.current.applicationContext
    val artworkReady by produceState(initialValue = false, key1 = context) {
        withContext(Dispatchers.IO) { WitchPlayerView.prepareAssets(context) }
        value = true
    }
    val favorites by model.favorites.collectAsState()
    val likes by cloud.likes.collectAsState()
    val position by model.position.collectAsState()
    DisposableEffect(model) { model.setProgressVisible(true); onDispose { model.setProgressVisible(false) } }
    var queue by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<String?>(null) }
    var seeking by remember(state.song?.id) { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val liked = state.song?.let { song ->
        if (state.source == null) song.id in favorites else (state.remoteId ?: song.id) in likes[state.source].orEmpty()
    } ?: false
    val openLyrics = { scope.launch { lyrics = model.lyrics() }; Unit }
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        PlayerLayout(
            state.song, state.buffering,
            header = { PlayerHeader(close, openLyrics, if (state.source != null) comments else null, more) { queue = true } },
            artwork = { modifier ->
                if (!artworkReady) Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else AndroidView(
                    factory = { WitchPlayerView(it).apply { setArtworkOnly(true) } }, modifier = modifier,
                    update = { view ->
                        view.setMotionEnabled(animate)
                        view.update(state.song?.name.orEmpty(), state.playing, state.buffering, state.playRequested, position, state.duration,
                        object : WitchPlayerView.Controls {
                            override fun playPause() = model.playPause()
                            override fun previous() = model.previous()
                            override fun next() = model.next()
                            override fun seek(position: Long) = model.seek(position)
                            override fun queue() { queue = true }
                        }) })
            },
            controls = {
                PlayerTransport(state, position, seeking, { seeking = it }, {
                    seeking?.let { model.seek(it.toLong()) }; seeking = null
                }, model::playPause, model::previous, model::next, model::shuffle, model::repeat, liked) {
                    state.song?.let { song ->
                        if (state.source == null) model.favorite(song) else model.currentTrack()?.let(cloud::like)
                    }
                }
            }
        )
    }
    if (queue) ModalBottomSheet(onDismissRequest = { queue = false }) {
        LazyColumn(contentPadding = PaddingValues(20.dp)) {
            items(state.queue.size) { index -> SongRow(Track(state.queue[index]), state.queue[index].id == state.song?.id,
                { model.queueIndex(index); queue = false }) }
        }
    }
    lyrics?.let { text -> ModalBottomSheet(onDismissRequest = { lyrics = null }) {
        LazyColumn(contentPadding = PaddingValues(24.dp)) { items(text.lines()) { Text(it, modifier = Modifier.padding(vertical = 8.dp)) } }
    } }
}

/** Portrait keeps transport visible. Wide screens reserve a separate, unclipped artwork pane.
 * Metadata can scroll at large font sizes without extending underneath the character.
 */
@Composable
internal fun PlayerLayout(song: Song?, buffering: Boolean, header: @Composable () -> Unit,
                          artwork: @Composable (Modifier) -> Unit, controls: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        if (maxWidth >= 600.dp && maxWidth > maxHeight) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("player-wide")) {
                artwork(Modifier.weight(.45f).fillMaxHeight().testTag("player-artwork"))
                Column(Modifier.weight(.55f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 12.dp)) {
                    header()
                    PlayerMetadata(song)
                    if (buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
                    controls()
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp).testTag("player-portrait")) {
                header()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val artHeight = (maxHeight - 100.dp).coerceAtLeast(160.dp)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PlayerMetadata(song)
                        artwork(Modifier.fillMaxWidth().height(artHeight).testTag("player-artwork"))
                    }
                }
                if (buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
                controls()
            }
        }
    }
}

@Composable
private fun PlayerMetadata(song: Song?) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("player-metadata")) {
        Text(song?.name ?: "未选择音乐", style = MaterialTheme.typography.headlineSmall,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (!song?.artist.isNullOrBlank()) Text(song!!.artist, style = MaterialTheme.typography.bodyMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PlayerHeader(close: () -> Unit, lyrics: () -> Unit, comments: (() -> Unit)?, more: () -> Unit, queue: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = close) { Icon(Icons.Default.KeyboardArrowDown, "收起播放器") }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = lyrics) { Icon(Icons.Default.Lyrics, "歌词") }
        if (comments != null) IconButton(onClick = comments) { Icon(Icons.Default.ChatBubbleOutline, "评论") }
        IconButton(onClick = more) { Icon(Icons.Default.MoreHoriz, "更多歌曲操作") }
        IconButton(onClick = queue) { Icon(Icons.Default.QueueMusic, "播放队列") }
    }
}

@Composable
internal fun PlayerTransport(state: Playback, position: Long, seeking: Float?, seek: (Float) -> Unit, finishSeek: () -> Unit,
                             playPause: () -> Unit, previous: () -> Unit, next: () -> Unit, shuffle: () -> Unit,
                             repeat: () -> Unit, liked: Boolean, favorite: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("player-transport")) {
        Slider(value = seeking ?: position.coerceIn(0, state.duration.coerceAtLeast(0)).toFloat(), onValueChange = seek,
            onValueChangeFinished = finishSeek, valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(), enabled = state.duration > 0,
            modifier = Modifier.testTag("player-seek"))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(playerTime((seeking ?: position.toFloat()).toLong()), style = MaterialTheme.typography.labelMedium)
            Text(playerTime(state.duration), style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = previous, enabled = state.queue.size > 1) { Icon(Icons.Default.SkipPrevious, "上一首", Modifier.size(34.dp)) }
            FilledIconButton(onClick = playPause, modifier = Modifier.size(64.dp)) { Icon(
                if (state.playRequested) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (state.playRequested) "暂停" else "播放", Modifier.size(38.dp)) }
            IconButton(onClick = next, enabled = state.queue.size > 1) { Icon(Icons.Default.SkipNext, "下一首", Modifier.size(34.dp)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconToggleButton(checked = state.shuffle, onCheckedChange = { shuffle() }) { Icon(Icons.Default.Shuffle, "随机播放") }
            IconToggleButton(checked = liked, onCheckedChange = { favorite() }, enabled = state.song != null) {
                Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "喜欢")
            }
            IconButton(onClick = repeat) { Icon(if (state.repeat == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                when (state.repeat) { 1 -> "单曲循环"; 2 -> "列表循环"; else -> "循环关闭" },
                tint = if (state.repeat != 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline) }
        }
    }
}

private fun playerTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
