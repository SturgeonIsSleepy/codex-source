package cp.player.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cp.player.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LightNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun homeHasStatsAndRecommendationsRemainBehindOneEntry() {
        compose.onNodeWithContentDescription("首页").performClick()
        compose.onNodeWithText("近 7 天").assertIsDisplayed()
        compose.onNodeWithText("继续播放").assertIsDisplayed()
        compose.onNodeWithText("每日推荐").assertDoesNotExist()
        compose.onNodeWithText("私人 FM").assertDoesNotExist()
        compose.onNodeWithText("网易云").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun rightmostSettingsOwnsQualityAndSourceManagement() {
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithText("音质").assertIsDisplayed().performClick()
        compose.onNodeWithText("Hi-Res").assertIsDisplayed()
        compose.onNodeWithText("无损").assertIsDisplayed()
        // Open and inspect only; never change a real user's quality or account.
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("播放与声音").assertIsDisplayed()
        compose.onNodeWithContentDescription("首页").performClick()
    }
}
