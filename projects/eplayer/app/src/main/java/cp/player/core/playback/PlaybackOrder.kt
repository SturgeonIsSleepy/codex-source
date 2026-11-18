package cp.player.core.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ForwardingTimeline

/** The same traversal is used by the engine, prefetch and visible queue. */
fun Player.playbackOrder(): List<Int> {
    val timeline = currentTimeline
    val order = ArrayList<Int>(timeline.windowCount)
    var index = timeline.getFirstWindowIndex(shuffleModeEnabled)
    while (index in 0 until timeline.windowCount && order.size < timeline.windowCount) {
        order += index
        index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
    }
    return order
}

internal class OrderedTimeline(timeline: Timeline, private val order: List<Int>) : ForwardingTimeline(timeline) {
    private val positions = order.withIndex().associate { it.value to it.index }
    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean) =
        if (shuffleModeEnabled) order.firstOrNull() ?: C.INDEX_UNSET else super.getFirstWindowIndex(false)
    override fun getLastWindowIndex(shuffleModeEnabled: Boolean) =
        if (shuffleModeEnabled) order.lastOrNull() ?: C.INDEX_UNSET else super.getLastWindowIndex(false)
    override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
        if (!shuffleModeEnabled) return super.getNextWindowIndex(windowIndex, repeatMode, false)
        if (repeatMode == Player.REPEAT_MODE_ONE) return windowIndex
        val position = positions[windowIndex] ?: return C.INDEX_UNSET
        return order.getOrNull(position + 1) ?: if (repeatMode == Player.REPEAT_MODE_ALL) getFirstWindowIndex(true) else C.INDEX_UNSET
    }
    override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int {
        if (!shuffleModeEnabled) return super.getPreviousWindowIndex(windowIndex, repeatMode, false)
        if (repeatMode == Player.REPEAT_MODE_ONE) return windowIndex
        val position = positions[windowIndex] ?: return C.INDEX_UNSET
        return order.getOrNull(position - 1) ?: if (repeatMode == Player.REPEAT_MODE_ALL) getLastWindowIndex(true) else C.INDEX_UNSET
    }
}
