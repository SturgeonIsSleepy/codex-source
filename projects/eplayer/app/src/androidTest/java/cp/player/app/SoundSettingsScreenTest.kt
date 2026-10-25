package cp.player.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.MainActivity
import cp.player.engine.ExoAudioFxManager
import cp.player.ui.theme.EPlayerTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SoundSettingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun state(engine: Int = 1) = SoundSettingsState(engine, true,
        listOf(SoundBand(125f, 0f), SoundBand(1000f, 0f), SoundBand(8000f, 0f)), -12f, 12f,
        true, .55f, .25f, 1f, false, 0f, false, 0f, false, 0f, ExoAudioFxManager.Capabilities())

    @Test fun realSettingsNavigationReturnsToTheSameHome() {
        compose.onNodeWithContentDescription("设置").performClick()
        compose.onNodeWithTag("open-sound-settings").assertIsDisplayed()
        save("settings-full-page", "settings-panel")
        compose.onNodeWithTag("open-sound-settings").performClick()
        compose.onNodeWithTag("sound-settings").assertIsDisplayed()
        save("sound-full-page", "settings-panel")
        compose.onNodeWithText("返回设置").performClick()
        compose.onNodeWithTag("open-sound-settings").assertIsDisplayed()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithTag("settings-panel").assertDoesNotExist()
        compose.onNodeWithContentDescription("设置").assertIsDisplayed()
    }

    @Test fun largeTextSoundControlsRemainReachableAndAccessible() {
        val state = mutableStateOf(state())
        var gainEdit: Pair<Int, Float>? = null
        var mixEdit: Float? = null
        var flattened = false
        compose.runOnUiThread {
            compose.activity.setContent {
                EPlayerTheme {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                        SoundSettingsScreen(state.value, { state.value = state.value.copy(equalizer = it) },
                            { index, gain -> gainEdit = index to gain }, { flattened = true }, { _, _ -> },
                            { effect, value -> if (effect == "mix") mixEdit = value })
                    }
                }
            }
        }
        compose.onNodeWithTag("eq-band-1").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(3f) }
        compose.runOnIdle { assertEquals(1, gainEdit?.first); assertEquals(3f, gainEdit!!.second, .01f) }
        compose.onNodeWithText("恢复平直").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(flattened) }
        compose.onNodeWithTag("fx-mix").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.4f) }
        compose.runOnIdle { assertEquals(.4f, mixEdit!!, .01f) }
        save("sound-large-text")
        compose.onNodeWithContentDescription("均衡器").performScrollTo().performClick()
        compose.onNodeWithTag("eq-band-0").performScrollTo().assertIsNotEnabled()
    }

    @Test fun absentSystemEffectsAreDisabledRatherThanSimulated() {
        compose.runOnUiThread {
            compose.activity.setContent { EPlayerTheme {
                SoundSettingsScreen(state(0).copy(bands = emptyList()), {}, { _, _ -> }, {}, { _, _ -> }, { _, _ -> })
            } }
        }
        compose.onNodeWithContentDescription("均衡器").assertIsNotEnabled()
        compose.onNodeWithText("播放一首歌后显示设备支持的频段。").assertIsDisplayed()
        for (label in listOf("环绕感", "低音增强", "响度增强"))
            compose.onNodeWithContentDescription(label).performScrollTo().assertIsNotEnabled()
        save("sound-system-unavailable")
    }
    private fun save(name: String, tag: String? = null) {
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "layout-check").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            (if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
