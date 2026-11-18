package cp.player.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cp.player.MainActivity
import cp.player.model.Song
import cp.player.ui.theme.EPlayerTheme
import cp.player.util.JsonUtils
import cp.player.util.toMediaItem
import cp.player.util.toSong
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QueueRoutingRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun song(index: Int) = Song("fixture$index", "队列夹具$index", "测试歌手", album = "测试")
    @Test fun longQueueOpensAtCurrentSongAndCanLocateItAgain() {
        val songs = (0..79).map(::song)
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            QueueSheet(Playback(songs[65], queue = songs, queueIndex = 65), {}, { _, _ -> }, {}, {}, expanded = true) {}
        } } }
        compose.onNodeWithTag("queue-row-65").assertIsDisplayed()
        compose.onNodeWithTag("queue-list").performScrollToIndex(0)
        compose.onNodeWithTag("locate-current").performClick()
        compose.onNodeWithTag("queue-row-65").assertIsDisplayed()
    }
    @Test fun shuffledQueueShowsEngineOrderAndClicksCanonicalIndex() {
        val songs = (0..3).map(::song)
        var clicked = -1
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            QueueSheet(Playback(songs[2], queue = songs, shuffle = true, queueIndex = 2, playOrder = listOf(2, 0, 3, 1)),
                { clicked = it }, { _, _ -> }, {}, {}, expanded = true) {}
        } } }
        val current = compose.onNodeWithTag("queue-row-2").fetchSemanticsNode().boundsInRoot.top
        val next = compose.onNodeWithTag("queue-row-0").fetchSemanticsNode().boundsInRoot.top
        assertTrue(current < next)
        compose.onNodeWithTag("queue-row-0").performClick()
        compose.runOnIdle { assertEquals(0, clicked) }
    }
    @Test fun allPerformersSurviveTheMediaSessionRoundTrip() {
        val song = JsonUtils.parseSong(com.google.gson.JsonParser.parseString("""{"id":1,"name":"Duet","ar":[{"id":10,"name":"A","alias":["Alias A"]},{"id":11,"name":"B"}],"al":{"id":20,"name":"Album"},"dt":180000}""").asJsonObject)!!
        val roundTrip = song.toMediaItem().toSong()
        assertEquals(listOf("10", "11"), roundTrip.artists.map { it.id })
        assertEquals(listOf("A", "B"), roundTrip.artists.map { it.name })
        assertEquals(listOf("Alias A"), roundTrip.artistAliases)
    }
    @Test fun searchPlaybackBarKeepsTheSongWithoutShowingRootTabs() {
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            Column { MusicDock(Playback(song(1)), 0, false, {}, {}, {}, {}, showNavigation = false) }
        } } }
        compose.onNodeWithTag("dock-now-playing").assertIsDisplayed()
        compose.onNodeWithTag("dock-navigation").assertDoesNotExist()
    }
    @Test fun rustShuffleAdvertisesTheSameNextSongItSeeksTo() {
        compose.runOnUiThread {
            val engine = cp.player.engine.FlickPlayer(compose.activity)
            try {
                engine.setMediaItems((0..5).map { song(it).toMediaItem() }, 0, 0)
                engine.shuffleModeEnabled = true
                val timeline = engine.currentTimeline
                val expected = timeline.getNextWindowIndex(engine.currentMediaItemIndex, androidx.media3.common.Player.REPEAT_MODE_OFF, true)
                assertTrue(expected in 1..5)
                repeat(3) { assertEquals(expected, engine.nextMediaItemIndex) }
                engine.seekToNextMediaItem()
                assertEquals(expected, engine.currentMediaItemIndex)
                engine.seekToPreviousMediaItem()
                assertEquals(0, engine.currentMediaItemIndex)
            } finally { engine.release() }
        }
    }

}
