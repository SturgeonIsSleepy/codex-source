package cp.player.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import cp.player.MainActivity
import cp.player.model.Song
import cp.player.ui.theme.EPlayerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PullHeaderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun shortDownwardDragOnlyPartlyRevealsTheHeader() {
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            val list = rememberLazyListState(); val header = rememberPullHeader(list)
            Column(Modifier.fillMaxSize().nestedScroll(header.connection)) {
                MovingHeader(header, Modifier.testTag("moving-header")) { Text("顶栏夹具", Modifier.height(56.dp)) }
                LazyColumn(Modifier.weight(1f).testTag("header-list"), state = list) { items(100) { Text("歌曲$it", Modifier.height(80.dp)) } }
            }
        } } }
        val full = compose.onNodeWithTag("moving-header").fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("header-list").performTouchInput { swipeUp() }
        compose.onNodeWithText("顶栏夹具").assertIsNotDisplayed()
        compose.onNodeWithTag("header-list").performTouchInput { down(center); moveBy(Offset(0f, 48f)) }
        val partial = compose.onNodeWithTag("moving-header").fetchSemanticsNode().boundsInRoot.height
        assertTrue("Header must follow drag rather than jump fully open", partial > 0 && partial < full)
        compose.onNodeWithTag("header-list").performTouchInput { cancel() }
    }
    @Test fun predictiveBackTransformsContentButNotTheFixedDock() {
        val preview = BackPreview(); val scope = CoroutineScope(Dispatchers.Main.immediate)
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            TravelDialog({}, previewState = preview, fixedContent = {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp).background(Color.Red).testTag("fixed-dock"))
            }) { Box(Modifier.fillMaxSize().background(Color.Blue).testTag("moving-page")) }
        } } }
        val dock = compose.onNodeWithTag("fixed-dock").fetchSemanticsNode().boundsInRoot
        val page = compose.onNodeWithTag("moving-page").fetchSemanticsNode().boundsInRoot
        compose.runOnUiThread { scope.launch { preview.progress.snapTo(.65f) } }
        compose.waitForIdle()
        assertEquals(dock, compose.onNodeWithTag("fixed-dock").fetchSemanticsNode().boundsInRoot)
        assertTrue(compose.onNodeWithTag("moving-page").fetchSemanticsNode().boundsInRoot.width < page.width)
    }
    @Test fun homeRemainsOnOneCanvasWhenSwipedVertically() {
        val picks = (0..8).map { QuickPick("song:$it", "点歌$it", track = Track(Song("$it", "点歌$it", "歌手", album = "专辑"), source = "fixture")) }
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            HomePanel(ListeningStats(), picks, {}, {}, Modifier.size(360.dp, 600.dp))
        } } }
        val before = compose.onNodeWithText("一键点歌").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("home-static").performTouchInput { swipeUp() }
        assertEquals(before, compose.onNodeWithText("一键点歌").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("点歌8").assertIsDisplayed()
    }
}
