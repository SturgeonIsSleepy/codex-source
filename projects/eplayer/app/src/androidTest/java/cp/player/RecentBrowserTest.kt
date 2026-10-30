package cp.player

import android.content.ComponentName
import android.content.Intent
import android.media.AudioManager
import android.media.browse.MediaBrowser
import android.os.Bundle
import android.service.media.MediaBrowserService
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.core.library.RecentStore
import cp.player.service.MusicService
import cp.player.testing.SyntheticAudio
import cp.player.util.UserPreferences
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The framework browser contract SystemUI uses, without playback or a source/account request. */
@RunWith(AndroidJUnit4::class)
class RecentBrowserTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    @Test fun explicitPlayCanResumeRecentAudioAfterQueueClearOnBothEngines() {
        val saved = context.getSharedPreferences("eplayer_queue", 0).getString("items", null)
        assumeTrue("Preserve user queue", saved == null || saved == "[]")
        val savedEngine = UserPreferences.getAudioEngine(context)
        val prefix = "explicit-recent-${UUID.randomUUID()}"
        val file = File.createTempFile("explicit-recent-", ".wav", context.cacheDir)
        var controller: MediaController? = null
        var ownsQueue = false
        var volume = 1f
        fun engine(value: Int) {
            lateinit var reply: com.google.common.util.concurrent.ListenableFuture<SessionResult>
            main { reply = controller!!.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", value) }) }
            assertEquals(SessionResult.RESULT_SUCCESS, reply.get(10, TimeUnit.SECONDS).resultCode)
        }
        try {
            file.writeBytes(SyntheticAudio.wav(30))
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            controller = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java)))
                .buildAsync().get(30, TimeUnit.SECONDS)
            val current = controller
            var empty = false
            main { empty = current.mediaItemCount == 0 }
            assumeTrue("Preserve active playback", empty)
            ownsQueue = true
            main { volume = current.volume; current.volume = .2f }
            for (value in 0..1) {
                engine(value)
                main {
                    current.setMediaItem(MediaItem.Builder().setMediaId("$prefix-$value").setUri(android.net.Uri.fromFile(file))
                        .setMediaMetadata(MediaMetadata.Builder().setTitle("最近播放恢复$value").build()).build())
                }
                main { current.stop(); current.clearMediaItems() }
                engine(value) // Join the clear before asking the session to resume an empty queue.
                main { assertEquals(0, current.mediaItemCount); assertFalse(current.playWhenReady); current.play() }
                val deadline = android.os.SystemClock.elapsedRealtime() + 15000
                var progressed = false
                while (android.os.SystemClock.elapsedRealtime() < deadline) {
                    main {
                        assertNull(current.playerError)
                        progressed = current.currentMediaItem?.mediaId == "$prefix-$value" && current.isPlaying &&
                            current.currentPosition > 500 && current.duration in 29000L..31000L
                    }
                    if (progressed && context.getSystemService(AudioManager::class.java).isMusicActive) break
                    android.os.SystemClock.sleep(50)
                }
                assertTrue("Explicit recent resume must produce progress on engine $value", progressed)
                assertTrue(context.getSystemService(AudioManager::class.java).isMusicActive)
                android.util.Log.i("EPlayerTest", "Explicit recent resume progressed on engine $value")
                main { current.stop(); current.clearMediaItems() }
                engine(value)
            }
        } finally {
            try {
                if (ownsQueue) {
                    main { controller?.stop(); controller?.clearMediaItems(); controller?.volume = volume }
                    engine(savedEngine)
                }
            } finally {
                main { controller?.release() }
                for (value in 0..1) RecentStore.remove(context, "$prefix-$value")
                file.delete()
            }
        }
    }

    @Test fun systemRecentBrowserCanReadPlayableMediaWithoutStartingIt() {
        val prefs = context.getSharedPreferences("eplayer_queue", 0)
        val saved = prefs.getString("items", null)
        assumeTrue("Preserve user queue", saved == null || saved == "[]")
        val component = ComponentName(context, MusicService::class.java)
        val discovered = context.packageManager.queryIntentServices(
            Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(context.packageName), 0)
        assertTrue("SystemUI must discover an exported recent-media browser service",
            discovered.any { it.serviceInfo.name == component.className && it.serviceInfo.exported })
        val id = "recent-browser-${UUID.randomUUID()}"
        val title = "最近播放契约"
        val file = File.createTempFile("recent-browser-", ".wav", context.cacheDir)
        val engine = UserPreferences.getAudioEngine(context)
        var controller: MediaController? = null
        var browser: MediaBrowser? = null
        var ownsQueue = false
        try {
            file.writeBytes(SyntheticAudio.wav(30))
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            instrumentation.waitForIdleSync()
            controller = MediaController.Builder(context, SessionToken(context, component)).buildAsync().get(30, TimeUnit.SECONDS)
            val current = controller
            var empty = false
            main { empty = current.mediaItemCount == 0 }
            assumeTrue("Preserve active playback", empty)
            ownsQueue = true
            main {
                current.setMediaItem(MediaItem.Builder().setMediaId(id).setUri(android.net.Uri.fromFile(file))
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("Offline fixture")
                        .setIsBrowsable(false).setIsPlayable(true).build()).build())
            }
            val connected = CountDownLatch(1)
            val loaded = CountDownLatch(1)
            val error = AtomicReference<String?>(null)
            val items = AtomicReference<List<MediaBrowser.MediaItem>?>(null)
            val candidate = MediaBrowser(context, component, object : MediaBrowser.ConnectionCallback() {
                override fun onConnected() { connected.countDown() }
                override fun onConnectionFailed() { error.set("Legacy recent browser connection failed"); connected.countDown() }
                override fun onConnectionSuspended() { error.set("Legacy recent browser suspended"); connected.countDown() }
            }, Bundle().apply { putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true) })
            browser = candidate
            main { candidate.connect() }
            assertTrue("Legacy recent browser did not answer", connected.await(15, TimeUnit.SECONDS))
            assertNull(error.get(), error.get())
            main {
                assertTrue(candidate.isConnected)
                candidate.subscribe(candidate.root, object : MediaBrowser.SubscriptionCallback() {
                    override fun onChildrenLoaded(parentId: String, children: MutableList<MediaBrowser.MediaItem>) {
                        items.set(children.toList()); loaded.countDown()
                    }
                    override fun onError(parentId: String) { error.set("Recent children unavailable"); loaded.countDown() }
                })
            }
            assertTrue("Legacy recent children did not answer", loaded.await(15, TimeUnit.SECONDS))
            assertNull(error.get(), error.get())
            val child = items.get()?.singleOrNull()
            assertNotNull("Recent browser must return the current playable item", child)
            assertEquals(id, child!!.mediaId)
            assertEquals(title, child.description.title.toString())
            assertTrue(child.isPlayable)
            main { assertFalse(current.playWhenReady); assertEquals(Player.STATE_IDLE, current.playbackState) }
            assertFalse("Browsing must not start an audio output", context.getSystemService(AudioManager::class.java).isMusicActive)
        } finally {
            main { browser?.disconnect() }
            controller?.let { current ->
                if (ownsQueue) {
                    lateinit var barrier: com.google.common.util.concurrent.ListenableFuture<SessionResult>
                    main {
                        current.stop(); current.clearMediaItems()
                        barrier = current.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", engine) })
                    }
                    barrier.get(10, TimeUnit.SECONDS)
                }
                main { current.release() }
            }
            RecentStore.remove(context, id)
            file.delete()
        }
    }
}
