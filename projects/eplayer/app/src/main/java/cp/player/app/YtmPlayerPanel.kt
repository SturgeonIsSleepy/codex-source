package cp.player.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
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
    more: () -> Unit, close: () -> Unit) {
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 8 } }
    val compact by remember { derivedStateOf { list.firstVisibleItemIndex >= 4 } }
    val backgroundCover by animateFloatAsState(if (scrolled && !compact) .42f else 0f, tween(180), label = "player-backdrop")
    val origin by model.queueOrigin.collectAsState()
    val busy by cloud.busy.collectAsState()
    val noInterest by model.noInterest.collectAsState()
    val qualityValue by model.quality.collectAsState()
    val disliked = state.song?.let { "${state.source}:${state.remoteId ?: it.id}" in noInterest } == true
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var removeConfirm by remember { mutableStateOf(false) }
    var save by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var saveIssue by remember { mutableStateOf<String?>(null) }
    val queueTracks = remember(state.queue) { model.currentQueueTracks() }
    val canSave = queueTracks.isNotEmpty() && queueTracks.first().source != null && queueTracks.all { it.source == queueTracks.first().source }
    val keys = remember(state.queue) {
        val occurrences = mutableMapOf<String, Int>()
        state.queue.map { song ->
            val identity = "upnext:${song.id}:${song.artist}:${song.album}"
            val count = occurrences[identity] ?: 0; occurrences[identity] = count + 1
            "$identity#$count"
        }
    }
    LaunchedEffect(keys) { selected = selected.intersect(keys.toSet()) }
    val reorder = rememberSongReorder(keys, list, model::moveQueue)
    fun toggle(key: String) { selected = if (key in selected) selected - key else selected + key }
    val base = MaterialTheme.colorScheme.background
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (background == null) LyricFrostBackground(state.song, Modifier.matchParentSize()) else PlayerBackdrop(background, Modifier.matchParentSize())
        Box(Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer { alpha = backgroundCover }) {
            TrackArtwork(state.song, Modifier.matchParentSize(), 640)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(base.copy(alpha = .15f), base))))
        }
        val artworkHeight = (maxWidth - 40.dp + 38.dp).coerceAtMost(460.dp)
        LazyColumn(Modifier.fillMaxSize().systemBarsPadding(), state = list, contentPadding = PaddingValues(bottom = 20.dp)) {
            stickyHeader(key = "player-header") {
                if (compact) Row(Modifier.fillMaxWidth().background(base).height(56.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    MusicCover(state.song, Modifier.size(40.dp).clickable { scope.launch { list.animateScrollToItem(0) } })
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(state.song?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(state.song?.artist.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = model::previous) { Icon(Icons.Default.SkipPrevious, "上一首", Modifier.size(22.dp)) }
                    IconButton(onClick = model::playPause) { Icon(if (state.playRequested) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playRequested) "暂停" else "播放") }
                    IconButton(onClick = model::next) { Icon(Icons.Default.SkipNext, "下一首", Modifier.size(22.dp)) }
                } else Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) { Icon(Icons.Default.KeyboardArrowDown, "收起播放器") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = more) { Icon(Icons.Default.MoreVert, "更多歌曲操作") }
                }
            }
            item(key = "player-art") { artwork(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(artworkHeight), !scrolled) }
            item(key = "player-title") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 10.dp)) {
                    AnimatedContent(state.song, contentKey = { it?.id }, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "player-title") { song ->
                        Column(Modifier.fillMaxWidth().clickable(onClick = more)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(song?.name.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.ChevronRight, null, Modifier.size(22.dp))
                            }
                            Text(song?.artist.orEmpty(), Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Row(Modifier.padding(top = 16.dp, bottom = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .65f)), verticalAlignment = Alignment.CenterVertically) {
                            IconToggleButton(liked, { like() }) { Icon(if (liked) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt, "喜欢", Modifier.size(21.dp)) }
                            Box(Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            IconToggleButton(disliked, { model.notInterested() }) { Icon(if (disliked) Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt, "不感兴趣", Modifier.size(21.dp)) }
                        }
                        FilledTonalButton(onClick = lyrics, contentPadding = PaddingValues(horizontal = 14.dp)) { Icon(Icons.Default.FormatQuote, null, Modifier.size(19.dp)); Text("歌词", Modifier.padding(start = 6.dp)) }
                        comments?.let { action -> FilledTonalButton(onClick = action, contentPadding = PaddingValues(horizontal = 14.dp)) { Icon(Icons.Default.ChatBubbleOutline, "评论", Modifier.size(20.dp)) } }
                        FilledTonalButton(onClick = quality, contentPadding = PaddingValues(horizontal = 14.dp)) { Text(qualityName(qualityValue)) }
                    }
                }
            }
            item(key = "player-controls") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    if (state.buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
                    transport()
                }
            }
            item(key = "queue-header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    Box(Modifier.fillMaxWidth().height(32.dp).clickable { scope.launch { list.animateScrollToItem(if (compact) 0 else 4) } }, contentAlignment = Alignment.Center) {
                        Box(Modifier.width(34.dp).height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (editing) "已选 ${selected.size}" else origin ?: "播放队列", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (editing) {
                            TextButton(onClick = { selected = if (selected.size == keys.size) emptySet() else keys.toSet() }) { Text(if (selected.size == keys.size) "取消全选" else "全选") }
                            IconButton(enabled = selected.isNotEmpty(), onClick = { removeConfirm = true }) { Icon(Icons.Default.DeleteOutline, "移除所选") }
                        } else if (canSave) TextButton(enabled = !busy, onClick = { save = true; saveIssue = null; name = origin.orEmpty() }) { Icon(Icons.Default.PlaylistAdd, null, Modifier.size(20.dp)); Text("保存", Modifier.padding(start = 5.dp)) }
                        TextButton(onClick = { editing = !editing; reorder.finish(false); selected = emptySet() }) { Text(if (editing) "完成" else "编辑") }
                    }
                }
            }
            itemsIndexed(reorder.order, key = { _, key -> key }) { _, key ->
                val index = keys.indexOf(key)
                val song = state.queue.getOrNull(index) ?: return@itemsIndexed
                val current = index == state.queueIndex
                val dragging = key == reorder.dragging
                Row(Modifier.fillMaxWidth().animateItem(placementSpec = if (dragging) null else tween(180))
                    .reorderLayer(reorder, key).songDrag(reorder, key, edit = { editing = true; selected += key })
                    .background(if (dragging || current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .65f) else Color.Transparent)
                    .padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (editing) Checkbox(key in selected, { toggle(key) })
                    Box(Modifier.weight(1f)) { SongRow(Track(song), current, { if (editing) toggle(key) else model.queueIndex(index) }) }
                    Icon(Icons.Default.DragHandle, "拖动歌曲", Modifier.size(44.dp).padding(8.dp).songDrag(reorder, key, longPress = false))
                }
            }
        }
    }
    if (removeConfirm) TravelAlertDialog(onDismissRequest = { removeConfirm = false }, title = { Text("移除 ${selected.size} 首歌曲？") },
        confirmButton = { TextButton(onClick = { model.removeQueueItems(selected.map(keys::indexOf).filter { it >= 0 }); selected = emptySet(); removeConfirm = false }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { removeConfirm = false }) { Text("取消") } })
    if (save) TravelAlertDialog(onDismissRequest = { if (!busy) save = false }, title = { Text("保存播放队列") }, text = { Column {
        OutlinedTextField(name, { name = it }, singleLine = true, placeholder = { Text("歌单名称") })
        saveIssue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(enabled = !busy && name.isNotBlank(), onClick = {
        cloud.saveQueue(name, queueTracks, done = { save = false }, failed = { saveIssue = it })
    }) { Text("保存") } }, dismissButton = { TextButton(enabled = !busy, onClick = { save = false }) { Text("取消") } })
}
