package cp.player.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import cp.player.util.DspPreferences
import cp.player.util.UserPreferences
import org.junit.Assume.assumeTrue
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
import cp.player.core.audio.AudioEffectCapabilities
import cp.player.ui.theme.EPlayerTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SoundSettingsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun state(engine: Int = 1) = SoundSettingsState(engine, true,
        listOf(SoundBand(125f, 0f), SoundBand(1000f, 0f), SoundBand(8000f, 0f)), -12f, 12f,
        true, .55f, .25f, 1f, false, 0f, false, 0f, false, 0f, AudioEffectCapabilities())

    @Test fun importDialogCancelApplyAndInvalidFilePreserveTheRightSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dsp = context.getSharedPreferences("cp_player_dsp_prefs", 0)
        assumeTrue("Preserve existing sound preferences", dsp.all.isEmpty())
        val queue = context.getSharedPreferences("eplayer_queue", 0).getString("items", null)
        assumeTrue("Preserve the user's playback queue", queue == null || queue == "[]")
        val savedEngine = UserPreferences.getAudioEngine(context)
        val file = File.createTempFile("autoeq-preview-", ".txt", context.cacheDir).apply {
            writeText("Preamp: -4.2 dB\nFilter 1: ON PK Fc 440 Hz Gain 3.5 dB Q 0.8\nFilter 2: OFF PK Fc 2000 Hz Gain 9 dB Q 2")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        var model: EPlayerViewModel? = null
        try {
            compose.runOnIdle { model = ViewModelProvider(compose.activity)[EPlayerViewModel::class.java] }
            val current = model!!
            compose.onNodeWithContentDescription("设置").performClick()
            if (savedEngine != 1) compose.onNodeWithTag("audio-engine-1").performClick()
            compose.waitUntil(15000) { UserPreferences.getAudioEngine(context) == 1 }
            compose.onNodeWithTag("open-sound-settings").performClick()
            compose.runOnIdle { current.soundSettings.previewProfile(uri) }
            compose.waitUntil(15000) { current.soundSettings.profile.value != null }
            compose.onNodeWithText("前置增益", substring = true).assertIsDisplayed()
            save("autoeq-preview-dialog", "eq-import-preview")
            assertTrue("Preview must not persist settings", dsp.all.isEmpty())
            compose.onNodeWithText("取消").performClick()
            compose.runOnIdle { assertNull(current.soundSettings.profile.value) }
            assertTrue("Cancel must preserve saved settings", dsp.all.isEmpty())
            compose.runOnIdle { current.soundSettings.previewProfile(uri) }
            compose.waitUntil(15000) { current.soundSettings.profile.value != null }
            compose.onNodeWithText("仅应用频段").performClick()
            compose.runOnIdle {
                assertTrue(DspPreferences.getEqEnabled(context))
                val bands = DspPreferences.getPeqBands(context)
                assertEquals(1, bands.size)
                assertEquals(440f, bands[0].freq, .01f)
                assertEquals(3.5f, bands[0].gain, .01f)
                assertEquals(.8f, bands[0].q, .01f)
                assertFalse("Unsupported preamp must not be enabled", DspPreferences.getPreamplifierEnabled(context))
            }
            val applied = dsp.all
            file.writeText("GraphicEQ: NaN 6")
            compose.runOnIdle { current.soundSettings.previewProfile(uri) }
            compose.waitUntil(15000) { current.soundSettings.importIssue.value != null }
            compose.onNodeWithText("导入未完成").assertIsDisplayed()
            compose.onNodeWithText("知道了").performClick()
            assertEquals("Invalid files must not partially overwrite the applied profile", applied, dsp.all)
            compose.onNodeWithTag("edit-eq-band-0").performScrollTo().performClick()
            compose.onNodeWithTag("eq-edit-frequency").performTextReplacement("NaN")
            compose.onNodeWithText("保存频段").assertIsNotEnabled()
            compose.onNodeWithText("取消").performClick()
            assertEquals("A cancelled draft must not change the imported profile", applied, dsp.all)
            compose.onNodeWithTag("edit-eq-band-0").performScrollTo().performClick()
            compose.onNodeWithTag("eq-edit-frequency").performTextReplacement("660")
            compose.onNodeWithTag("eq-edit-gain").performTextReplacement("-2.5")
            compose.onNodeWithTag("eq-edit-q").performTextReplacement("1.2")
            save("eq-band-editor", "eq-band-editor")
            compose.onNodeWithText("保存频段").performClick()
            compose.runOnIdle {
                val bands = DspPreferences.getPeqBands(context)
                assertEquals(1, bands.size)
                assertEquals(660f, bands[0].freq, .01f)
                assertEquals(-2.5f, bands[0].gain, .01f)
                assertEquals(1.2f, bands[0].q, .01f)
                assertTrue(DspPreferences.getEqEnabled(context))
            }
        } finally {
            dsp.edit().clear().commit() // Original store must be empty (guard above).
            model?.let { current ->
                compose.runOnUiThread { current.soundSettings.dismissImport(); current.setEngine(savedEngine) }
                compose.waitUntil(15000) { UserPreferences.getAudioEngine(context) == savedEngine }
            }
            file.delete()
        }
    }

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
