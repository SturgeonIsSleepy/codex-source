package cp.player.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun QueueSheet(state: Playback, select: (Int) -> Unit, move: (Int, Int) -> Unit,
    remove: (Int) -> Unit, clear: () -> Unit, close: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val latestMove by rememberUpdatedState(move)
    val size by rememberUpdatedState(state.queue.size)
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
    var dragIdentity by remember { mutableStateOf<String?>(null) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragTo by remember { mutableStateOf<Int?>(null) }
    var center by remember { mutableFloatStateOf(0f) }
    fun finishDrag(commit: Boolean) {
        val from = dragIdentity?.let { latestKeys.indexOf(it).takeIf { index -> index >= 0 } }
        val to = dragTo
        dragIdentity = null; dragFrom = null; dragTo = null
        if (commit && from != null && to != null && from != to && from in 0 until size && to in 0 until size)
            latestMove(from, to)
    }
    TravelSheet(close, gesturesEnabled = !editing) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("播放队列 · ${state.queue.size}", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (editing && state.queue.isNotEmpty()) IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteSweep, "清空播放队列") }
            TextButton(onClick = { editing = !editing; finishDrag(false) }) { Text(if (editing) "完成" else "编辑") }
        }
        if (state.queue.isEmpty()) Text("暂无歌曲", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).testTag("queue-list"),
            state = list, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            itemsIndexed(state.queue, key = { index, _ -> keys[index] }) { index, song ->
                val current = if (state.queueIndex >= 0) index == state.queueIndex else song.id == state.song?.id
                Row(Modifier.fillMaxWidth().animateItem().testTag("queue-row-$index")
                    .background(if (dragTo == index) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                    .semantics {
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction("上移") { move(index, index - 1); true })
                            if (index < state.queue.lastIndex) add(CustomAccessibilityAction("下移") { move(index, index + 1); true })
                            add(CustomAccessibilityAction("移除") { remove(index); true })
                        }
                    }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        SongRow(Track(song), current, { select(index) }, enabled = !editing)
                    }
                    if (editing) {
                        IconButton(onClick = { remove(index) }) { Icon(Icons.Default.RemoveCircleOutline, "移除第 ${index + 1} 首") }
                        val currentIndex by rememberUpdatedState(index)
                        Icon(Icons.Default.DragHandle, "拖动第 ${index + 1} 首",
                            Modifier.padding(start = 4.dp).size(32.dp).pointerInput(keys[index]) {
                                detectDragGestures(
                                    onDragStart = {
                                        val row = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
                                        dragIdentity = latestKeys.getOrNull(currentIndex)
                                        dragFrom = currentIndex; dragTo = currentIndex
                                        center = row?.let { it.offset + it.size / 2f } ?: 0f
                                    },
                                    onDragCancel = { finishDrag(false) },
                                    onDragEnd = { finishDrag(true) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        val from = dragFrom
                                        if (from != null && from in 0 until size) {
                                            center += amount.y
                                            val target = list.layoutInfo.visibleItemsInfo.firstOrNull { center >= it.offset && center <= it.offset + it.size }?.index
                                            if (target != null && target in 0 until size) {
                                                dragTo = target
                                            }

                                        }
                                    })
                            }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (current) Icon(Icons.Default.GraphicEq, "当前播放", tint = MusicRed)
                }
            }
        }
    }
    if (confirmClear) TravelAlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("清空播放队列？") },
        confirmButton = { TextButton(onClick = { clear(); confirmClear = false }) { Text("清空") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
}
