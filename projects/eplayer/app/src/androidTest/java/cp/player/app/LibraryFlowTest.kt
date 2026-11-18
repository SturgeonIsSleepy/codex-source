package cp.player.app

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.MainActivity
import cp.player.model.Song
import cp.player.ui.theme.EPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LibraryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun libraryAlwaysEntersPlaylistsAndHasNoPresetSongRows() {
        compose.onNodeWithContentDescription("媒体库").performClick()
        compose.onNodeWithTag("music-segment-歌单").assertIsSelected()
        compose.onNodeWithTag("music-segment-歌曲").performClick()
        compose.onAllNodes(hasText("灰色のサーガ") and hasAnyAncestor(hasTestTag("root-content-2"))).assertCountEquals(0)
        compose.onAllNodes(hasText("リテラチュア") and hasAnyAncestor(hasTestTag("root-content-2"))).assertCountEquals(0)
        compose.onNodeWithContentDescription("首页").performClick()
        compose.onNodeWithContentDescription("媒体库").performClick()
        compose.onNodeWithTag("music-segment-歌单").assertIsSelected()
    }

    @Test fun rootSettingsBackLeavesTheTaskAndReopenRetainsTheSelectedTab() {
        val activity = compose.activity
        compose.onNodeWithContentDescription("媒体库").performClick()
        // A stale library search may not consume this root page's back event.
        compose.runOnIdle { ViewModelProvider(activity)[CloudViewModel::class.java].searched.value = true }
        Espresso.pressBackUnconditionally()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        compose.waitUntil(5000) { !activity.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        compose.onNodeWithContentDescription("媒体库").assertIsSelected()
        compose.onNodeWithContentDescription("首页").performClick()
    }

    @Test fun expandedPanelDismissesOnOneBackInsteadOfCollapsingFirst() {
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            var open by remember { mutableStateOf(true) }
            Text("underlying fixture")
            if (open) TravelSheet({ open = false }) {
                Column(Modifier.fillMaxHeight(.85f)) { Text("expanded comments fixture") }
            }
        } } }
        compose.onNodeWithText("expanded comments fixture").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("expanded comments fixture").assertDoesNotExist()
        compose.onNodeWithText("underlying fixture").assertExists()
    }

    @Test fun queueEditorRemovesAndReordersDirectlyWithoutSelectingAnotherTrack() {
        val a = Song("queue-a", "Queue A", "fixture", album = "fixture")
        val b = Song("queue-b", "Queue B", "fixture", album = "fixture")
        val c = Song("queue-c", "Queue C", "fixture", album = "fixture")
        var latest = Playback(b, playing = true, position = 42000, queue = listOf(a, b, c), queueIndex = 1)
        var selects = 0
        var moves = 0
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            var state by remember { mutableStateOf(latest) }
            SideEffect { latest = state }
            QueueSheet(state, select = { selects++ }, move = { from, to ->
                moves++
                val queue = state.queue.toMutableList().apply { add(to, removeAt(from)) }
                state = state.copy(queue = queue, queueIndex = queue.indexOf(b))
            }, remove = { index ->
                val queue = state.queue.toMutableList().apply { removeAt(index) }
                state = state.copy(queue = queue, queueIndex = queue.indexOf(b))
            }, clear = { state = state.copy(queue = emptyList()) }, close = {})
        } } }
        compose.onNodeWithContentDescription("第 1 首歌曲更多操作").performClick()
        compose.onNodeWithText("从播放列表移除").performClick()
        compose.runOnIdle {
            assertEquals(listOf(b, c), latest.queue)
            assertEquals(b, latest.song)
            assertEquals(42000L, latest.position)
            assertEquals(0, selects)
        }
        val actions = compose.onNodeWithTag("queue-row-1").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnUiThread { assertTrue(actions.first { it.label == "上移" }.action()) }
        compose.runOnIdle { assertEquals(listOf(c, b), latest.queue); assertEquals(1, latest.queueIndex); assertEquals(0, selects) }
        val distance = 84f * compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("queue-row-0").performTouchInput {
            down(center); advanceEventTime(600); moveTo(Offset(center.x, center.y + distance)); up()
        }
        compose.onNodeWithTag("queue-list").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("one move on drop, not repeated hover commands", 2, moves)
            assertEquals("drag handle must reorder rather than dismiss the sheet", listOf(b, c), latest.queue)
            assertEquals(b, latest.song)
            assertEquals(0, selects)
        }
    }
}
