package cp.player.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayNextOrderTest {
    @Test fun newestManualRequestGoesBeforeEarlierRequestAndRandomSongs() {
        assertEquals(listOf(1, 2), nextPriorityIndices(listOf(null, "new", "earlier", null), 0, emptySet()))
    }
    @Test fun consumedRequestDoesNotReenterPriorityAfterShuffleLoops() {
        assertEquals(emptyList<Int>(), nextPriorityIndices(listOf("done", null, null), 2, setOf("done")))
    }
    @Test fun priorityFollowsMovedItemAndSupportsTheSameSongRequestedTwice() {
        assertEquals(listOf(3, 0), nextPriorityIndices(listOf("request-a", null, null, "request-b"), 1, emptySet()))
    }
    @Test fun removedRequestsAndEmptyQueuesHaveNoNextOverride() {
        assertEquals(emptyList<Int>(), nextPriorityIndices(emptyList(), -1, setOf("removed")))
        assertEquals(emptyList<Int>(), nextPriorityIndices(listOf(null, "current", null), 1, emptySet()))
    }
}
