package cp.player.app

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive

@Stable
internal class SongReorder(private val list: LazyListState) {
    var order by mutableStateOf(emptyList<String>())
        private set
    var dragging by mutableStateOf<String?>(null)
        private set
    private var source = emptyList<String>()
    private var center by mutableFloatStateOf(0f)
    var onMove: (Int, Int) -> Unit = { _, _ -> }
    fun update(keys: List<String>) {
        if (source != keys) { source = keys; order = keys; dragging = null }
    }
    fun start(key: String, edit: () -> Unit) {
        if (key !in source) return
        edit()
        val row = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        dragging = key; center = row.offset + row.size / 2f
    }
    fun drag(dy: Float) {
        val info = list.layoutInfo
        center = (center + dy).coerceIn(info.viewportStartOffset.toFloat(), info.viewportEndOffset.toFloat())
        target()
    }
    private fun target() {
        val key = dragging ?: return
        val from = order.indexOf(key)
        val targetKey = list.layoutInfo.visibleItemsInfo.firstOrNull { center >= it.offset && center < it.offset + it.size }?.key as? String ?: return
        val to = order.indexOf(targetKey)
        if (from >= 0 && to >= 0 && from != to) order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun finish(commit: Boolean) {
        val key = dragging
        val from = if (key == null) -1 else source.indexOf(key)
        val to = if (key == null) -1 else order.indexOf(key)
        dragging = null
        if (commit && from >= 0 && to >= 0 && from != to) onMove(from, to) else order = source
    }
    suspend fun scroll(edge: Float, speed: Float, dt: Float) {
        val info = list.layoutInfo
        val top = info.viewportStartOffset + info.beforeContentPadding
        val bottom = info.viewportEndOffset - info.afterContentPadding
        val velocity = when {
            center < top + edge -> -((top + edge - center) / edge).coerceIn(0f, 1f)
            center > bottom - edge -> ((center - bottom + edge) / edge).coerceIn(0f, 1f)
            else -> 0f
        }
        if (velocity != 0f && list.scrollBy(velocity * speed * dt) != 0f) target()
    }
    fun offset(key: String): Float = if (dragging == key) {
        list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { center - it.offset - it.size / 2f } ?: 0f
    } else 0f
}

@Composable
internal fun rememberSongReorder(keys: List<String>, list: LazyListState, move: (Int, Int) -> Unit): SongReorder {
    val state = remember(list) { SongReorder(list) }
    SideEffect { state.onMove = move; state.update(keys) }
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    val speed = with(LocalDensity.current) { 680.dp.toPx() }
    LaunchedEffect(state.dragging) {
        if (state.dragging == null) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive && state.dragging != null) {
            val frame = withFrameNanos { it }
            state.scroll(edge, speed, ((frame - previous) / 1_000_000_000f).coerceIn(0f, .032f))
            previous = frame
        }
    }
    return state
}

internal fun Modifier.songDrag(state: SongReorder, key: String, enabled: Boolean = true,
    longPress: Boolean = true, edit: () -> Unit = {}): Modifier = if (!enabled) this else pointerInput(state, key, longPress) {
    if (longPress) detectDragGesturesAfterLongPress(
        onDragStart = { state.start(key, edit) }, onDragEnd = { state.finish(true) }, onDragCancel = { state.finish(false) },
        onDrag = { change, amount -> change.consume(); state.drag(amount.y) })
    else detectDragGestures(
        onDragStart = { state.start(key, edit) }, onDragEnd = { state.finish(true) }, onDragCancel = { state.finish(false) },
        onDrag = { change, amount -> change.consume(); state.drag(amount.y) })
}

internal fun Modifier.reorderLayer(state: SongReorder, key: String): Modifier =
    zIndex(if (state.dragging == key) 1f else 0f).graphicsLayer {
        if (state.dragging == key) {
            translationY = state.offset(key); scaleX = 1.025f; scaleY = 1.025f
            shadowElevation = 8.dp.toPx(); shape = RoundedCornerShape(14.dp); clip = true
        }
    }
