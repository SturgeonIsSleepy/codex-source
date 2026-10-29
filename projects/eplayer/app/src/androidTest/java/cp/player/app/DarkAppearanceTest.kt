package cp.player.app

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.ColorUtils
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.MainActivity
import cp.player.model.Song
import cp.player.ui.artwork.WitchPlayerView
import cp.player.ui.theme.EPlayerTheme
import cp.player.ui.theme.NightColors
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DarkAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun nightPlayerUsesDarkPaperWithLegibleControlsAndTheSamePortrait() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WitchPlayerView.prepareAssets(context, true)
        val song = Song("dark-fixture", "灰色のサーガ", "ChouCho", album = "")
        val state = Playback(song = song, duration = 60000, queue = listOf(song, song.copy(id = "second")))
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme(darkTheme = true) {
            val background = MaterialTheme.colorScheme.background.toArgb()
            PlayerLayout(song, false, header = { PlayerHeader({}, {}, null, {}, {}) },
                artwork = { modifier -> AndroidView(factory = { WitchPlayerView(it).apply { setMotionEnabled(false) } },
                    modifier = modifier, update = { it.setAppearance(true, background, 1) }) },
                controls = { PlayerTransport(state, 0, null, {}, {}, {}, {}, {}, {}, {}, false, {}) })
        } } }
        compose.onNodeWithTag("player-transport").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放").assertIsDisplayed()
        assertTrue(ColorUtils.calculateContrast(NightColors.onSurface.toArgb(), NightColors.surface.toArgb()) >= 7)
        assertTrue(ColorUtils.calculateContrast(NightColors.onSurfaceVariant.toArgb(), NightColors.surface.toArgb()) >= 4.5)
        assertTrue(ColorUtils.calculateContrast(NightColors.primary.toArgb(), NightColors.background.toArgb()) >= 4.5)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        assertEquals(NightColors.background.toArgb(), bitmap.getPixel(2, 2))
        val directory = File(context.getExternalFilesDir(null), "layout-check").apply { mkdirs() }
        File(directory, "night-player.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
