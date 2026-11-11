package cp.player.app

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

@Stable
internal class PullHeader {
    var offset by mutableFloatStateOf(0f)
        private set
    private var extent = 0f
    fun measure(height: Float) {
        if (extent != height) { extent = height; offset = offset.coerceIn(-height, 0f) }
    }
    fun reveal() { offset = 0f }
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val before = offset
            offset = (offset + available.y).coerceIn(-extent, 0f)
            return Offset(0f, offset - before)
        }
    }
}
@Composable
internal fun rememberPullHeader(list: LazyListState): PullHeader {
    val state = remember(list) { PullHeader() }
    LaunchedEffect(list.canScrollBackward) { if (!list.canScrollBackward) state.reveal() }
    return state
}
@Composable
internal fun MovingHeader(state: PullHeader, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
        val child = measurable.measure(constraints.copy(minHeight = 0))
        state.measure(child.height.toFloat())
        layout(child.width, (child.height + state.offset).roundToInt().coerceAtLeast(0)) {
            child.placeRelative(0, state.offset.roundToInt())
        }
    }, content = content)
}
