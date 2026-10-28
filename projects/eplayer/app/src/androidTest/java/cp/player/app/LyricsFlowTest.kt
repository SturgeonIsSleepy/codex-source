package cp.player.app

import android.content.ComponentName
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import cp.player.MainActivity
import cp.player.service.MusicService
import cp.player.testing.SyntheticAudio
import cp.player.util.UserPreferences
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real app navigation and session seek, with only UUID-owned offline lyric/audio fixtures. */
@RunWith(AndroidJUnit4::class)
class LyricsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun translatedLyricsSeekWithoutStartingPausedAudioAndReturnToPlayer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val queue = context.getSharedPreferences("eplayer_queue", 0).getString("items", null)
        assumeTrue("Preserve user queue", queue == null || queue == "[]")
        val id = "lyrics-ui-${UUID.randomUUID()}"
        val title = "紙の上の旅 / 纸页里的歌"
        val hash = MessageDigest.getInstance("SHA-256").digest("$id:$id".toByteArray()).joinToString("") { "%02x".format(it) }
        val lyrics = File(context.filesDir, "lyrics/$hash.json")
        assertFalse("Only create a new fixture cache entry", lyrics.exists())
        val audio = File.createTempFile("lyrics-ui-", ".wav", context.cacheDir)
        var controller: MediaController? = null
        var ownsQueue = false
        try {
            audio.writeBytes(SyntheticAudio.wav(30))
            lyrics.parentFile!!.mkdirs()
            lyrics.writeText(Gson().toJson(LyricsText(
                "[00:00.00]晴れた空を旅しよう\n[00:12.00]紙の頁をめくるように\n[00:24.00]次の景色へ",
                "[00:00.00]沿着晴空旅行\n[00:12.00]像翻开纸页一样\n[00:24.00]去往下一幅风景")))
            controller = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java)))
                .buildAsync().get(30, TimeUnit.SECONDS)
            val current = controller
            var empty = false
            compose.runOnUiThread { empty = current.mediaItemCount == 0 }
            assumeTrue("Preserve active playback", empty)
            ownsQueue = true
            compose.runOnUiThread {
                current.pause()
                current.setMediaItem(MediaItem.Builder().setMediaId(id).setUri(Uri.fromFile(audio))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("Offline UI fixture")
                        .setExtras(Bundle().apply { putString("source", id); putString("remoteId", id); putLong("duration", 30000) }).build()).build())
                current.prepare()
            }
            compose.onNodeWithContentDescription("首页").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
            // The last match is the mini player, which opens without the home's resume action.
            compose.onAllNodesWithText(title).onLast().performClick()
            compose.onNodeWithContentDescription("歌词").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithText("沿着晴空旅行").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("沿着晴空旅行").assertIsDisplayed()
            compose.onNodeWithText("译").performClick()
            compose.onNodeWithText("沿着晴空旅行").assertDoesNotExist()
            compose.onNodeWithText("译").performClick()
            compose.onNodeWithText("紙の頁をめくるように").performScrollTo().performClick()
            compose.waitUntil(10000) {
                var positioned = false
                compose.runOnUiThread { positioned = current.currentPosition in 11900..12100 && !current.playWhenReady }
                positioned
            }
            compose.onNodeWithText("像翻开纸页一样").assertIsDisplayed()
            compose.waitForIdle()
            val folder = File(context.getExternalFilesDir(null), "layout-check").apply { mkdirs() }
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            try { File(folder, "lyrics-full-page.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
            compose.onNodeWithContentDescription("关闭歌词").performClick()
            compose.onNodeWithContentDescription("收起播放器").assertIsDisplayed()
            compose.runOnUiThread { assertFalse(current.playWhenReady); assertFalse(current.isPlaying) }
        } finally {
            try {
                controller?.let { current ->
                    try {
                        if (ownsQueue) {
                            lateinit var barrier: ListenableFuture<SessionResult>
                            compose.runOnUiThread {
                                current.stop(); current.clearMediaItems()
                                // Same-engine command joins prior service mutations without changing preferences.
                                barrier = current.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY),
                                    Bundle().apply { putInt("engine", UserPreferences.getAudioEngine(context)) })
                            }
                            assertEquals(SessionResult.RESULT_SUCCESS, barrier.get(10, TimeUnit.SECONDS).resultCode)
                        }
                    } finally { compose.runOnUiThread { current.release() } }
                }
            } finally { lyrics.delete(); audio.delete(); RecentStore.remove(context, id) }
        }
    }
}
