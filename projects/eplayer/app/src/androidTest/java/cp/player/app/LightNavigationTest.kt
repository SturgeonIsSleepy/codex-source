package cp.player.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import cp.player.MainActivity
import org.junit.Rule
import org.junit.Test

class LightNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun homeHasStatsQuickPicksAndThreeTabs() {
        compose.onNodeWithContentDescription("首页").performClick()
        compose.onNodeWithText("近 7 天").assertIsDisplayed()
        compose.onNodeWithText("一键点歌").assertIsDisplayed()
        compose.onNodeWithContentDescription("探索").assertExists()
        compose.onNodeWithContentDescription("媒体库").assertExists()
    }
    @Test fun settingsReturnsThroughItsRealParents() {
        compose.onNodeWithContentDescription("账号与设置").performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("音源").assertIsDisplayed()
        compose.onNodeWithText("画面设置").performClick()
        compose.onNodeWithText("显示模式").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("音源").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("历史记录").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithContentDescription("首页").assertExists()
    }
}
