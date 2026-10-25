package cp.player.core.playback

import org.junit.Assert.*
import org.junit.Test

class PlaybackSleepTimerTest {
    private class Clock {
        var now = 1000L
        var pauses = 0
        val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
        val cancelled = mutableSetOf<Int>()
        val changes = mutableListOf<Long>()
        val timer = PlaybackSleepTimer({ now }, { delay, task ->
            val id = scheduled.size
            scheduled.add(delay to task)
            val cancel: () -> Unit = { cancelled.add(id); Unit }
            cancel
        }, { pauses++ }, { changes.add(it) })
    }
    @Test fun expiryPausesOnceAndClearsPublishedDeadline() {
        val clock = Clock()
        clock.timer.set(60000)
        clock.now += 60000
        clock.scheduled[0].second()
        clock.scheduled[0].second()
        assertEquals(1, clock.pauses)
        assertEquals(listOf(61000L, 0L), clock.changes)
    }
    @Test fun replacingOrCancellingIgnoresAlreadyQueuedCallbacks() {
        val clock = Clock()
        clock.timer.set(100)
        clock.timer.set(200)
        clock.now += 500
        clock.scheduled[0].second()
        assertEquals(0, clock.pauses)
        clock.timer.set(0)
        clock.scheduled[1].second()
        assertEquals(0, clock.pauses)
        assertEquals(setOf(0, 1), clock.cancelled)
    }
    @Test fun earlyCallbackUsesRemainingMonotonicTime() {
        val clock = Clock()
        clock.timer.set(60000)
        clock.now += 10000
        clock.scheduled[0].second()
        assertEquals(50000L, clock.scheduled[1].first)
        clock.now += 50000
        clock.scheduled[1].second()
        assertEquals(1, clock.pauses)
    }
    @Test fun invalidDurationDoesNotCancelAnExistingTimer() {
        val clock = Clock()
        clock.timer.set(60000)
        for (duration in listOf(-1L, PlaybackSleepTimer.MAX_DURATION_MS + 1)) {
            assertThrows(IllegalArgumentException::class.java) { clock.timer.set(duration) }
        }
        assertEquals(61000L, clock.timer.deadline)
        assertTrue(clock.cancelled.isEmpty())
    }
}
