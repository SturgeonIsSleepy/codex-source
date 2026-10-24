package cp.player.core.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class PlaybackCommandsTest {
    private class Session(val state: Int, var requested: Boolean) {
        val calls = mutableListOf<String>()
        // No codec or Android service is involved: these checks cover transport intent.
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getPlaybackState" -> state
                "getPlayWhenReady" -> requested
                "isPlaying" -> false
                "play" -> { requested = true; calls.add("play"); null }
                "pause" -> { requested = false; calls.add("pause"); null }
                "prepare" -> { calls.add("prepare"); null }
                "seekToDefaultPosition" -> { calls.add("restart"); null }
                else -> error("Unexpected transport call: ${method.name}")
            }
        } as Player
    }
    @Test fun bufferingCanBePausedAndResumed() {
        val session = Session(Player.STATE_BUFFERING, true)
        session.player.togglePlayback()
        session.player.togglePlayback()
        assertEquals(listOf("pause", "play"), session.calls)
    }
    @Test fun focusSuppressedRequestCanBePaused() {
        val session = Session(Player.STATE_READY, true)
        session.player.togglePlayback()
        assertEquals(listOf("pause"), session.calls)
    }
    @Test fun pausedReadySessionResumesWithoutRepreparing() {
        val session = Session(Player.STATE_READY, false)
        session.player.togglePlayback()
        assertEquals(listOf("play"), session.calls)
    }
    @Test fun endedTrackRestartsEvenIfPlayWasRequested() {
        val session = Session(Player.STATE_ENDED, true)
        session.player.togglePlayback()
        assertEquals(listOf("restart", "play"), session.calls)
    }
    @Test fun stoppedOrFailedSessionPreparesAgain() {
        val session = Session(Player.STATE_IDLE, true)
        session.player.togglePlayback()
        assertEquals(listOf("prepare", "play"), session.calls)
    }
}
