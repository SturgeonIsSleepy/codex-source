package cp.player.app

import org.junit.Assert.*
import org.junit.Test

class ListeningClockTest {
    @Test fun bufferingAndPausedTimeDoNotCount() {
        val clock = ListeningClock()
        assertNull(clock.update(0, "song", false))
        assertNull(clock.update(5000, "song", true))
        assertEquals("song" to 3000L, clock.update(8000, "song", false))
        assertNull(clock.update(50000, "song", false))
    }
    @Test fun switchingTracksChargesThePreviousSongAndSeekingDoesNotChangeElapsedTime() {
        val clock = ListeningClock()
        clock.update(100, "first", true)
        assertEquals("first" to 900L, clock.update(1000, "second", true))
        assertEquals("second" to 2000L, clock.update(3000, "second", true))
        assertEquals("second" to 1000L, clock.update(4000, null, false))
        assertNull(clock.update(5000, null, false))
    }
}
