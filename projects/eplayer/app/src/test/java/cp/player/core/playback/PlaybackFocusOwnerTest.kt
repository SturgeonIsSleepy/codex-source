package cp.player.core.playback

import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PlaybackFocusOwnerTest {
    private class Playback(var requested: Boolean) {
        val calls = mutableListOf<String>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getPlayWhenReady" -> requested
                "pause" -> { requested = false; calls.add("pause"); null }
                "play" -> { requested = true; calls.add("play"); null }
                else -> error("Unexpected focus operation: ${method.name}")
            }
        } as Player
    }

    @Test fun transientLossResumesRequestedPlaybackEvenDuringBuffering() {
        val playback = Playback(true)
        val owner = PlaybackFocusOwner(playback.player)
        owner.lost(transient = true)
        owner.lost(transient = true) // Repeated loss must retain the original resume intent.
        owner.gained()
        owner.gained()
        assertEquals(listOf("pause", "pause", "play"), playback.calls)
    }
    @Test fun pausedPlaybackNeverStartsOnGain() {
        val playback = Playback(false)
        val owner = PlaybackFocusOwner(playback.player)
        owner.lost(transient = true)
        owner.gained()
        assertEquals(listOf("pause"), playback.calls)
    }
    @Test fun permanentLossAndExplicitPauseCancelResume() {
        for (permanent in listOf(true, false)) {
            val playback = Playback(true)
            val owner = PlaybackFocusOwner(playback.player)
            owner.lost(transient = !permanent)
            if (!permanent) owner.cancelResume()
            owner.gained()
            assertEquals(listOf("pause"), playback.calls)
        }
    }
    @Test fun replacedPlayerIgnoresQueuedFocusCallbacks() {
        val old = Playback(true)
        val owner = PlaybackFocusOwner(old.player)
        owner.lost(transient = true)
        owner.close()
        owner.gained()
        owner.lost(transient = false)
        assertEquals(listOf("pause"), old.calls)
    }
}
