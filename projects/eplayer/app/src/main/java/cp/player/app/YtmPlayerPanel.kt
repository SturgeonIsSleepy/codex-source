package cp.player.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.core.Animatable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun YtmPlayerPanel(model: EPlayerViewModel, cloud: CloudViewModel, state: Playback, background: String?,
    artwork: @Composable (Modifier, Boolean) -> Unit, transport: @Composable () -> Unit,
    liked: Boolean, like: () -> Unit, lyrics: () -> Unit, comments: (() -> Unit)?, quality: () -> Unit,
    more: () -> Unit, songOptions: (Int) -> Unit, close: () -> Unit) {
    var expansion by remember { mutableFloatStateOf(0f) }
    var queueHeight by remember { mutableFloatStateOf(1f) }
    var settling by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val queueList = rememberLazyListState()
    val queueBack = rememberBackPreview()
    fun settle(open: Boolean) {
        settling?.cancel()
        settling = scope.launch { Animatable(expansion).animateTo(if (open) 1f else 0f, tween(200)) { expansion = value } }
    }
    val queueOpen = expansion > 0f
    PreviewBackHandler(queueBack, enabled = queueOpen) { settle(false) }
    val dragState = rememberDraggableState { delta -> expansion = (expansion - delta / queueHeight).coerceIn(0f, 1f) }
    val swipe = Modifier.draggable(dragState, Orientation.Vertical,
        onDragStarted = { settling?.cancel() }, onDragStopped = { velocity ->
            settle(velocity < -300f || (velocity < 300f && expansion > .22f))
        })
    val qualityValue by model.quality.collectAsState()
    val noInterest by model.noInterest.collectAsState()
    val origin by model.queueOrigin.collectAsState()
    val busy by cloud.busy.collectAsState()
    var save by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var saveIssue by remember { mutableStateOf<String?>(null) }
    val queueTracks = remember(state.queue) { model.currentQueueTracks() }
    val canSave = queueTracks.isNotEmpty() && queueTracks.first().source != null && queueTracks.all { it.source == queueTracks.first().source }
    val openSave: () -> Unit = { saveName = origin.orEmpty(); saveIssue = null; save = true }
    val disliked = state.song?.let { "${state.source}:${state.remoteId ?: it.id}" in noInterest } == true
    val base = MaterialTheme.colorScheme.background
    @Composable
    fun PlaybackBody() {
    AnimatedContent(state.song, contentKey = { it?.id }, modifier = Modifier.fillMaxWidth().testTag("player-metadata"), transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(100)) }, label = "player-title") { song ->
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
    Column(Modifier.fillMaxWidth().height(56.dp).then(swipe).clickable { settle(true) }.testTag("queue-peek"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.padding(top = 10.dp, bottom = 10.dp).width(32.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
        Text(origin ?: "播放列表", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("player-page")) {
        val density = LocalDensity.current
        SideEffect { queueHeight = with(density) { maxHeight.toPx() }.coerceAtLeast(1f) }
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        if (background == null) {
            LyricFrostBackground(state.song, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(base.copy(alpha = if (base.luminance() < .3f) .42f else .50f)))
        } else PlayerBackdrop(background, Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(Icons.Default.KeyboardArrowDown, "收起播放器") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = more) { Icon(Icons.Default.MoreVert, "更多歌曲操作") }
            }
            if (wide) Row(Modifier.weight(1f).fillMaxWidth()) {
                artwork(Modifier.weight(.43f).fillMaxHeight().then(swipe).testTag("player-artwork"), !queueOpen)
                Column(Modifier.weight(.57f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 18.dp)) { PlaybackBody() }
            } else {
                artwork(Modifier.weight(1f).fillMaxWidth().then(swipe).testTag("player-artwork"), !queueOpen)
                PlaybackBody()
            }
        }
      if (queueOpen) {
        val scroll = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    val atTop = queueList.firstVisibleItemIndex == 0 && queueList.firstVisibleItemScrollOffset == 0
                    if (expansion < 1f || (atTop && available.y > 0)) {
                        settling?.cancel()
                        val before = expansion
                        expansion = (expansion - available.y / queueHeight).coerceIn(0f, 1f)
                        return Offset(0f, (before - expansion) * queueHeight)
                    }
                    return Offset.Zero
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (expansion < 1f) {
                        settle(available.y < -300f || (available.y < 300f && expansion > .22f))
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }
        Surface(Modifier.fillMaxSize().graphicsLayer {
            translationY = queueHeight * (1f - expansion + expansion * queueBack.progress.value)
        }.statusBarsPadding().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .nestedScroll(scroll).testTag("player-queue-surface"), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(24.dp).then(swipe).testTag("queue-drag-handle"), contentAlignment = Alignment.Center) {
            Box(Modifier.width(32.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
        }
        QueueSheet(state, model::queueIndex, model::moveQueue, model::removeQueue, model::clearQueue,
        removeSelected = model::removeQueueItems, options = songOptions,
        origin = origin, expanded = true, inline = true, listState = queueList, saveAction = if (canSave) openSave else null, header = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(60.dp).then(swipe), verticalAlignment = Alignment.CenterVertically) {
                MusicCover(state.song, Modifier.size(38.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(state.song?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(state.song?.artist.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = model::previous) { Icon(Icons.Default.SkipPrevious, "上一首", Modifier.size(22.dp)) }
                IconButton(onClick = model::playPause) { Icon(if (state.playRequested) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playRequested) "暂停" else "播放") }
                IconButton(onClick = model::next) { Icon(Icons.Default.SkipNext, "下一首", Modifier.size(22.dp)) }
            }
        }) { settle(false) }
        }
        }
      }
    }
    if (save) TravelAlertDialog(onDismissRequest = { if (!busy) save = false }, title = { Text("保存播放列表") }, text = { Column {
        OutlinedTextField(saveName, { saveName = it }, singleLine = true, placeholder = { Text("歌单名称") })
        saveIssue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(enabled = !busy && saveName.isNotBlank(), onClick = {
        cloud.saveQueue(saveName, queueTracks, done = { save = false }, failed = { saveIssue = it })
    }) { Text("保存") } }, dismissButton = { TextButton(enabled = !busy, onClick = { save = false }) { Text("取消") } })
}

@Composable
private fun PlayerAction(label: String?, icon: androidx.compose.ui.graphics.vector.ImageVector?, tag: String, action: () -> Unit) {
    Row(Modifier.height(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .7f))
        .clickable(onClick = action).padding(horizontal = 12.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, if (label == null) "评论" else null, Modifier.size(20.dp)) }
        label?.let { Text(it, if (icon == null) Modifier else Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge) }
    }
}
