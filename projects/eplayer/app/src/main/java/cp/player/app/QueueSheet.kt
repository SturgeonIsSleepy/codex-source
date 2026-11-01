package cp.player.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun QueueSheet(state: Playback, select: (Int) -> Unit, move: (Int, Int) -> Unit,
    remove: (Int) -> Unit, clear: () -> Unit, removeSelected: (List<Int>) -> Unit = { values -> values.sortedDescending().forEach(remove) }, close: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    val list = rememberLazyListState()
    val keys = remember(state.queue) {
        val occurrences = mutableMapOf<String, Int>()
        state.queue.map { song ->
            val identity = "${song.id}\u0000${song.artist}\u0000${song.album}"
            val count = occurrences[identity] ?: 0
            occurrences[identity] = count + 1
            "$identity#$count"
        }
    }
    LaunchedEffect(keys) { selected = selected.intersect(keys.toSet()) }
    val reorder = rememberSongReorder(keys, list, move)
    fun toggle(key: String) { selected = if (key in selected) selected - key else selected + key }
    TravelSheet(close, gesturesEnabled = !editing) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (editing) "已选 ${selected.size}" else "播放队列", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            if (editing && state.queue.isNotEmpty()) {
                TextButton(onClick = { selected = if (selected.size == keys.size) emptySet() else keys.toSet() }) { Text(if (selected.size == keys.size) "取消全选" else "全选") }
                IconButton(enabled = selected.isNotEmpty(), onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteOutline, "移除所选") }
            }
            TextButton(onClick = { reorder.finish(false); editing = !editing; selected = emptySet() }) { Text(if (editing) "完成" else "编辑") }
        }
        if (state.queue.isEmpty()) Text("暂无歌曲", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).testTag("queue-list"),
            state = list, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            itemsIndexed(reorder.order, key = { _, key -> key }) { _, key ->
                val index = keys.indexOf(key)
                val song = state.queue.getOrNull(index) ?: return@itemsIndexed
                val current = if (state.queueIndex >= 0) index == state.queueIndex else song.id == state.song?.id
                val dragging = reorder.dragging == key
                val surface = MaterialTheme.colorScheme.surfaceContainerHigh
                Row(Modifier.fillMaxWidth()
                    .animateItem(fadeInSpec = tween(150), placementSpec = if (dragging) null else tween(180), fadeOutSpec = tween(150))
                    .reorderLayer(reorder, key)
                    .songDrag(reorder, key, edit = { editing = true; selected = selected + key }).testTag("queue-row-$index")
                    .background(if (dragging) surface else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(14.dp))
                    .semantics {
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction("上移") { move(index, index - 1); true })
                            if (index < state.queue.lastIndex) add(CustomAccessibilityAction("下移") { move(index, index + 1); true })
                            add(CustomAccessibilityAction("移除") { remove(index); true })
                        }
                    }, verticalAlignment = Alignment.CenterVertically) {
                    if (editing) Checkbox(key in selected, { toggle(key) })
                    Box(Modifier.weight(1f)) { SongRow(Track(song), current, { if (editing) toggle(key) else select(index) }) }
                    AnimatedVisibility(editing, enter = fadeIn(tween(140)) + expandHorizontally(tween(180)),
                        exit = fadeOut(tween(100)) + shrinkHorizontally(tween(160))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DragHandle, "拖动第 ${index + 1} 首",
                                Modifier.size(44.dp).padding(6.dp).songDrag(reorder, key, longPress = false, edit = { editing = true; selected = selected + key }) , tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (!editing && current) Icon(Icons.Default.GraphicEq, "当前播放", tint = MusicRed)
                }
            }
        }
    }
    if (confirmClear) TravelAlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("移除 ${selected.size} 首歌曲？") },
        confirmButton = { TextButton(onClick = { val indices = selected.map(keys::indexOf).filter { it >= 0 }; if (indices.size == keys.size) clear() else removeSelected(indices); selected = emptySet(); confirmClear = false }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } })
}
