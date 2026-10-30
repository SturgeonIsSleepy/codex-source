package cp.player

import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaSessionManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.media.session.PlaybackState
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import cp.player.engine.FlickPlayer
import cp.player.engine.RustEngine
import cp.player.service.MusicService
import cp.player.testing.SyntheticAudio
import cp.player.util.UserPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.UUID
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeEngineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun <T> native(action: () -> T): T = runBlocking { withContext(RustEngine.engineDispatcher) { action() } }
    private fun await(timeout: Long, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < end) { if (condition()) return; SystemClock.sleep(50) }
        assertTrue("Native lifecycle state=${native { RustEngine.getState() }} progress=${native { RustEngine.getProgress() }}", condition())
    }
    @Test fun nativeLibraryInitializesWithoutUsbOwnership() = native {
        assertTrue("The packaged native audio engine must initialize on the device ABI", RustEngine.initEngine(context.applicationContext))
        try {
            assertNotEquals("Uninitialized", RustEngine.getState())
            assertFalse("ePlayer must never create an exclusive USB session", RustEngine.isRustDirectUsbSessionActive())
        } finally { RustEngine.stopEngine() }
    }

    @Test fun publicPlayerReleaseStopsNativeOutputBeforeTheFixtureEnds() {
        val queue = context.getSharedPreferences("eplayer_queue", 0).getString("items", null)
        assumeTrue("Preserve the user's queue", queue == null || queue == "[]")
        val savedEngine = UserPreferences.getAudioEngine(context)
        val controller = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java)))
            .buildAsync().get(30, TimeUnit.SECONDS)
        var direct: FlickPlayer? = null
        val file = File.createTempFile("release-contract-", ".wav", context.cacheDir).apply { writeBytes(SyntheticAudio.wav(30)) }
        fun engine(value: Int) {
            lateinit var future: ListenableFuture<SessionResult>
            main { future = controller.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", value) }) }
            assertEquals(SessionResult.RESULT_SUCCESS, future.get(10, TimeUnit.SECONDS).resultCode)
        }
        try {
            var empty = false
            main { empty = controller.mediaItemCount == 0 }
            assumeTrue("Preserve active user playback", empty)
            engine(0) // A single native owner during this direct Player contract check.
            main {
                direct = FlickPlayer(context.applicationContext)
                assertTrue("SimpleBasePlayer ignores release() unless COMMAND_RELEASE is available", direct!!.isCommandAvailable(Player.COMMAND_RELEASE))
                direct!!.volume = .15f
                direct!!.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                direct!!.prepare(); direct!!.play()
            }
            await(15000) {
                var playing = false
                main { assertNull(direct!!.playerError); playing = direct!!.isPlaying }
                playing && (native { RustEngine.getProgress()?.positionSecs } ?: 0.0) > .5
            }
            main { direct!!.release() }
            await(2000) { native { RustEngine.getState().equals("Stopped", ignoreCase = true) } }
            android.util.Log.i("EPlayerTest", "Public native Player.release stopped the 30-second fixture within two seconds")
        } finally {
            main { direct?.release() }
            native { Unit } // Join queued adapter cleanup before restoring the service-owned engine.
            try { engine(savedEngine) } finally { main { controller.release() }; file.delete() }
        }
    }

    @Test fun currentFrameworkStopStillStopsRealNativeAudio() {
        val queue = context.getSharedPreferences("eplayer_queue", 0).getString("items", null)
        assumeTrue("Preserve user playback", queue == null || queue == "[]")
        val savedEngine = UserPreferences.getAudioEngine(context)
        val id = "legacy-stop-${UUID.randomUUID()}"
        val file = File.createTempFile("legacy-stop-", ".wav", context.cacheDir).apply { writeBytes(SyntheticAudio.wav(12)) }
        var controller: MediaController? = null
        var volume = 1f
        var framework: android.media.session.MediaController? = null
        val handoverStates = ConcurrentLinkedQueue<Int>()
        val notifications = context.getSystemService(android.app.NotificationManager::class.java)
        fun mediaNotificationPresent() = notifications.activeNotifications.any {
            it.notification.extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)
        }
        val watchingNotifications = java.util.concurrent.atomic.AtomicBoolean(false)
        val missingNotifications = ConcurrentLinkedQueue<Long>()
        var notificationObserver: Thread? = null
        val observer = object : android.media.session.MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                state?.let { handoverStates.add(it.state) }
            }
        }
        fun engine(value: Int) {
            lateinit var reply: ListenableFuture<SessionResult>
            main { reply = controller!!.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", value) }) }
            assertEquals(SessionResult.RESULT_SUCCESS, reply.get(10, TimeUnit.SECONDS).resultCode)
        }
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            controller = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java)))
                .setConnectionHints(Bundle().apply { putString("eplayer.qa.owner", "native-contract") })
                .buildAsync().get(30, TimeUnit.SECONDS)
            engine(1)
            main {
                volume = controller!!.volume
                controller!!.volume = .2f
                controller!!.setMediaItem(MediaItem.Builder().setMediaId(id).setUri(Uri.fromFile(file)).build())
                controller!!.prepare(); controller!!.play()
            }
            await(15000) {
                var playing = false
                main { assertNull(controller!!.playerError); playing = controller!!.isPlaying }
                val progress = native { RustEngine.getProgress() }
                playing && progress != null && progress.positionSecs > .5 && progress.durationSecs in 11.9..12.1 &&
                    cp.player.engine.MusicPulse.sample().let { it.isFinite() && it > .01f }
            }
            val manager = context.getSystemService(MediaSessionManager::class.java)
            val legacy = try {
                instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.MEDIA_CONTENT_CONTROL")
                manager.getActiveSessions(null).firstOrNull { it.packageName == context.packageName }
            } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
            assertNotNull("The actual framework session must be available", legacy)
            framework = legacy
            legacy!!.registerCallback(observer, Handler(Looper.getMainLooper()))
            instrumentation.waitForIdleSync()
            handoverStates.clear()
            await(15000) { mediaNotificationPresent() }
            watchingNotifications.set(true)
            notificationObserver = Thread {
                while (watchingNotifications.get()) {
                    if (!mediaNotificationPresent()) missingNotifications.add(SystemClock.elapsedRealtime())
                    SystemClock.sleep(5)
                }
            }.apply { name = "ePlayer-notification-check"; isDaemon = true; start() }
            repeat(3) { engine(0); engine(1) }
            await(15000) {
                var playing = false
                main { playing = controller!!.isPlaying }
                playing && (native { RustEngine.getProgress()?.positionSecs } ?: 0.0) > .5
            }
            instrumentation.waitForIdleSync()
            watchingNotifications.set(false)
            notificationObserver?.join(2000)
            assertFalse("Notification observer did not finish", notificationObserver?.isAlive == true)
            assertTrue("Playing engine handover removed its real media notification: $missingNotifications", missingNotifications.isEmpty())
            assertFalse("Playing engine handover published an empty/stopped/paused framework session: $handoverStates",
                handoverStates.any { it == PlaybackState.STATE_NONE || it == PlaybackState.STATE_STOPPED || it == PlaybackState.STATE_PAUSED })
            // The handover fix must not ignore a genuine permanent loss to another focus owner.
            val audio = context.getSystemService(android.media.AudioManager::class.java)
            val competingFocus = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener({}, Handler(Looper.getMainLooper())).build()
            try {
                assertEquals(android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(competingFocus))
                await(2000) {
                    var paused = false
                    main { paused = !controller!!.playWhenReady }
                    paused && native { RustEngine.getState().equals("paused", true) }
                }
            } finally { audio.abandonAudioFocusRequest(competingFocus) }
            main { assertFalse("Permanent focus loss must require explicit resume", controller!!.playWhenReady); controller!!.play() }
            await(5000) {
                var playing = false
                main { playing = controller!!.isPlaying }
                playing && native { RustEngine.getState().equals("playing", true) }
            }
            legacy!!.transportControls.stop()
            await(2000) {
                var idle = false
                main { idle = controller!!.playbackState == Player.STATE_IDLE && !controller!!.playWhenReady }
                idle && native { RustEngine.getState().equals("stopped", true) }
            }
            repeat(10) {
                main { assertEquals(Player.STATE_IDLE, controller!!.playbackState); assertFalse(controller!!.playWhenReady) }
                assertTrue("Late acknowledgements must not revive an explicit Stop", native { RustEngine.getState().equals("stopped", true) })
                SystemClock.sleep(100)
            }
            android.util.Log.i("EPlayerTest", "Current framework Stop stopped real native playback; legacy control remains available")
        } finally {
            watchingNotifications.set(false)
            notificationObserver?.join(2000)
            try {
                framework?.unregisterCallback(observer)
                controller?.let { current ->
                    main { current.stop(); current.clearMediaItems(); current.volume = volume }
                    try { engine(savedEngine) } finally { main { current.release() } }
                }
            } finally {
                instrumentation.uiAutomation.dropShellPermissionIdentity()
                cp.player.core.library.RecentStore.remove(context, id)
                file.delete()
            }
        }
    }
}
