package cp.player.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun YtmPlayerPanel(model: EPlayerViewModel, cloud: CloudViewModel, state: Playback, background: String?,
    artwork: @Composable (Modifier, Boolean) -> Unit, transport: @Composable () -> Unit,
    liked: Boolean, like: () -> Unit, lyrics: () -> Unit, comments: (() -> Unit)?, quality: () -> Unit,
    more: () -> Unit, songOptions: (Int) -> Unit, close: () -> Unit) {
    var queueOpen by remember { mutableStateOf(false) }
    val qualityValue by model.quality.collectAsState()
    val noInterest by model.noInterest.collectAsState()
    val origin by model.queueOrigin.collectAsState()
    val disliked = state.song?.let { "${state.source}:${state.remoteId ?: it.id}" in noInterest } == true
    val threshold = with(androidx.compose.ui.platform.LocalDensity.current) { 44.dp.toPx() }
    val base = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxSize().testTag("player-page").pointerInput(threshold) {
        var distance = 0f
        detectVerticalDragGestures(onDragStart = { distance = 0f }, onDragEnd = { if (distance < -threshold) queueOpen = true },
            onVerticalDrag = { change, amount -> change.consume(); distance += amount })
    }) {
        if (background == null) LyricFrostBackground(state.song, Modifier.matchParentSize()) else PlayerBackdrop(background, Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(Icons.Default.KeyboardArrowDown, "收起播放器") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = more) { Icon(Icons.Default.MoreVert, "更多歌曲操作") }
            }
            artwork(Modifier.weight(1f).fillMaxWidth(), !queueOpen)
            AnimatedContent(state.song, contentKey = { it?.id }, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) }, label = "player-title") { song ->
                Column(Modifier.fillMaxWidth().clickable(onClick = more)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(song?.name ?: "未选择音乐", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ChevronRight, null, Modifier.size(22.dp))
                    }
                    Text(song?.artist.orEmpty(), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth().height(52.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.height(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .7f)).testTag("player-action-rating"), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(44.dp).fillMaxHeight().clickable(onClick = like), contentAlignment = Alignment.Center) { Icon(if (liked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt, "喜欢", Modifier.size(20.dp)) }
                    Box(Modifier.width(1.dp).height(18.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    Box(Modifier.width(44.dp).fillMaxHeight().clickable { model.notInterested() }, contentAlignment = Alignment.Center) { Icon(if (disliked) Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt, "不感兴趣", Modifier.size(20.dp)) }
                }
                PlayerAction("歌词", Icons.Default.FormatQuote, "player-action-lyrics", lyrics)
                comments?.let { PlayerAction(null, Icons.Default.ChatBubbleOutline, "player-action-comments", it) }
                PlayerAction(qualityName(qualityValue), null, "player-action-quality", quality)
            }
            transport()
            Column(Modifier.fillMaxWidth().height(48.dp).clickable { queueOpen = true }.testTag("queue-peek"), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(top = 6.dp, bottom = 8.dp).width(32.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                Text(origin ?: "播放列表", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (queueOpen) QueueSheet(state, model::queueIndex, model::moveQueue, model::removeQueue, model::clearQueue,
        removeSelected = model::removeQueueItems, options = songOptions,
        origin = origin, expanded = true, header = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(60.dp), verticalAlignment = Alignment.CenterVertically) {
                MusicCover(state.song, Modifier.size(38.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(state.song?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(state.song?.artist.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = model::previous) { Icon(Icons.Default.SkipPrevious, "上一首", Modifier.size(22.dp)) }
                IconButton(onClick = model::playPause) { Icon(if (state.playRequested) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playRequested) "暂停" else "播放") }
                IconButton(onClick = model::next) { Icon(Icons.Default.SkipNext, "下一首", Modifier.size(22.dp)) }
            }
        }) { queueOpen = false }
}

@Composable
private fun PlayerAction(label: String?, icon: androidx.compose.ui.graphics.vector.ImageVector?, tag: String, action: () -> Unit) {
    Row(Modifier.height(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .7f))
        .clickable(onClick = action).padding(horizontal = 12.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, if (label == null) "评论" else null, Modifier.size(20.dp)) }
        label?.let { Text(it, if (icon == null) Modifier else Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge) }
    }
}
