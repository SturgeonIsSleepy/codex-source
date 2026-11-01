package cp.player.app

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cp.player.MainActivity
import cp.player.model.Song
import cp.player.ui.theme.EPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TravelPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    // Dispatch through the actual dialog window owner. This exercises AndroidX's
    // progress/cancel/commit path without mutating playback or an account.
    @Test fun dialogCancelRestoresAndCommitClosesOnlyTheTopWindow() {
        var dispatcher: OnBackPressedDispatcher? = null
        var closes = 0
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            var open by remember { mutableStateOf(true) }
            Text("underlying page")
            if (open) TravelDialog(onDismissRequest = { closes++; open = false },
                destination = { Text("return preview") }) {
                val view = LocalView.current
                SideEffect { dispatcher = view.findViewTreeOnBackPressedDispatcherOwner()?.onBackPressedDispatcher }
                Text("dialog content")
            }
        } } }
        compose.waitForIdle()
        assertNotNull(dispatcher)
        assertNotSame(compose.activity.onBackPressedDispatcher, dispatcher)
        gesture(dispatcher!!, cancel = true, checkPreview = true)
        compose.onNodeWithText("dialog content").assertExists()
        compose.runOnIdle { assertEquals(0, closes) }
        gesture(dispatcher!!, cancel = false)
        compose.onNodeWithText("dialog content").assertDoesNotExist()
        compose.onNodeWithText("underlying page").assertExists()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    @Test fun alertCancelAndCommitNeverRunConfirmAction() {
        var dispatcher: OnBackPressedDispatcher? = null
        var closes = 0
        var confirms = 0
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            var open by remember { mutableStateOf(true) }
            if (open) TravelAlertDialog(onDismissRequest = { closes++; open = false },
                title = {
                    val view = LocalView.current
                    SideEffect { dispatcher = view.findViewTreeOnBackPressedDispatcherOwner()?.onBackPressedDispatcher }
                    Text("confirmation")
                }, confirmButton = { TextButton(onClick = { confirms++ }) { Text("confirm") } })
        } } }
        compose.waitForIdle()
        assertNotNull(dispatcher)
        assertNotSame(compose.activity.onBackPressedDispatcher, dispatcher)
        gesture(dispatcher!!, cancel = true)
        compose.onNodeWithText("confirmation").assertExists()
        compose.runOnIdle { assertEquals(0, closes); assertEquals(0, confirms) }
        gesture(dispatcher!!, cancel = false)
        compose.onNodeWithText("confirmation").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, closes); assertEquals(0, confirms) }
    }

    @Test fun playingRowAndNavigationShareDockAndKeepClicksSeparate() {
        var opened = 0
        var played = 0
        var selected = -1
        val song = Song("dock-fixture", "测试歌曲", "测试歌手", album = "")
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            MusicDock(Playback(song = song), page = 0, discovery = false,
                choose = { selected = it }, showPlayer = { opened++ }, playPause = { played++ }, next = {})
        } } }
        compose.waitForIdle()
        val dock = compose.onNodeWithTag("music-dock").fetchSemanticsNode().boundsInRoot
        val now = compose.onNodeWithTag("dock-now-playing").fetchSemanticsNode().boundsInRoot
        val nav = compose.onNodeWithTag("dock-navigation").fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertTrue(now.left >= dock.left && now.right <= dock.right && now.top >= dock.top)
        assertTrue(nav.left >= dock.left && nav.right <= dock.right && nav.bottom <= dock.bottom)
        assertTrue("rows must be adjacent", nav.top - now.bottom in 0f..(2f * density))
        compose.onNodeWithContentDescription("播放").performClick()
        compose.runOnIdle { assertEquals(1, played); assertEquals(0, opened) }
        compose.onNodeWithText("测试歌曲").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
        compose.onNodeWithContentDescription("媒体库").performClick()
        compose.runOnIdle { assertEquals(2, selected) }
    }

    private fun gesture(dispatcher: OnBackPressedDispatcher, cancel: Boolean, checkPreview: Boolean = false) {
        val start = BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT)
        val progress = BackEventCompat(160f, 200f, .6f, BackEventCompat.EDGE_LEFT)
        compose.runOnUiThread { dispatcher.dispatchOnBackStarted(start) }
        compose.waitForIdle()
        compose.runOnUiThread { dispatcher.dispatchOnBackProgressed(progress) }
        compose.waitForIdle()
        if (checkPreview) compose.onNodeWithText("return preview").assertExists()
        compose.runOnUiThread {
            if (cancel) dispatcher.dispatchOnBackCancelled() else dispatcher.onBackPressed()
        }
        compose.waitForIdle()
    }
}
