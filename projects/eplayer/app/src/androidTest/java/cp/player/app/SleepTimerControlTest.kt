package cp.player.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cp.player.MainActivity
import cp.player.ui.theme.EPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class SleepTimerControlTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun selectAdjustAndCancelUseServiceCommands() {
        val remaining = mutableStateOf(0L)
        val commands = mutableListOf<Int>()
        compose.runOnUiThread {
            compose.activity.setContent {
                EPlayerTheme { SleepTimerControl(remaining.value) {
                    commands.add(it)
                    remaining.value = it.toLong()
                } }
            }
        }
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("30 分钟").performClick()
        compose.onNodeWithText("约 30 分钟后暂停").assertIsDisplayed()
        compose.onNodeWithText("调整").performClick()
        compose.onNodeWithText("60 分钟").performClick()
        compose.onNodeWithText("约 60 分钟后暂停").assertIsDisplayed()
        val folder = File(compose.activity.getExternalFilesDir(null), "layout-check").apply { mkdirs() }
        File(folder, "sleep-timer.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("调整").performClick()
        compose.onNodeWithText("关闭定时").performClick()
        compose.onNodeWithText("设置").assertIsDisplayed()
        assertEquals(listOf(30, 60, 0), commands)
    }
}
