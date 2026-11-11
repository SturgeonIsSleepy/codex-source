package cp.player.app

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.model.Song
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoamQueuePolicyTest {
    @Test fun activatingRoamKeepsOnlyCurrentSongAndItsPosition() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val engine = ExoPlayer.Builder(context).build()
            val tracks = (0..7).map { Track(Song("fixture-$it", "夹具", "测试歌手", album = "测试"), source = "fixture") }
            try {
                engine.setMediaItems(tracks.map { it.mediaItem() }, 3, 1200)
                engine.shuffleModeEnabled = true; engine.repeatMode = Player.REPEAT_MODE_ALL
                RoamPlayback.attach(context) { engine }
                engine.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) { RoamPlayback.changed() }
                })
                RoamPlayback.start("fixture", tracks)
                assertTrue(RoamPlayback.active.value)
                assertEquals(1, engine.mediaItemCount)
                assertEquals("fixture-3", engine.currentMediaItem?.mediaId)
                assertEquals(1200L, engine.currentPosition)
                assertFalse(engine.shuffleModeEnabled); assertEquals(Player.REPEAT_MODE_OFF, engine.repeatMode)
            } finally { RoamPlayback.detach(); engine.release() }
        }
    }
    @Test fun externalModeChangesCannotMakeRoamingRandomOrLoop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext; val engine = ExoPlayer.Builder(context).build()
            val track = Track(Song("fixture", "夹具", "歌手", album = "测试"), source = "fixture")
            try {
                engine.setMediaItem(track.mediaItem())
                RoamPlayback.attach(context) { engine }; RoamPlayback.start("fixture", listOf(track))
                repeat(3) {
                    engine.shuffleModeEnabled = true; engine.repeatMode = Player.REPEAT_MODE_ALL
                    RoamPlayback.changed()
                    assertFalse(engine.shuffleModeEnabled); assertEquals(Player.REPEAT_MODE_OFF, engine.repeatMode)
                    assertEquals(1, engine.mediaItemCount)
                }
            } finally { RoamPlayback.detach(); engine.release() }
        }
    }
}
