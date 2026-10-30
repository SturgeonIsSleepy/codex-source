package cp.player.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cp.player.MainActivity
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LightNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "layout-check").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(folder, "navigation-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun homeHasStatsAndRecommendationsRemainBehindOneEntry() {
        compose.onNodeWithContentDescription("首页").performClick()
        compose.onNodeWithText("近 7 天").assertIsDisplayed()
        compose.onNodeWithText("继续播放").assertIsDisplayed()
        compose.onNodeWithText("每日推荐").assertDoesNotExist()
        compose.onNodeWithText("私人 FM").assertDoesNotExist()
        compose.onNodeWithText("网易云").assertIsDisplayed().assertHasClickAction()
        capture("home")
    }

    @Test fun rightmostSettingsOwnsQualityAndSourceManagement() {
        compose.onNodeWithContentDescription("设置").performClick()
        capture("settings")
        compose.onNodeWithText("音质").assertIsDisplayed().performClick()
        compose.onNodeWithText("Hi-Res").assertIsDisplayed()
        compose.onNodeWithText("无损").assertIsDisplayed()
        capture("quality")
        // Open and inspect only; never change a real user's quality or account.
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("播放与声音").assertIsDisplayed()
        compose.onNodeWithContentDescription("首页").performClick()
    }

    @Test fun libraryKeepsWitchImportsAndDiscoveryReturnsHome() {
        compose.onNodeWithContentDescription("曲库").performClick()
        compose.onNodeWithContentDescription("导入音乐").assertIsDisplayed()
        compose.onNodeWithText("歌曲").performClick()
        compose.onNodeWithText("魔女の旅々").assertIsDisplayed()
        compose.onNodeWithText("灰色のサーガ").assertIsDisplayed()
        compose.onNodeWithText("リテラチュア").assertIsDisplayed()
        capture("library")
        compose.onNodeWithContentDescription("首页").performClick()
        compose.onNodeWithText("网易云").performClick()
        compose.onNodeWithContentDescription("返回首页").assertIsDisplayed()
        capture("discovery")
        compose.onNodeWithContentDescription("返回首页").performClick()
        compose.onNodeWithText("近 7 天").assertIsDisplayed()
    }
}
