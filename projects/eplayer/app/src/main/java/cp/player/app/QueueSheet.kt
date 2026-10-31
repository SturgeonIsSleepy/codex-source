package cp.player.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlinx.coroutines.isActive

@Composable
internal fun QueueSheet(state: Playback, select: (Int) -> Unit, move: (Int, Int) -> Unit,
    remove: (Int) -> Unit, clear: () -> Unit, close: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val latestMove by rememberUpdatedState(move)
    val keys = remember(state.queue) {
        val occurrences = mutableMapOf<String, Int>()
        state.queue.map { song ->
            val identity = "${song.id}\u0000${song.artist}\u0000${song.album}"
            val count = occurrences[identity] ?: 0
            occurrences[identity] = count + 1
            "$identity#$count"
        }
    }
    val latestKeys by rememberUpdatedState(keys)
    var order by remember(keys) { mutableStateOf(keys) }
    var dragIdentity by remember { mutableStateOf<String?>(null) }
    var center by remember { mutableFloatStateOf(0f) }
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    val speed = with(LocalDensity.current) { 680.dp.toPx() }
    fun updateTarget() {
        val key = dragIdentity ?: return
        val from = order.indexOf(key)
        val target = list.layoutInfo.visibleItemsInfo.firstOrNull {
            center >= it.offset && center < it.offset + it.size
        }?.index ?: return
        if (from >= 0 && target in order.indices && from != target) {
            order = order.toMutableList().apply { add(target, removeAt(from)) }
        }
    }
    fun finishDrag(commit: Boolean) {
        val key = dragIdentity
        val from = if (key != null) latestKeys.indexOf(key) else -1
        val to = if (key != null) order.indexOf(key) else -1
        dragIdentity = null
        if (commit && from >= 0 && to >= 0 && from != to) latestMove(from, to)
        else order = latestKeys
    }
    LaunchedEffect(keys) { dragIdentity = null }
    // Only the temporary visual order changes during a drag. The player gets one move on release.
    LaunchedEffect(dragIdentity) {
        if (dragIdentity == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive && dragIdentity != null) {
            val frame = withFrameNanos { it }
            val dt = ((frame - previous) / 1_000_000_000f).coerceIn(0f, .032f)
            previous = frame
            val info = list.layoutInfo
            val top = info.viewportStartOffset + info.beforeContentPadding
            val bottom = info.viewportEndOffset - info.afterContentPadding
            val velocity = when {
                center < top + edge -> -((top + edge - center) / edge).coerceIn(0f, 1f)
                center > bottom - edge -> ((center - bottom + edge) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (abs(velocity) > 0f && list.scrollBy(velocity * speed * dt) != 0f) updateTarget()
        }
    }
    TravelSheet(close, gesturesEnabled = !editing) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("播放队列", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (editing && state.queue.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteSweep, "清空播放队列") }
            TextButton(onClick = { finishDrag(false); editing = !editing }) { Text(if (editing) "完成" else "编辑") }
        }
        if (state.queue.isEmpty()) Text("暂无歌曲", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).testTag("queue-list"),
            state = list, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            itemsIndexed(order, key = { _, key -> key }) { _, key ->
                val index = keys.indexOf(key)
                val song = state.queue.getOrNull(index) ?: return@itemsIndexed
                val current = if (state.queueIndex >= 0) index == state.queueIndex else song.id == state.song?.id
                val dragging = dragIdentity == key
                val surface = MaterialTheme.colorScheme.surfaceContainerHigh
                Row(Modifier.fillMaxWidth()
                    .animateItem(fadeInSpec = tween(150), placementSpec = if (dragging) null else tween(180), fadeOutSpec = tween(150))
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer {
                        if (dragging) {
                            val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                            translationY = item?.let { center - it.offset - it.size / 2f } ?: 0f
                            scaleX = 1.025f; scaleY = 1.025f
                            shadowElevation = 8.dp.toPx(); shape = RoundedCornerShape(14.dp); clip = true
                        }
                    }.testTag("queue-row-$index")
                    .background(if (dragging) surface else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(14.dp))
                    .semantics {
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction("上移") { move(index, index - 1); true })
                            if (index < state.queue.lastIndex) add(CustomAccessibilityAction("下移") { move(index, index + 1); true })
                            add(CustomAccessibilityAction("移除") { remove(index); true })
                        }
                    }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SongRow(Track(song), current, { select(index) }, enabled = !editing) }
                    AnimatedVisibility(editing, enter = fadeIn(tween(140)) + expandHorizontally(tween(180)),
                        exit = fadeOut(tween(100)) + shrinkHorizontally(tween(160))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { remove(index) }, enabled = dragIdentity == null) {
                                Icon(Icons.Default.RemoveCircleOutline, "移除第 ${index + 1} 首")
                            }
                            Icon(Icons.Default.DragHandle, "拖动第 ${index + 1} 首",
                                Modifier.size(44.dp).padding(6.dp).pointerInput(key) {
                                    detectDragGestures(
                                        onDragStart = {
                                            val row = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                                            dragIdentity = key
                                            center = row?.let { it.offset + it.size / 2f } ?: 0f
                                        },
                                        onDragCancel = { finishDrag(false) }, onDragEnd = { finishDrag(true) },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            val info = list.layoutInfo
                                            center = (center + amount.y).coerceIn(info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat())
                                            updateTarget()
                                        })
                                }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!editing && current) Icon(Icons.Default.GraphicEq, "当前播放", tint = MusicRed)
                }
            }
        }
    }
    if (confirmClear) TravelAlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("清空播放队列？") },
        confirmButton = { TextButton(onClick = { clear(); confirmClear = false }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
}
