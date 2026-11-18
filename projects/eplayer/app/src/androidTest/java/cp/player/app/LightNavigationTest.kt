package cp.player.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import cp.player.MainActivity
import org.junit.Rule
import org.junit.Test

class LightNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun systemBack() {
        val command = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4")
        ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
        compose.waitForIdle()
    }
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
        systemBack()
        compose.onNodeWithText("音源").assertIsDisplayed()
        systemBack()
        compose.onNodeWithText("历史记录").assertIsDisplayed()
        systemBack()
        compose.onNodeWithContentDescription("首页").assertExists()
    }
}
