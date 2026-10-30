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
import androidx.media3.common.Player
import android.net.Uri
import java.io.File
import cp.player.core.library.RecentStore
import cp.player.core.playback.PlaybackCache
import cp.player.engine.RustEngine
import cp.player.model.Song
import cp.player.service.MusicService
import cp.player.testing.DelayedAudioServer
import cp.player.testing.SyntheticAudio
import cp.player.util.UserPreferences
import cp.player.util.toMediaItem
import java.util.UUID
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

    private fun withNativePlayer(action: (MediaController) -> Unit) {
        val queue = context.getSharedPreferences("eplayer_queue", 0).getString("items", null)
        assumeTrue("Preserve existing playback", queue == null || queue == "[]")
        val savedEngine = UserPreferences.getAudioEngine(context)
        var controller: MediaController? = null
        var volume = 1f
        var ownsPlayback = false
        try {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            controller = MediaController.Builder(context, SessionToken(context,
                ComponentName(context, MusicService::class.java)))
                .setConnectionHints(Bundle().apply { putString("eplayer.qa.owner", "stream") }).buildAsync().get(30, TimeUnit.SECONDS)
            val current = controller
            var empty = false
            main { empty = current.mediaItemCount == 0; volume = current.volume }
            assumeTrue("Preserve active user playback", empty)
            ownsPlayback = true
            lateinit var switch: ListenableFuture<SessionResult>
            main { switch = current.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", 1) }) }
            assertEquals(SessionResult.RESULT_SUCCESS, switch.get(10, TimeUnit.SECONDS).resultCode)
            main { current.volume = .2f }
            action(current)
        } finally {
            controller?.let { current ->
                if (!ownsPlayback) { main { current.release() }; return@let }
                lateinit var restore: ListenableFuture<SessionResult>
                main {
                    current.stop(); current.clearMediaItems(); current.volume = volume
                    restore = current.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", savedEngine) })
                }
                try { assertEquals(SessionResult.RESULT_SUCCESS, restore.get(10, TimeUnit.SECONDS).resultCode) }
                finally { main { current.release() } }
            }
        }
    }

    @Test fun pauseDuringPrebufferSurvivesDownloadCompletionAndResumeUsesCachedBytes() {
        val id = "stream-pause-${UUID.randomUUID()}"
        val body = SyntheticAudio.wav(12)
        val previousFiles = context.cacheDir.listFiles().orEmpty().map { it.name }.toSet()
        val server = DelayedAudioServer(id, body)
        val uri = server.uri
        try { withNativePlayer { current ->
            main {
                current.setMediaItem(Song(id, "缓冲暂停测试", "Synthetic fixture", album = "Test", durationMs = 12000).toMediaItem(uri))
                current.prepare(); current.play()
            }
            assertTrue("The real remote request must reach the delayed fixture", server.firstChunk.await(15, TimeUnit.SECONDS))
            main { current.pause() }
            instrumentation.waitForIdleSync() // Flush the controller batch before releasing more bytes.
            await { var paused = false; main { paused = !current.playWhenReady }; paused && !nativeState().equals("playing", true) }
            server.finish()
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
        } } finally {
            server.close()
            PlaybackCache.get(context).removeResource(uri.toString())
            context.cacheDir.listFiles().orEmpty().filter {
                it.name.startsWith("stream_cache_") && it.name !in previousFiles && it.length() == body.size.toLong()
            }.forEach { file -> if (file.readBytes().contentEquals(body)) file.delete() }
            RecentStore.remove(context, id)
        }
    }

    @Test fun switchingFromPrebufferToLocalTrackCannotBeOverwrittenByOldDownload() {
        val remoteId = "stream-replace-${UUID.randomUUID()}"
        val localId = "local-replacement-${UUID.randomUUID()}"
        val body = SyntheticAudio.wav(12)
        val local = File.createTempFile("stream-local-", ".wav", context.cacheDir).apply { writeBytes(SyntheticAudio.wav(30)) }
        val previousFiles = context.cacheDir.listFiles().orEmpty().map { it.name }.toSet()
        val server = DelayedAudioServer(remoteId, body, allowClientCancellation = true)
        try { withNativePlayer { current ->
            main {
                current.setMediaItem(Song(remoteId, "旧下载", "Synthetic fixture", album = "Test", durationMs = 12000).toMediaItem(server.uri))
                current.prepare(); current.play()
            }
            assertTrue("The old transfer must be blocked below prebuffer", server.firstChunk.await(15, TimeUnit.SECONDS))
            main {
                current.setMediaItem(Song(localId, "本地替代", "Synthetic fixture", album = "Test", durationMs = 30000).toMediaItem(Uri.fromFile(local)))
                current.prepare(); current.play()
            }
            fun localIsAudible(): Boolean {
                var playing = false
                main {
                    assertNull(current.playerError)
                    playing = current.currentMediaItem?.mediaId == localId && current.playWhenReady &&
                        current.isPlaying && current.playbackState == Player.STATE_READY
                }
                val progress = runBlocking { withContext(RustEngine.engineDispatcher) { RustEngine.getProgress() } }
                return playing && progress != null && progress.positionSecs > .5 && progress.durationSecs in 29.9..30.1 &&
                    cp.player.engine.MusicPulse.sample().let { it.isFinite() && it > .01f }
            }
            await { localIsAudible() }
            server.finish() // Let the stale HTTP transfer finish only after local audio is real.
            repeat(20) {
                assertTrue("Old download changed the replacement player/native output", localIsAudible())
                SystemClock.sleep(100)
            }
            android.util.Log.i("EPlayerTest", "Old prebuffer transfer cannot replace 30-second local PCM or set its player IDLE")
        } } finally {
            server.close()
            PlaybackCache.get(context).removeResource(server.uri.toString())
            context.cacheDir.listFiles().orEmpty().filter {
                it.name.startsWith("stream_cache_") && it.name !in previousFiles && it.length() == body.size.toLong()
            }.forEach { if (it.readBytes().contentEquals(body)) it.delete() }
            local.delete()
            RecentStore.remove(context, remoteId); RecentStore.remove(context, localId)
        }
    }

    @Test fun stopDuringPrebufferCannotBecomeReadyWhenOldResponseCompletes() {
        val id = "stream-stop-${UUID.randomUUID()}"
        val body = SyntheticAudio.wav(12)
        val previousFiles = context.cacheDir.listFiles().orEmpty().map { it.name }.toSet()
        val server = DelayedAudioServer(id, body, allowClientCancellation = true)
        try { withNativePlayer { current ->
            main {
                current.setMediaItem(Song(id, "缓冲停止", "Synthetic fixture", album = "Test", durationMs = 12000).toMediaItem(server.uri))
                current.prepare(); current.play()
            }
            assertTrue(server.firstChunk.await(15, TimeUnit.SECONDS))
            main { current.stop() }
            await {
                var stopped = false
                main { stopped = current.playbackState == Player.STATE_IDLE && !current.playWhenReady }
                stopped && nativeState().equals("stopped", true)
            }
            server.finish()
            repeat(10) {
                main {
                    assertEquals("Completed cancelled transfer must not turn Stop into READY", Player.STATE_IDLE, current.playbackState)
                    assertFalse(current.playWhenReady); assertFalse(current.isPlaying)
                }
                assertTrue(nativeState().equals("stopped", true))
                SystemClock.sleep(100)
            }
        } } finally {
            server.close()
            PlaybackCache.get(context).removeResource(server.uri.toString())
            context.cacheDir.listFiles().orEmpty().filter {
                it.name.startsWith("stream_cache_") && it.name !in previousFiles && it.length() == body.size.toLong()
            }.forEach { if (it.readBytes().contentEquals(body)) it.delete() }
            RecentStore.remove(context, id)
        }
    }

}
