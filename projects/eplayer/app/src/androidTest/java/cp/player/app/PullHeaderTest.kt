package cp.player.app

import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import cp.player.MainActivity
import cp.player.ui.theme.EPlayerTheme
import org.junit.Rule
import org.junit.Test

class PullHeaderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun downwardDragShowsTheHeaderBeforeReachingTheTop() {
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            val list = rememberLazyListState(); val header = rememberPullHeader(list)
            Column(Modifier.fillMaxSize().nestedScroll(header.connection)) {
                AnimatedVisibility(header.shown || !list.canScrollBackward) { Text("顶栏夹具", Modifier.height(56.dp)) }
                LazyColumn(Modifier.weight(1f).testTag("header-list"), state = list) { items(100) { Text("歌曲$it", Modifier.height(80.dp)) } }
            }
        } } }
        compose.onNodeWithTag("header-list").performTouchInput { swipeUp() }
        compose.onNodeWithText("顶栏夹具").assertDoesNotExist()
        compose.onNodeWithTag("header-list").performTouchInput { swipeDown(startY = centerY, endY = centerY + 90) }
        compose.onNodeWithText("顶栏夹具").assertIsDisplayed()
    }
}
