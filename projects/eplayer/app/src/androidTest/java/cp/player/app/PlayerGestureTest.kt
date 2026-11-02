package cp.player.app

import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import cp.player.MainActivity
import cp.player.model.Song
import cp.player.ui.artwork.WitchPlayerView
import cp.player.ui.theme.EPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerGestureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val songs = (0..8).map { Song("gesture-$it", "手势歌曲 $it", "手势歌手", album = "手势") }
    private fun model() = ViewModelProvider(compose.activity)[EPlayerViewModel::class.java]
    private fun cloud() = ViewModelProvider(compose.activity)[CloudViewModel::class.java]

    @Test fun heldDragOnlyReordersAndSelectionCannotMoveSongs() {
        val moves = mutableListOf<Pair<Int, Int>>()
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            QueueSheet(Playback(songs[0], queue = songs.take(4), queueIndex = 0), {}, { a, b -> moves += a to b }, {}, {}, close = {})
        } } }
        val rowHeight = compose.onNodeWithTag("queue-row-1").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("queue-row-1").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, rowHeight * .65f))
            moveBy(Offset(0f, rowHeight * .45f))
            up()
        }
        compose.runOnIdle { assertEquals(listOf(1 to 2), moves) }
        compose.onNodeWithText("编辑").assertIsDisplayed()
        compose.onNodeWithText("已选 1").assertDoesNotExist()
        compose.onNodeWithTag("queue-row-1").performTouchInput { longClick() }
        compose.onNodeWithTag("queue-row-1").assertIsSelected()
        compose.onNodeWithTag("queue-row-0").assertIsNotSelected()
        compose.onNodeWithTag("queue-row-1").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(0f, rowHeight * 1.1f))
            up()
        }
        compose.runOnIdle { assertEquals("Selection mode must never reorder", listOf(1 to 2), moves) }
        compose.onNodeWithText("已选 1").assertIsDisplayed()
    }

    @Test fun normalTapStillPlaysTheChosenSong() {
        var chosen = -1
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            QueueSheet(Playback(songs[0], queue = songs.take(4), queueIndex = 0), { chosen = it }, { _, _ -> }, {}, {}, close = {})
        } } }
        compose.onNodeWithTag("queue-row-2").performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, chosen) }
    }

    @Test fun queueFollowsCoverSwipeBeforeReleaseAndCanBePulledDown() {
        val player = model(); val library = cloud()
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            YtmPlayerPanel(player, library, Playback(songs[0], queue = songs, queueIndex = 0), null,
                { modifier, visible -> Box(modifier) { if (visible) Text("角色手势夹具") } },
                { Text("控制夹具") }, false, {}, {}, {}, {}, {}, {}, {})
        } } }
        val page = compose.onNodeWithTag("player-page").fetchSemanticsNode().boundsInRoot
        val art = compose.onNodeWithTag("player-artwork")
        art.performTouchInput {
            down(center)
            moveBy(Offset(0f, -page.height * .30f), delayMillis = 300)
        }
        val surface = compose.onNodeWithTag("player-queue-surface").fetchSemanticsNode().boundsInRoot
        assertTrue("Sheet must already follow the held finger", surface.top < page.height * .90f)
        assertTrue("Sheet must not jump fully open before release", surface.top > page.height * .25f)
        art.performTouchInput { advanceEventTime(200); up() }
        compose.onNodeWithTag("queue-list").assertIsDisplayed()
        compose.onNodeWithText("角色手势夹具").assertDoesNotExist()
        compose.onNodeWithTag("queue-drag-handle").performTouchInput {
            down(center)
            moveBy(Offset(0f, page.height * .65f), delayMillis = 500)
            up()
        }
        compose.onNodeWithTag("queue-list").assertDoesNotExist()
        compose.onNodeWithText("角色手势夹具").assertIsDisplayed()
    }

    @Test fun queueReturnRetainsTheMountedCharacterView() {
        val player = model(); val library = cloud()
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            PlayerScreen(player, library, Playback(songs[0], queue = songs, queueIndex = 0), {}, {}, close = {})
        } } }
        fun find(view: View): WitchPlayerView? {
            if (view is WitchPlayerView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        fun rig() = WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find)
        var original: WitchPlayerView? = null
        compose.waitUntil(10000) { compose.runOnUiThread { original = rig() }; original != null }
        compose.onNodeWithTag("player-artwork").performTouchInput { swipeUp() }
        compose.onNodeWithTag("queue-list").assertIsDisplayed()
        compose.runOnUiThread { assertSame(original, rig()) }
        compose.onNodeWithTag("queue-drag-handle").performTouchInput { swipeDown(endY = height * 30f, durationMillis = 500) }
        compose.onNodeWithTag("queue-list").assertDoesNotExist()
        compose.runOnUiThread { assertSame("Return must not recreate the portrait", original, rig()) }
    }
}
