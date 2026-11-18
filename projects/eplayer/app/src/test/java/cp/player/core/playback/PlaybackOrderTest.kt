package cp.player.core.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import org.junit.Assert.*
import org.junit.Test

class PlaybackOrderTest {
    @Test fun displayedRandomOrderAndNextPreviousUseTheSameTraversal() {
        val items = (0..3).map { SimpleBasePlayer.MediaItemData.Builder("uid$it")
            .setMediaItem(MediaItem.Builder().setMediaId("$it").build()).build() }
        val base = SimpleBasePlayer.State.Builder().setPlaylist(items).build().timeline
        val timeline = OrderedTimeline(base, listOf(2, 0, 3, 1))
        assertEquals(2, timeline.getFirstWindowIndex(true))
        assertEquals(0, timeline.getNextWindowIndex(2, Player.REPEAT_MODE_OFF, true))
        assertEquals(3, timeline.getPreviousWindowIndex(1, Player.REPEAT_MODE_OFF, true))
        assertEquals(C.INDEX_UNSET, timeline.getNextWindowIndex(1, Player.REPEAT_MODE_OFF, true))
        assertEquals(2, timeline.getNextWindowIndex(1, Player.REPEAT_MODE_ALL, true))
        assertEquals(3, timeline.getNextWindowIndex(3, Player.REPEAT_MODE_ONE, true))
        assertEquals(3, timeline.getNextWindowIndex(2, Player.REPEAT_MODE_OFF, false))
    }
}
