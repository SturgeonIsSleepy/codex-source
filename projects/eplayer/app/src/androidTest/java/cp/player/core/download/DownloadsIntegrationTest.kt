package cp.player.core.download

import cp.player.core.library.Track
import cp.player.core.library.LibraryStore

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.model.Song
import cp.player.testing.SyntheticAudio
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import kotlin.concurrent.thread

/** Uses Android DownloadManager and a loopback fixture, with no platform account or recording. */
@RunWith(AndroidJUnit4::class)
class DownloadsIntegrationTest {
    @Test fun failedTransferCanRetryAndCommitPlayableLocalAudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("eplayer_downloads", 0)
        val previous = prefs.getString("jobs", null)
        assumeTrue("Run on a clean test queue; never consume a user's existing jobs", previous == null || previous == "[]")
        val library = LibraryStore(context)
        val downloads = Downloads(context, library)
        val remoteId = "download-fixture-${UUID.randomUUID()}"
        val sourceId = "loopback-download-test"
        val track = Track(Song(remoteId, "旅途下载测试", "Synthetic fixture", album = "Test"), source = sourceId)
        val key = "$sourceId:$remoteId"
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val wav = SyntheticAudio.wav(1)
        val worker = thread(name = "ePlayer-Download-Fixture", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                        val path = reader.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                        while (!reader.readLine().isNullOrEmpty()) { }
                        val good = path == "/tone.wav"
                        val body = if (good) wav else ByteArray(0)
                        socket.getOutputStream().use { output ->
                            output.write(("HTTP/1.1 ${if (good) "200 OK" else "404 Not Found"}\r\n" +
                                "Content-Type: audio/wav\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                            output.write(body)
                        }
                    }
                } catch (_: Exception) { if (server.isClosed) break }
            }
        }
        fun awaitResult(message: String, action: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 60000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (action()) return
                SystemClock.sleep(200)
            }
            assertTrue(message, action())
        }
        try {
            downloads.enqueue(track, "http://127.0.0.1:${server.localPort}/missing.wav")
            awaitResult("A failed system transfer must become a retryable record") {
                downloads.reconcile().first.any { it.record.key == key && it.record.error != null }
            }
            downloads.enqueue(track, "http://127.0.0.1:${server.localPort}/tone.wav")
            awaitResult("A retry must commit downloaded audio into the local library") {
                downloads.reconcile()
                library.imported().any { it.source == sourceId && it.remoteId == remoteId }
            }
            val imported = library.imported().single { it.source == sourceId && it.remoteId == remoteId }
            assertEquals("旅途下载测试", imported.song.name)
            assertTrue(imported.song.durationMs > 0)
            context.contentResolver.openInputStream(android.net.Uri.parse(imported.uri)).use { input ->
                assertArrayEquals("Saved audio must survive removal of the system download", wav, input!!.readBytes())
            }
            assertTrue(downloads.reconcile().first.none { it.record.key == key })
            // Cancellation removes only this owned job and cannot add a second library item.
            downloads.enqueue(track, "http://127.0.0.1:${server.localPort}/tone.wav")
            downloads.cancel(key)
            assertTrue(downloads.reconcile().first.none { it.record.key == key })
            assertEquals(1, library.imported().count { it.source == sourceId && it.remoteId == remoteId })
        } finally {
            downloads.cancel(key)
            library.imported().filter { it.source == sourceId && it.remoteId == remoteId }.forEach(library::removeImported)
            if (previous == null) prefs.edit().remove("jobs").commit() else prefs.edit().putString("jobs", previous).commit()
            server.close(); worker.join(2000)
        }
    }
}
