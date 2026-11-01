package cp.player.app

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import cp.player.model.Song
import cp.player.ui.artwork.WitchPlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Playback presentation owns no decoder, queue persistence or source resolution. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerScreen(model: EPlayerViewModel, cloud: CloudViewModel, state: Playback,
                          more: () -> Unit, comments: () -> Unit, queueOptions: (Int) -> Unit = {}, close: () -> Unit) {
    val animate by model.animate.collectAsState()
    val coverBlur by model.coverBlur.collectAsState()
    val customBackground by model.playerBackground.collectAsState()
    val context = LocalContext.current.applicationContext
    val backdrop = MaterialTheme.colorScheme.background
    val night = backdrop.luminance() < .3f
    val artworkGeneration by produceState(initialValue = 0, key1 = context) {
        withContext(Dispatchers.IO) { WitchPlayerView.prepareAssets(context) }
        value += 1
    }
    val favorites by model.favorites.collectAsState()
    val likes by cloud.likes.collectAsState()
    val position by model.position.collectAsState()
    DisposableEffect(model) { model.setProgressVisible(true); onDispose { model.setProgressVisible(false) } }
    var queue by remember { mutableStateOf(false) }
    var qualitySheet by remember { mutableStateOf(false) }
    val quality by model.quality.collectAsState()
    var lyrics by remember { mutableStateOf<LyricsText?>(null) }
    var showLyrics by remember { mutableStateOf(false) }
    var seeking by remember(state.song?.id) { mutableStateOf<Float?>(null) }
    val liked = state.song?.let { song ->
        if (state.source == null) song.id in favorites else (state.remoteId ?: song.id) in likes[state.source].orEmpty()
    } ?: false
    LaunchedEffect(showLyrics, state.source, state.remoteId, state.song?.id) {
        lyrics = null
        if (showLyrics) lyrics = model.lyrics { lyrics = it }
    }
    val openLyrics = { showLyrics = true }
    TravelDialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        YtmPlayerPanel(model, cloud, state, customBackground,
            artwork = { modifier, characterVisible ->
                CharacterStage(state.song, modifier, coverBlur, customBackground) { portrait ->
                    AnimatedVisibility(characterVisible && artworkGeneration > 0, enter = fadeIn(tween(140)), exit = fadeOut(tween(90))) {
                        AndroidView(factory = { WitchPlayerView(it) }, modifier = portrait.testTag("player-character"), update = { view ->
                            view.setAppearance(night, backdrop.toArgb(), artworkGeneration)
                            view.setCoverBackdropEnabled(true)
                            view.setMotionEnabled(animate)
                            view.setArtworkActive(characterVisible && !showLyrics && !qualitySheet)
                            view.setPlayback(state.playing, state.playRequested, position)
                        })
                    }
                }
            },
            transport = { PlayerTransport(state, position, seeking, { seeking = it }, {
                seeking?.let { model.seek(it.toLong()) }; seeking = null
            }, model::playPause, model::previous, model::next, model::shuffle, model::repeat, liked, {
                state.song?.let { song -> if (state.source == null) model.favorite(song) else model.currentTrack()?.let(cloud::like) }
            }, showFavorite = false) },
            liked = liked, like = { state.song?.let { song -> if (state.source == null) model.favorite(song) else model.currentTrack()?.let(cloud::like) } },
            lyrics = openLyrics, comments = if (state.source != null) comments else null,
            quality = { qualitySheet = true }, more = more, songOptions = queueOptions, close = close)
    }
    if (qualitySheet) QualitySheet(model) { qualitySheet = false }
    if (showLyrics) LyricsScreen(lyrics, model) { showLyrics = false }
}

/** Portrait keeps transport visible. Wide screens reserve a separate, unclipped artwork pane.
 * Metadata can scroll at large font sizes without extending underneath the character.
 */
@Composable
internal fun PlayerLayout(song: Song?, buffering: Boolean, header: @Composable () -> Unit,
                          artwork: @Composable (Modifier) -> Unit, controls: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
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
                artwork(Modifier.weight(1f).fillMaxWidth().testTag("player-artwork"))
                PlayerMetadata(song)
                if (buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
                controls()
            }
        }
    }
}

@Composable
private fun PlayerMetadata(song: Song?) {
    AnimatedContent(song, contentKey = { it?.id },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("player-metadata"),
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "track-metadata") { display ->
        Column(Modifier.fillMaxWidth()) {
            Text(display?.name ?: "未选择音乐", style = MaterialTheme.typography.headlineSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!display?.artist.isNullOrBlank()) Text(display!!.artist, style = MaterialTheme.typography.bodyMedium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PlayerHeader(close: () -> Unit, lyrics: () -> Unit, comments: (() -> Unit)?, more: () -> Unit, queue: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = close) { Icon(Icons.Default.KeyboardArrowDown, "收起播放器") }
        Spacer(Modifier.weight(1f))
        if (comments != null) IconButton(onClick = comments) { Icon(Icons.Default.ChatBubbleOutline, "评论") }
        IconButton(onClick = more) { Icon(Icons.Default.MoreHoriz, "更多歌曲操作") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerTransport(state: Playback, position: Long, seeking: Float?, seek: (Float) -> Unit, finishSeek: () -> Unit,
                             playPause: () -> Unit, previous: () -> Unit, next: () -> Unit, shuffle: () -> Unit,
                             repeat: () -> Unit, liked: Boolean, favorite: () -> Unit, showFavorite: Boolean = true) {
    Column(Modifier.fillMaxWidth().testTag("player-transport")) {
        val limit = state.duration.coerceAtLeast(1).toFloat()
        val value = (seeking ?: position.toFloat()).coerceIn(0f, limit)
        val ink = MaterialTheme.colorScheme.onSurface
        val quietInk = MaterialTheme.colorScheme.outline
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Slider(value = value, onValueChange = seek, onValueChangeFinished = finishSeek,
            valueRange = 0f..limit, enabled = state.duration > 0,
            modifier = Modifier.testTag("player-seek").semantics {
                contentDescription = "播放进度"
                stateDescription = "${playerTime(value.toLong())} / ${playerTime(state.duration)}"
            },
            thumb = { Surface(Modifier.size(12.dp), shape = CircleShape,
                color = if (state.duration > 0) ink else quietInk) {} },
            track = { slider -> Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                val fraction = (slider.value / limit).coerceIn(0f, 1f)
                val start = if (rtl) size.width else 0f
                val end = if (rtl) 0f else size.width
                val thumb = start + (end - start) * fraction
                drawLine(quietInk, Offset(start, center.y), Offset(end, center.y), 2.dp.toPx(), StrokeCap.Round)
                drawLine(ink, Offset(start, center.y), Offset(thumb, center.y), 2.dp.toPx(), StrokeCap.Round)
            } })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(playerTime((seeking ?: position.toFloat()).toLong()), style = MaterialTheme.typography.labelMedium)
            Text(playerTime(state.duration), style = MaterialTheme.typography.labelMedium)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            IconToggleButton(checked = state.shuffle, onCheckedChange = { shuffle(); haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove) },
                modifier = Modifier.semantics { stateDescription = if (state.shuffle) "随机播放已开启" else "顺序播放" }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Shuffle, if (state.shuffle) "关闭随机播放" else "开启随机播放",
                        modifier = Modifier.size(23.dp), tint = if (state.shuffle) ink else quietInk)
                    if (state.shuffle) Box(Modifier.padding(top = 3.dp).size(4.dp).background(ink, CircleShape))
                }
            }
            IconButton(onClick = previous, enabled = state.queue.size > 1) { Icon(Icons.Default.SkipPrevious, "上一首", Modifier.size(29.dp)) }
            FilledIconButton(onClick = playPause, modifier = Modifier.size(56.dp)) { Icon(
                if (state.playRequested) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (state.playRequested) "暂停" else "播放", Modifier.size(32.dp)) }
            IconButton(onClick = next, enabled = state.queue.size > 1) { Icon(Icons.Default.SkipNext, "下一首", Modifier.size(29.dp)) }
            IconButton(onClick = repeat) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (state.repeat == 1) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        when (state.repeat) { 1 -> "单曲循环"; 2 -> "列表循环"; else -> "循环关闭" },
                        modifier = Modifier.size(23.dp), tint = if (state.repeat != 0) ink else quietInk)
                    if (state.repeat != 0) Box(Modifier.padding(top = 3.dp).size(4.dp).background(ink, CircleShape))
                }
            }
        }
        if (showFavorite) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconToggleButton(checked = liked, onCheckedChange = { favorite() }, enabled = state.song != null, modifier = Modifier.size(40.dp)) {
                Icon(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "喜欢", Modifier.size(21.dp))
            }
        }
    }
}

internal fun playerTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}
