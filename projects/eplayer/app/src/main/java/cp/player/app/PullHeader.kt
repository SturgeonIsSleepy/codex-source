package cp.player.app

import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Stable
internal class PullHeader(private val threshold: Float) {
    var shown by mutableStateOf(true)
    private var accumulated = 0f
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            if (available.y * accumulated < 0) accumulated = 0f
            accumulated += available.y
            if (accumulated > threshold) shown = true
            if (accumulated < -threshold) shown = false
            return Offset.Zero
        }
    }
}
@Composable
internal fun rememberPullHeader(list: LazyListState): PullHeader {
    val threshold = with(LocalDensity.current) { 12.dp.toPx() }
    return remember(list, threshold) { PullHeader(threshold) }
}
