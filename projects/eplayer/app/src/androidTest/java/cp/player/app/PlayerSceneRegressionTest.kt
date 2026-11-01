package cp.player.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.MainActivity
import cp.player.model.Song
import cp.player.ui.theme.EPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerSceneRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val songs = (0..44).map { Song("scene-$it", "歌曲 $it", "测试歌手", album = "测试") }
    private fun player() = ViewModelProvider(compose.activity)[EPlayerViewModel::class.java]
    private fun cloud() = ViewModelProvider(compose.activity)[CloudViewModel::class.java]
    private fun windowsEnabled() {
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    }
    private fun focusedWindow(): Int = instrumentation.uiAutomation.windows.first { it.isFocused && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.id

    @Test fun swipeOpensAnIndependentQueueAndHidesTheCharacter() {
        val model = player(); val library = cloud()
        val state = Playback(songs[0], queue = songs.take(3), queueIndex = 0)
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            YtmPlayerPanel(model, library, state, null, artwork = { modifier, visible -> Box(modifier) { if (visible) Text("角色夹具") } },
                transport = { Text("播放控制夹具") }, liked = false, like = {}, lyrics = {}, comments = {}, quality = {}, more = {}, songOptions = {}, close = {})
        } } }
        compose.onNodeWithText("角色夹具").assertIsDisplayed()
        compose.onNodeWithTag("queue-list").assertDoesNotExist()
        compose.onNodeWithTag("player-page").performTouchInput { swipeUp() }
        compose.onNodeWithTag("queue-list").assertIsDisplayed()
        compose.onNodeWithText("角色夹具").assertDoesNotExist()
        compose.onNodeWithContentDescription("第 2 首歌曲更多操作").assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("拖动歌曲")).assertCountEquals(0)
        Espresso.pressBack()
        compose.onNodeWithTag("queue-list").assertDoesNotExist()
        compose.onNodeWithText("角色夹具").assertIsDisplayed()
    }

    @Test fun actionCapsulesHaveTheSameHeight() {
        val model = player(); val library = cloud()
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            YtmPlayerPanel(model, library, Playback(songs[0]), null, { modifier, _ -> Box(modifier) }, {}, false, {}, {}, {}, {}, {}, {}, {})
        } } }
        val heights = listOf("player-action-rating", "player-action-lyrics", "player-action-comments", "player-action-quality").map {
            compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.height
        }
        assertTrue(heights.maxOrNull()!! - heights.minOrNull()!! <= 1f)
    }

    @Test fun coverAndCharacterShareTheOriginalSceneCoordinates() {
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            Box(Modifier.fillMaxSize()) { CharacterStage(songs[0], Modifier.width(300.dp).height(420.dp)) { Box(it) } }
        } } }
        val scene = compose.onNodeWithTag("character-stage").fetchSemanticsNode().boundsInRoot
        val cover = compose.onNodeWithTag("stage-cover").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(cover.width - cover.height) < 2f)
        assertTrue(cover.width < scene.width * .9f)
        assertTrue(cover.height < scene.height * .7f)
        assertTrue(scene.bottom > cover.bottom)
    }

    @Test fun playerTintKeepsSecondaryTextReadableEvenOnAWhiteCover() {
        val file = java.io.File(compose.activity.cacheDir, "qa-white-cover.png")
        val image = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.WHITE)
        file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
        val model = player(); val library = cloud()
        val song = songs[0].copy(albumArtUrl = file.toURI().toString())
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme(darkTheme = true) {
            YtmPlayerPanel(model, library, Playback(song), null, { modifier, _ -> Box(modifier) }, {}, false, {}, {}, {}, {}, {}, {}, {})
        } } }
        var background = android.graphics.Color.BLACK
        compose.waitUntil(12000) {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            val bounds = compose.onNodeWithText("测试歌手").fetchSemanticsNode().boundsInRoot
            background = bitmap.getPixel(2, bounds.center.y.toInt().coerceIn(0, bitmap.height - 1))
            android.graphics.Color.red(background) in 50..85
        }
        assertTrue(androidx.core.graphics.ColorUtils.calculateContrast(cp.player.ui.theme.NightColors.onSurfaceVariant.toArgb(), background) >= 4.5)
        file.delete()
    }

    @Test fun longPressSelectsThatSecondSongAndMoreIsNotADragHandle() {
        var selectedIndex = -1
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            QueueSheet(Playback(songs[0], queue = songs.take(3), queueIndex = 0), {}, { _, _ -> }, {}, {}, options = { selectedIndex = it }, close = {})
        } } }
        compose.onNodeWithContentDescription("第 2 首歌曲更多操作").performClick()
        compose.runOnIdle { assertEquals(1, selectedIndex) }
        compose.onNodeWithTag("queue-row-1").performTouchInput { longClick() }
        compose.onNodeWithTag("queue-row-1").assertIsSelected()
        compose.onNodeWithTag("queue-row-0").assertIsNotSelected()
        compose.onNodeWithText("已选 1").assertIsDisplayed()
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithText("已选 3").assertIsDisplayed()
    }

    @Test fun searchOpensFocusedWithTheKeyboardOnTheFirstEntry() {
        windowsEnabled()
        val model = player(); val library = cloud()
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            var open by remember { mutableStateOf(true) }
            var query by remember { mutableStateOf("") }
            Text("搜索前页")
            if (open) SearchScreen(query, { query = it }, library, model, {}, {}) { open = false }
        } } }
        compose.onNode(hasSetTextAction()).assertIsFocused()
        compose.waitUntil(8000) { instrumentation.uiAutomation.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
        Espresso.pressBack()
        compose.onNode(hasSetTextAction()).assertExists()
        compose.waitUntil(5000) { instrumentation.uiAutomation.windows.none { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
        Espresso.pressBack()
        compose.onNodeWithText("搜索前页").assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun chartAndAlbumNavigationKeepOneWindowAndRestoreParentScroll() {
        windowsEnabled()
        val model = player(); val library = cloud()
        compose.waitUntil(15000) { library.home.value.source != null }
        val parentTarget = MusicEntry("chart-fixture", "排行榜夹具", "fixture", MusicKind.CHARTS)
        val entries = (0..44).map { MusicEntry("entry-$it", "榜单 $it", "fixture", MusicKind.PLAYLIST) }
        val parent = DetailPage(parentTarget, MusicDetail(parentTarget, entries = entries), loading = false)
        val childTarget = MusicEntry("album-fixture", "专辑夹具", "fixture", MusicKind.ALBUM)
        val child = DetailPage(childTarget, MusicDetail(childTarget, tracks = songs.map { Track(it) }), loading = false)
        compose.runOnUiThread { library.pages.value = listOf(parent); compose.activity.setContent { EPlayerTheme { CloudDetailScreen(library, model, {}) { _, _ -> } } } }
        compose.onNodeWithTag("cloud-detail-list").performScrollToIndex(15)
        val window = focusedWindow()
        compose.runOnUiThread { library.pages.value = listOf(parent, child) }
        compose.onNodeWithTag("cloud-detail-list").performScrollToIndex(25)
        assertEquals("Pushing details must not recreate the Android window", window, focusedWindow())
        Espresso.pressBack()
        compose.onNodeWithText("排行榜夹具").assertIsDisplayed()
        assertEquals("Popping details must keep the same Android window", window, focusedWindow())
        compose.onAllNodes(hasText("专辑夹具")).assertCountEquals(0)
        compose.onNodeWithText("榜单 15").assertIsDisplayed()
    }
}
