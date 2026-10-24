package cp.player

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.manager.WitchPlaylist
import cp.player.service.MusicService
import cp.player.util.toMediaItem
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Exercises real import validation and the CPPlayer foreground playback service on Android. */
@RunWith(AndroidJUnit4::class)
class EPlayerIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun await(timeout: Long = 30000, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        assertTrue("Condition did not become true", predicate())
    }
    @Test fun importAndMediaSessionPlayback() {
        assertEquals("ePlayer", context.getString(R.string.app_name))
        // This test replaces the active queue and default-song files. Never run it
        // against a user's real queue/imports; use a fresh test installation.
        val savedItems = context.getSharedPreferences("eplayer_queue", 0).getString("items", null)
        assumeTrue("Use a clean test queue", savedItems == null || savedItems == "[]")
        assumeTrue("Do not replace user recordings", (0..1).none { File(context.filesDir, "witch/audio-$it").exists() })
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        val fixture = File(context.cacheDir, "integration-tone.wav")
        fixture.writeBytes(cp.player.testing.SyntheticAudio.wav(45))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fixture)
        val destination = File(context.filesDir, "witch/audio-0")
        val secondDestination = File(context.filesDir, "witch/audio-1")
        var controller: MediaController? = null
        try {
            WitchPlaylist.import(context, 0, uri)
            WitchPlaylist.import(context, 1, uri)
            assertTrue(WitchPlaylist.isImported(context, 0))
            val firstRecording = WitchPlaylist.uri(context, "witch_0")
            WitchPlaylist.import(context, 0, uri)
            val replacement = WitchPlaylist.uri(context, "witch_0")
            assertNotEquals("Replacing audio must invalidate playback and animation caches", firstRecording, replacement)
            val valid = destination.readBytes()
            val invalid = File(context.cacheDir, "invalid-audio.txt").apply { writeText("not audio") }
            try {
                WitchPlaylist.import(context, 0, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", invalid))
                fail("Invalid audio must be rejected")
            } catch (_: RuntimeException) { }
            assertArrayEquals("Rejected import must preserve the previous recording", valid, destination.readBytes())
            assertEquals(replacement, WitchPlaylist.uri(context, "witch_0"))
            val token = SessionToken(context, ComponentName(context, MusicService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            controller = future.get(30, TimeUnit.SECONDS)
            val player = controller!!
            val started = java.util.concurrent.atomic.AtomicBoolean(false)
            main {
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying && player.duration > 0) started.set(true)
                    }
                })
                player.repeatMode = Player.REPEAT_MODE_ONE
                player.setMediaItems(WitchPlaylist.songs.map { it.toMediaItem(WitchPlaylist.uri(context, it.id)) })
                player.prepare()
                player.play()
            }
            await(120000) {
                main { assertNull("Playback must not fail", player.playerError) }
                started.get()
            }
            android.util.Log.i("EPlayerTest", "Audio started with a valid duration")
            await { cp.player.engine.MusicPulse.sample().let { it.isFinite() && it > .1f } }
            var initial = 0L
            main { initial = player.currentPosition }
            await(60000) { var advanced = false; main { advanced = player.currentPosition > initial }; advanced }
            main { player.pause() }
            main { player.seekTo(4000) }
            await { var sought = false; main { sought = player.currentPosition in 3900..5000 }; sought }
            main { assertFalse(player.isPlaying); player.play() }
            await { var playing = false; main { playing = player.isPlaying }; playing }
            main { player.seekToNextMediaItem() }
            await {
                var ready = false
                main { assertNull(player.playerError); ready = player.currentMediaItem?.mediaId == "witch_1" && player.isPlaying }
                ready
            }
            main { player.seekToPreviousMediaItem() }
            await {
                var ready = false
                main { assertNull(player.playerError); ready = player.currentMediaItem?.mediaId == "witch_0" && player.isPlaying }
                ready
            }
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SystemClock.sleep(1000)
            main {
                androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<MainActivity>().forEach { it.moveTaskToBack(true) }
            }
            await {
                var stopped = false
                main {
                    stopped = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                        .none { it is MainActivity }
                }
                stopped
            }
            var backgroundPosition = 0L
            main { assertTrue("Playback survives backgrounding", player.isPlaying); backgroundPosition = player.currentPosition }
            await {
                var advances = false
                main { advances = player.isPlaying && player.currentPosition > backgroundPosition + 500 }
                advances
            }
            context.startActivity(Intent(context, MainActivity::class.java).setAction("ACTION_SHOW_PLAYER").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await(60000) {
                var expanded = false
                main {
                    fun containsQueue(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
                        if (node == null) return false
                        if (node.isVisibleToUser && node.contentDescription?.toString() == "播放队列") return true
                        for (index in 0 until node.childCount) if (containsQueue(node.getChild(index))) return true
                        return false
                    }
                    expanded = containsQueue(instrumentation.uiAutomation.rootInActiveWindow)
                }
                expanded
            }
            val screenshots = File(context.getExternalFilesDir(null), "visual-check").apply { mkdirs() }
            fun capture(name: String) {
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                assertNotNull("Native screenshot is available", bitmap)
                File(screenshots, "$name.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            SystemClock.sleep(1200)
            capture("player-playing")
            main { player.pause() }
            SystemClock.sleep(1200)
            capture("player-paused")
        } finally {
            controller?.let { player -> main {
                player.stop()
                player.clearMediaItems()
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.release()
            } }
            destination.delete()
            secondDestination.delete()
            fixture.delete()
            File(context.cacheDir, "invalid-audio.txt").delete()
        }
    }
}
