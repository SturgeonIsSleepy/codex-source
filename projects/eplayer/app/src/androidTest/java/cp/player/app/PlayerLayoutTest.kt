package cp.player.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.MainActivity
import cp.player.model.Song
import cp.player.ui.artwork.WitchPlayerView
import cp.player.ui.theme.EPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Presentation-only checks: the real playback/session path has its own integration test. */
class PlayerLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val song = Song("layout-fixture", "旅の途中で聴きたい、とても長い歌の名前 / 在旅途中听见的悠长旋律", "ChouCho・上田麗奈", album = "魔女の旅々")

    @Test fun portraitKeepsTransportVisible() = checkLayout(false, 1f)
    @Test fun portraitLargeTextKeepsTransportVisible() = checkLayout(false, 2f)
    @Test fun landscapeSeparatesArtworkAndControls() = checkLayout(true, 1f)
    @Test fun landscapeLargeTextKeepsEveryActionReachable() = checkLayout(true, 2f)

    @Test fun bufferingRequestStillOffersPause() = checkLayout(false, 1f, requested = true)

    private fun checkLayout(wide: Boolean, fontScale: Float, requested: Boolean = false) {
        compose.runOnUiThread {
            compose.activity.requestedOrientation = if (wide) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(15000) {
            compose.activity.resources.configuration.orientation == if (wide) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        }
        val sought = mutableStateOf<Float?>(null)
        val clicks = AtomicInteger()
        val action = { clicks.incrementAndGet(); Unit }
        val state = Playback(song = song, buffering = requested, duration = 60000, queue = listOf(song, song.copy(id = "second")), playRequested = requested)
        // MainActivity is already a Compose host. Replacing its content avoids a test-only
        // Activity or launcher entry being included in production/release builds.
        compose.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    EPlayerTheme {
                        PlayerLayout(song, false,
                            header = { PlayerHeader(action, action, action, action, action) },
                            artwork = { modifier -> AndroidView(factory = { context ->
                                WitchPlayerView(context).apply { setArtworkOnly(true) }
                            }, modifier = modifier) },
                            controls = { PlayerTransport(state, 0, sought.value, { sought.value = it }, {}, action, action, action, action, action, false, action) })
                    }
                }
            }
        }
        compose.waitForIdle()
        val metadata = compose.onNodeWithTag("player-metadata").getUnclippedBoundsInRoot()
        val artwork = compose.onNodeWithTag("player-artwork").getUnclippedBoundsInRoot()
        if (wide) {
            compose.onNodeWithTag("player-wide").assertExists()
            assertTrue("Metadata must stay beside, never underneath, Elaina", artwork.right <= metadata.left)
        } else {
            compose.onNodeWithTag("player-portrait").assertExists()
            assertTrue("The entire title block precedes the artwork", metadata.bottom <= artwork.top)
            compose.onNodeWithTag("player-transport").assertIsDisplayed()
        }
        for (description in listOf(if (requested) "暂停" else "播放", "上一首", "下一首", "随机播放", "喜欢", "循环关闭")) {
            val button = compose.onNodeWithContentDescription(description)
            if (wide) button.performScrollTo()
            button.assertIsDisplayed().assertHasClickAction().performClick()
        }
        assertEquals("All transport callbacks remain connected", 6, clicks.get())
        val seek = compose.onNodeWithTag("player-seek")
        if (wide) seek.performScrollTo()
        seek.assertIsDisplayed().performSemanticsAction(SemanticsActions.SetProgress) { set -> set(30000f) }
        assertEquals("Seek stays accessible without dragging the custom thumb", 30000f, sought.value!!, .01f)
        if (wide) compose.onNodeWithContentDescription("收起播放器").performScrollTo()
        // Keep a native component render for visual review; it is not an audio test result.
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "layout-check").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder, "${if (wide) "landscape" else "portrait"}-$fontScale${if (requested) "-buffering" else ""}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
