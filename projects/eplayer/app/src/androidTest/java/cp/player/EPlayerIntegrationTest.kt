package cp.player

import android.content.ComponentName
import android.content.Intent
import android.os.SystemClock
import android.os.Bundle
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import cp.player.core.playback.PlaybackSleepTimer
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
    private fun capture(name: String) {
        val folder = File(context.getExternalFilesDir(null), "visual-check").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull("Native screenshot is available", bitmap)
        File(folder, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    private fun openFromNotification(title: String) {
        assertTrue("Notification shade opens", instrumentation.uiAutomation.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS))
        await(15000) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root?.packageName?.toString() != "com.android.systemui") false
            else {
                val label = root.findAccessibilityNodeInfosByText(title).firstOrNull { it.isVisibleToUser }
                var target = label
                while (target != null && !target.isClickable) target = target.parent
                if (target == null) false else {
                    capture("notification-controls")
                    target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
        }
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
        val savedEngine = cp.player.util.UserPreferences.getAudioEngine(context)
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
            fun engine(value: Int) {
                lateinit var reply: ListenableFuture<SessionResult>
                main { reply = player.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", value) }) }
                assertEquals(SessionResult.RESULT_SUCCESS, reply.get(10, TimeUnit.SECONDS).resultCode)
            }
            engine(0)
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
            fun timer(duration: Long): Int {
                lateinit var reply: ListenableFuture<SessionResult>
                main { reply = player.sendCustomCommand(SessionCommand(PlaybackSleepTimer.COMMAND, Bundle.EMPTY),
                    Bundle().apply { putLong(PlaybackSleepTimer.DURATION_MS, duration) }) }
                return reply.get(10, TimeUnit.SECONDS).resultCode
            }
            assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE, timer(-1))
            assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE, timer(PlaybackSleepTimer.MAX_DURATION_MS + 1))
            assertEquals(SessionResult.RESULT_SUCCESS, timer(60000))
            await { var active = false; main { active = player.sessionExtras.getLong(PlaybackSleepTimer.DEADLINE) > SystemClock.elapsedRealtime() }; active }
            assertEquals(SessionResult.RESULT_SUCCESS, timer(0))
            await { var cancelled = false; main { cancelled = player.sessionExtras.getLong(PlaybackSleepTimer.DEADLINE) == 0L }; cancelled }
            assertEquals(SessionResult.RESULT_SUCCESS, timer(1200))
            await { var paused = false; main { paused = !player.playWhenReady && player.sessionExtras.getLong(PlaybackSleepTimer.DEADLINE) == 0L }; paused }
            main { assertFalse("Service timer pauses while the activity is backgrounded", player.isPlaying); player.play() }
            await { var playing = false; main { playing = player.isPlaying }; playing }
            main { assertNotNull("MediaSession exposes the notification return intent", player.sessionActivity) }
            openFromNotification(WitchPlaylist.songs[0].name)
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
            SystemClock.sleep(1200)
            capture("player-playing")
            main { player.pause() }
            SystemClock.sleep(1200)
            capture("player-paused")
            // Exercise the packaged Rust backend and real session replacement, not only JNI init.
            assertEquals(SessionResult.RESULT_SUCCESS, timer(180000))
            main { player.seekTo(12000) }
            await { var sought = false; main { sought = player.currentPosition in 11900..13000 }; sought }
            engine(1)
            await { var retained = false; main { retained = !player.playWhenReady && player.currentPosition in 11900..13000 }; retained }
            main { player.play() }
            await(90000) {
                var playing = false
                main { assertNull("Rust playback must not fail", player.playerError)
                    playing = player.isPlaying && player.duration > 0 && player.currentMediaItem?.mediaId == "witch_0" }
                playing
            }
            await(7000) {
                // A from-zero restart cannot reach 12 seconds inside this window.
                cp.player.engine.RustEngine.getProgress()?.positionSecs?.let { it in 11.8..20.0 } == true
            }
            main {
                assertEquals(2, player.mediaItemCount)
                assertTrue("Engine switch must retain the service sleep timer", player.sessionExtras.getLong(PlaybackSleepTimer.DEADLINE) > SystemClock.elapsedRealtime())
                player.pause(); player.seekTo(2000)
            }
            await { var sought = false; main { sought = !player.playWhenReady && player.currentPosition in 1900..3000 }; sought }
            await { cp.player.engine.RustEngine.getState().equals("paused", ignoreCase = true) }
            main { player.seekTo(1, 6000) }
            await { var parked = false; main { parked = !player.playWhenReady && player.currentMediaItem?.mediaId == "witch_1" && player.currentPosition in 5900..7000 }; parked }
            main { player.removeMediaItem(1) }
            await { var parked = false; main { parked = !player.playWhenReady && player.currentMediaItem?.mediaId == "witch_0" && player.currentPosition == 0L }; parked }
            engine(0)
            main { player.play() }
            await { var playing = false; main { assertNull(player.playerError); playing = player.isPlaying }; playing }
            assertEquals(SessionResult.RESULT_SUCCESS, timer(0))
            android.util.Log.i("EPlayerTest", "Rust playback/seek/pause and return to ExoPlayer passed")
        } finally {
            controller?.let { player -> main {
                player.sendCustomCommand(SessionCommand(PlaybackSleepTimer.COMMAND, Bundle.EMPTY),
                    Bundle().apply { putLong(PlaybackSleepTimer.DURATION_MS, 0) })
                player.stop()
                player.clearMediaItems()
                player.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", savedEngine) })
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
