package cp.player.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import cp.player.MainActivity
import cp.player.model.Song
import cp.player.ui.theme.EPlayerTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AnimationContinuityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val song = Song("animation", "动画夹具", "测试歌手", album = "动画")
    private fun model() = ViewModelProvider(compose.activity)[EPlayerViewModel::class.java]
    private fun cloud() = ViewModelProvider(compose.activity)[CloudViewModel::class.java]
    private fun panel() {
        val player = model(); val library = cloud()
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme(darkTheme = true) {
            YtmPlayerPanel(player, library, Playback(song, queue = listOf(song), queueIndex = 0), null,
                artwork = { modifier, visible -> Box(modifier.background(Color.Blue), contentAlignment = Alignment.Center) {
                    if (visible) Box(Modifier.size(80.dp).background(Color.Red).testTag("motion-character"))
                } }, transport = { Text("控制夹具") }, liked = false, like = {}, lyrics = {}, comments = {}, quality = {}, more = {}, songOptions = {}, close = {})
        } } }
    }
    private fun red(): Int {
        val page = compose.onNodeWithTag("player-page")
        val root = page.fetchSemanticsNode().boundsInRoot
        val center = compose.onNodeWithTag("motion-character").fetchSemanticsNode().boundsInRoot.center
        val image = page.captureToImage().asAndroidBitmap()
        return android.graphics.Color.red(image.getPixel((center.x-root.left).toInt(), (center.y-root.top).toInt()))
    }
    @Test fun partialQueueDragFadesCharacterContinuouslyAndCancelRestoresClosedState() {
        panel()
        val full = red()
        val height = compose.onNodeWithTag("player-page").fetchSemanticsNode().boundsInRoot.height
        val art = compose.onNodeWithTag("player-artwork")
        art.performTouchInput { down(center); moveBy(Offset(0f,-height*.24f),delayMillis=300) }
        compose.onNodeWithTag("motion-character").assertExists()
        val partial = red()
        assertTrue("The character should fade rather than disappear on the first pixel", partial in 80..(full-10))
        art.performTouchInput { moveBy(Offset(0f,height*.10f),delayMillis=200) }
        assertTrue("Reversing the finger must restore the same visible layer", red() > partial)
        art.performTouchInput { cancel() }
        compose.onNodeWithTag("queue-list").assertDoesNotExist()
        assertTrue(red() >= full-2)
    }
    @Test fun characterReappearsDuringClosingPreviewAndCancelRestoresOpenState() {
        panel()
        compose.onNodeWithTag("queue-peek").performClick()
        compose.onNodeWithTag("motion-character").assertDoesNotExist()
        val height = compose.onNodeWithTag("player-page").fetchSemanticsNode().boundsInRoot.height
        val handle = compose.onNodeWithTag("queue-drag-handle")
        handle.performTouchInput { down(center); moveBy(Offset(0f,height*.65f),delayMillis=500) }
        compose.onNodeWithTag("motion-character").assertExists()
        assertTrue("Character should already be visible before the drawer disappears", red() > 80)
        handle.performTouchInput { cancel() }
        compose.onNodeWithTag("queue-list").assertIsDisplayed()
        compose.onNodeWithTag("motion-character").assertDoesNotExist()
    }
    @Test fun ordinaryBackClosesQueueThroughIntermediateFrames() {
        panel()
        compose.onNodeWithTag("queue-peek").performClick()
        compose.onNodeWithTag("queue-list").assertIsDisplayed()
        compose.mainClock.autoAdvance=false
        try {
            val command=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(command).use { it.readBytes() }
            compose.mainClock.advanceTimeBy(64)
            val page=compose.onNodeWithTag("player-page").fetchSemanticsNode().boundsInRoot
            val surface=compose.onNodeWithTag("player-queue-surface").fetchSemanticsNode().boundsInRoot
            assertTrue("Hardware back must have an intermediate closing frame",surface.top>page.top+10 && surface.top<page.bottom-10)
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("queue-list").assertDoesNotExist()
        } finally { compose.mainClock.autoAdvance=true }
    }
    @Test fun parsingLyricsNeverFlashesAnEmptyResultBeforeTheDocumentIsReady() {
        val player=model(); val gate=CompletableDeferred<Unit>()
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            LyricsScreen(LyricsText("[00:00.00]歌词夹具"),player,
                parse={ text,duration -> gate.await(); LyricParser.document(text,duration) }, close={})
        } } }
        compose.onNodeWithTag("lyrics-loading").assertIsDisplayed()
        compose.onNodeWithText("暂无歌词").assertDoesNotExist()
        gate.complete(Unit)
        compose.waitUntil(5000) { compose.onAllNodesWithTag("lyric-line-0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("lyrics-loading").assertDoesNotExist()
        compose.onNodeWithText("暂无歌词").assertDoesNotExist()
    }
    @Test fun pendingCoverLoadKeepsThePreviousBackgroundInsteadOfFadingToBlack() {
        val gate=CompletableDeferred<Unit>()
        val red=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val blue=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val selected=mutableStateOf("fixture:red")
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme(darkTheme=true) {
            LyricFrostBackground(song.copy(albumArtUrl=selected.value),Modifier.fillMaxSize(),fetch={ _,url,_ ->
                if(url=="fixture:red") red else { gate.await(); blue }
            })
        } } }
        fun centerRed():Int {
            val image=compose.onNodeWithTag("lyric-frost").captureToImage().asAndroidBitmap()
            return android.graphics.Color.red(image.getPixel(image.width/2,image.height/2))
        }
        compose.waitUntil(5000) { centerRed()>180 }
        val before=centerRed()
        compose.runOnIdle { selected.value="fixture:blue" }
        compose.mainClock.advanceTimeBy(500)
        assertTrue("A pending request must not clear a displayed background", centerRed()>=before-2)
        gate.complete(Unit)
        compose.waitUntil(5000) { centerRed()<80 }
    }
    @Test fun repeatedCloseTapsCommitExactlyOneDismiss() {
        var closed=0
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            var open by remember { mutableStateOf(true) }
            Box(Modifier.fillMaxSize().systemBarsPadding()) { Text("父页面") }
            if(open) TravelDialog({ closed++; open=false }) {
                val dismiss=LocalDialogDismiss.current!!
                Column(Modifier.fillMaxSize().systemBarsPadding()) { TextButton(onClick=dismiss,modifier=Modifier.testTag("motion-dismiss")) { Text("关闭动画") } }
            }
        } } }
        compose.onNodeWithTag("motion-dismiss").performTouchInput { click(); advanceEventTime(30); click() }
        compose.onNodeWithTag("motion-dismiss").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1,closed) }
        compose.onNodeWithText("父页面").assertIsDisplayed()
    }
}
