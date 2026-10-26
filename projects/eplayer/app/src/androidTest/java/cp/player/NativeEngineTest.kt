package cp.player

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
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
}
