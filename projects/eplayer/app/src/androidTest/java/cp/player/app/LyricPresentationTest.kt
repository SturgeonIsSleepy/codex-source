package cp.player.app

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import cp.player.MainActivity
import cp.player.ui.theme.EPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricPresentationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun actualInkSweepsWithinAWordAndSeekRestoresUnfilledText() {
        val clock = mutableLongStateOf(1000)
        val line = TimedLine(1000, "MMMMMM", 2000, listOf(TimedWord("MMMMMM", 1000, 2000)))
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme(darkTheme = true) {
            Surface(color = Color(0xFF333333)) {
                KaraokeWords(line, clock, true, Color.White, Modifier.width(280.dp).testTag("karaoke-row"))
            }
        } } }
        fun bright(): Int {
            val bitmap = compose.onNodeWithTag("karaoke-row").captureToImage().asAndroidBitmap()
            return pixels(bitmap).count { AndroidColor.red(it) > 210 && AndroidColor.green(it) > 210 && AndroidColor.blue(it) > 210 }
        }
        val initial = bright()
        compose.runOnIdle { clock.longValue = 1500 }
        val halfway = bright()
        compose.runOnIdle { clock.longValue = 2000 }
        val complete = bright()
        assertTrue("half-word must contain newly lit glyph pixels", halfway > initial + 10)
        assertTrue("remaining word must still be unlit halfway", complete > halfway * 1.2)
        compose.runOnIdle { clock.longValue = 1000 }
        assertEquals(initial, bright())
    }

    @Test fun explicitDialogueRolesUseOppositeSidesWithoutSpeakerLabels() {
        val clock = mutableLongStateOf(1500)
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            Column(Modifier.width(320.dp)) {
                LyricVerse(TimedLine(1000, "lead", 2000, speaker = "v1"), clock, true, true, true, false, Color.Black)
                LyricVerse(TimedLine(1000, "reply", 2000, speaker = "v2", right = true), clock, true, true, true, false, Color.Black)
            }
        } } }
        val lead = compose.onNodeWithTag("lyric-lead").fetchSemanticsNode().boundsInRoot
        val reply = compose.onNodeWithTag("lyric-response").fetchSemanticsNode().boundsInRoot
        assertTrue("response must move toward the other edge", reply.left > lead.left && reply.right > lead.right)
        compose.onNodeWithText("lead").assertIsDisplayed()
        compose.onNodeWithText("reply").assertIsDisplayed()
        compose.onNodeWithText("v1").assertDoesNotExist()
    }

    @Test fun interludeDotsFloatWithAudioTimeAndFreezeWithAnUnchangedClock() {
        val clock = mutableLongStateOf(1000)
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            Surface(color = Color(0xFF333333)) { InterludeDots(clock, 1000, 10000, true, Color.White) }
        } } }
        fun image() = pixels(compose.onNodeWithTag("interlude-dots").captureToImage().asAndroidBitmap())
        val first = image()
        compose.runOnIdle { clock.longValue = 1450 }
        val floated = image()
        assertFalse("dots must move, not remain a literal ellipsis", first.contentEquals(floated))
        compose.mainClock.advanceTimeBy(400)
        assertArrayEquals("unchanged playback clock must not animate indefinitely", floated, image())
    }

    @Test fun frostedCoverSuppressesDetailAndKeepsTheAlbumsColor() {
        val checker = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        for (y in 0 until 32) for (x in 0 until 32) checker.setPixel(x, y, if ((x + y) % 2 == 0) AndroidColor.RED else AndroidColor.BLUE)
        val frost = LyricFrost.create(checker, true)
        assertTrue("high-frequency cover detail must be strongly blurred", variation(frost) < variation(checker) * .2)
        val purple = average(frost)
        assertTrue(purple[0] > purple[1] + 10 && purple[2] > purple[1] + 10)
        val green = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(AndroidColor.GREEN) }
        val greenTint = average(LyricFrost.create(green, true))
        assertTrue("material must derive from the actual cover", greenTint[1] > greenTint[0] + 20 && greenTint[1] > greenTint[2] + 20)
        assertTrue("night material must leave room for readable bright text", pixels(frost).all { AndroidColor.red(it) <= 112 && AndroidColor.green(it) <= 112 && AndroidColor.blue(it) <= 112 })
    }

    private fun pixels(bitmap: Bitmap) = IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    private fun average(bitmap: Bitmap): List<Double> {
        val colors = pixels(bitmap)
        return listOf(colors.map(AndroidColor::red).average(), colors.map(AndroidColor::green).average(), colors.map(AndroidColor::blue).average())
    }
    private fun variation(bitmap: Bitmap): Double {
        val colors = pixels(bitmap)
        var total = 0.0
        for (y in 0 until bitmap.height) for (x in 1 until bitmap.width) {
            val a = colors[y * bitmap.width + x - 1]; val b = colors[y * bitmap.width + x]
            total += kotlin.math.abs(AndroidColor.red(a) - AndroidColor.red(b)) + kotlin.math.abs(AndroidColor.blue(a) - AndroidColor.blue(b))
        }
        return total / (bitmap.height * (bitmap.width - 1))
    }
}
