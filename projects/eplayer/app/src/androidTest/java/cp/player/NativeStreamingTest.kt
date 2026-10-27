package cp.player

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import cp.player.app.RecentStore
import cp.player.core.playback.PlaybackCache
import cp.player.engine.RustEngine
import cp.player.model.Song
import cp.player.service.MusicService
import cp.player.testing.SyntheticAudio
import cp.player.util.UserPreferences
import cp.player.util.toMediaItem
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Delayed loopback bytes exercise real buffering without an account or a recording. */
@RunWith(AndroidJUnit4::class)
class NativeStreamingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun main(action: () -> Unit) {
        var error: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (failure: Throwable) { error = failure } }
        error?.let { throw it }
    }
    private fun nativeState() = runBlocking { withContext(RustEngine.engineDispatcher) { RustEngine.getState() } }
    private fun await(check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < end) { if (check()) return; SystemClock.sleep(100) }
        assertTrue("Streaming state did not settle; native=${nativeState()}", check())
    }

    @Test fun pauseDuringPrebufferSurvivesDownloadCompletionAndResumeUsesCachedBytes() {
        val queue = context.getSharedPreferences("eplayer_queue", 0).getString("items", null)
        assumeTrue("Preserve existing playback", queue == null || queue == "[]")
        val savedEngine = UserPreferences.getAudioEngine(context)
        val id = "stream-pause-${UUID.randomUUID()}"
        val body = SyntheticAudio.wav(12)
        val previousFiles = context.cacheDir.listFiles().orEmpty().map { it.name }.toSet()
        val firstChunk = CountDownLatch(1)
        val finishBody = CountDownLatch(1)
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val uri = android.net.Uri.parse("http://127.0.0.1:${server.localPort}/$id.wav")
        val worker = Executors.newSingleThreadExecutor()
        val response = worker.submit {
            server.accept().use { socket ->
                socket.soTimeout = 10000
                val reader = socket.getInputStream().bufferedReader()
                while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break }
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(body, 0, 96 * 1024); flush() // Deliberately below the 240KiB prebuffer.
                    firstChunk.countDown()
                    check(finishBody.await(30, TimeUnit.SECONDS)) { "Fixture release timed out" }
                    write(body, 96 * 1024, body.size - 96 * 1024); flush()
                }
            }
        }
        var controller: MediaController? = null
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            controller = MediaController.Builder(context, SessionToken(context,
                ComponentName(context, MusicService::class.java)))
                .setConnectionHints(Bundle().apply { putString("eplayer.qa.owner", "stream") }).buildAsync().get(30, TimeUnit.SECONDS)
            val current = controller!!
            lateinit var switch: ListenableFuture<SessionResult>
            main { switch = current.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", 1) }) }
            assertEquals(SessionResult.RESULT_SUCCESS, switch.get(10, TimeUnit.SECONDS).resultCode)
            main {
                current.setMediaItem(Song(id, "缓冲暂停测试", "Synthetic fixture", album = "Test", durationMs = 12000).toMediaItem(uri))
                current.prepare(); current.play()
            }
            assertTrue("The real remote request must reach the delayed fixture", firstChunk.await(15, TimeUnit.SECONDS))
            main { current.pause() }
            instrumentation.waitForIdleSync() // Flush the controller batch before releasing more bytes.
            await { var paused = false; main { paused = !current.playWhenReady }; paused && !nativeState().equals("playing", true) }
            finishBody.countDown()
            response.get(15, TimeUnit.SECONDS)
            val cache = PlaybackCache.get(context)
            await { cache.isCached(uri.toString(), 0, body.size.toLong()) }
            server.close() // Subsequent resume must work from the completed cache.
            repeat(10) {
                assertFalse("Prebuffer completion must not start native audio after pause", nativeState().equals("playing", true))
                main { assertFalse(current.playWhenReady); assertFalse(current.isPlaying) }
                SystemClock.sleep(100)
            }
            main { current.play() }
            await {
                var playing = false
                main { assertNull(current.playerError); playing = current.isPlaying && current.currentPosition > 500 }
                val progress = runBlocking { withContext(RustEngine.engineDispatcher) { RustEngine.getProgress() } }
                playing && nativeState().equals("playing", true) && progress != null &&
                    progress.positionSecs > .5 && progress.durationSecs in 11.9..12.1 &&
                    cp.player.engine.MusicPulse.sample().let { it.isFinite() && it > .01f }
            }
            android.util.Log.i("EPlayerTest", "Native stream stayed paused after prebuffer; resumed with upstream closed")
        } finally {
            finishBody.countDown(); server.close(); worker.shutdownNow()
            controller?.let { current ->
                lateinit var restore: ListenableFuture<SessionResult>
                main {
                    current.stop(); current.clearMediaItems()
                    restore = current.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", savedEngine) })
                }
                try { assertEquals(SessionResult.RESULT_SUCCESS, restore.get(10, TimeUnit.SECONDS).resultCode) }
                finally { main { current.release() } }
            }
            PlaybackCache.get(context).removeResource(uri.toString())
            context.cacheDir.listFiles().orEmpty().filter {
                it.name.startsWith("stream_cache_") && it.name !in previousFiles && it.length() == body.size.toLong()
            }.forEach { file -> if (file.readBytes().contentEquals(body)) file.delete() }
            RecentStore.remove(context, id)
        }
    }
}
