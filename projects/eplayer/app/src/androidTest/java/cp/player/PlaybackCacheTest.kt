package cp.player

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.core.playback.PlaybackCache
import cp.player.engine.DataSourceFactoryProvider
import cp.player.testing.SyntheticAudio
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the app's complete factory path; only this test's unique cache key is removed. */
@RunWith(AndroidJUnit4::class)
class PlaybackCacheTest {
    @Test fun remoteAudioRemainsReadableFromTheSameCacheAfterUpstreamCloses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = PlaybackCache.get(context)
        val key = "cache-fixture-${UUID.randomUUID()}"
        val body = SyntheticAudio.wav(1)
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val worker = Executors.newSingleThreadExecutor()
        val response = worker.submit {
            server.accept().use { socket ->
                socket.soTimeout = 10000
                val reader = socket.getInputStream().bufferedReader()
                while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break }
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(body); flush()
                }
            }
        }
        val uri = "http://127.0.0.1:${server.localPort}/fixture.wav"
        fun read(): ByteArray {
            val source = DataSourceFactoryProvider.createCacheDataSourceFactory(context).createDataSource()
            try {
                source.open(DataSpec.Builder().setUri(uri).setKey(key).build())
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = source.read(buffer, 0, buffer.size)
                    if (count == C.RESULT_END_OF_INPUT) break
                    output.write(buffer, 0, count)
                }
                return output.toByteArray()
            } finally { source.close() }
        }
        try {
            assertArrayEquals(body, read())
            response.get(10, TimeUnit.SECONDS)
            server.close()
            assertEquals(body.size.toLong(), cache.getCachedLength(key, 0, body.size.toLong()))
            assertSame(cache, PlaybackCache.get(context))
            assertArrayEquals("A new data source must reuse cached bytes without a live upstream", body, read())
        } finally {
            server.close(); worker.shutdownNow()
            cache.removeResource(key)
        }
    }
}
