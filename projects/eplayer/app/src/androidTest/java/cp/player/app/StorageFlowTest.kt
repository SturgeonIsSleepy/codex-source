package cp.player.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cp.player.MainActivity
import org.junit.Rule
import org.junit.Test

class StorageFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun storageIsReachableFromSettingsAndReturnsToTheSamePage() {
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("存储空间").performClick()
        compose.onNodeWithTag("storage-panel").assertIsDisplayed()
        compose.onNodeWithText("流媒体缓存").assertIsDisplayed()
        compose.onNodeWithText("清理缓存").assertIsDisplayed()
        compose.waitUntil(10000) {
            runCatching { compose.onNodeWithText("刷新").assertIsEnabled() }.isSuccess
        }
        // Only inspect storage here; the disk test clears its own unique cache, never user data.
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("storage-panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("设置").assertIsSelected()
        compose.onNodeWithText("账号与音源").assertIsDisplayed()
    }
}
