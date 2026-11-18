package cp.player

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.core.content.FileProvider
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
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the app's complete factory path; only this test's unique cache key is removed. */
@RunWith(AndroidJUnit4::class)
class PlaybackCacheTest {
    @Test fun localFilesAndImportedContentNeverInitializeTheStreamingCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val body = SyntheticAudio.wav(1)
        val file = File.createTempFile("local-cache-bypass-", ".wav", context.cacheDir).apply { writeBytes(body) }
        val factory = DataSourceFactoryProvider.createCacheDataSourceFactory(context) {
            throw AssertionError("Local audio must not open or scan the streaming cache")
        }
        try {
            for (uri in listOf(android.net.Uri.fromFile(file), FileProvider.getUriForFile(context,
                "${context.packageName}.fileprovider", file))) {
                val source = factory.createDataSource()
                try {
                    source.open(DataSpec.Builder().setUri(uri).build())
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = source.read(buffer, 0, buffer.size)
                        if (count == C.RESULT_END_OF_INPUT) break
                        output.write(buffer, 0, count)
                    }
                    assertArrayEquals(body, output.toByteArray())
                } finally { source.close() }
            }
        } finally { file.delete() }
    }

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
        var cacheRequests = 0
        val factory = DataSourceFactoryProvider.createCacheDataSourceFactory(context) { cacheRequests++; cache }
        assertEquals("Creating a factory must not touch disk cache", 0, cacheRequests)
        fun read(): ByteArray {
            val before = cacheRequests
            val source = factory.createDataSource()
            assertEquals("Creating an unopened source must not scan cache", before, cacheRequests)
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
            assertEquals(2, cacheRequests)
        } finally {
            server.close(); worker.shutdownNow()
            cache.removeResource(key)
        }
    }
    @Test fun changedQualityCannotSpliceOldByteRangesAndRequiresANewExtractor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cache = PlaybackCache.get(context)
        val id = "representation-${UUID.randomUUID()}"
        val uri = android.net.Uri.parse("cp://$id?source=cache-fixture&quality=lossless")
        val a = ByteArray(4096) { 31 }
        val b = ByteArray(6144) { 71 }
        var body = a
        var resource = cp.player.core.playback.AudioResource("https://fixture.test/audio", "first", a.size.toLong(), 1200)
        val factory = androidx.media3.datasource.DataSource.Factory { androidx.media3.datasource.ByteArrayDataSource(body) }
        fun source() = cp.player.service.BackendDataSource(context, factory, { cache }) { _, _, _, _ -> resource }
        fun read(ds: androidx.media3.datasource.DataSource, position: Long = 0, length: Long = C.LENGTH_UNSET.toLong()): ByteArray {
            try {
                ds.open(DataSpec.Builder().setUri(uri).setPosition(position).setLength(length).build())
                val out = ByteArrayOutputStream(); val buffer = ByteArray(511)
                while (true) { val n = ds.read(buffer, 0, buffer.size); if (n == -1) break; out.write(buffer, 0, n) }
                return out.toByteArray()
            } finally { ds.close() }
        }
        val keyA = resource.cacheKey("cache-fixture", id, "lossless")
        val first = source()
        try {
            assertArrayEquals(a.copyOf(1024), read(first, length = 1024))
            body = b
            resource = resource.copy(identity = "second", size = b.size.toLong())
            try { read(first, position = 1024); fail("An existing extractor must not consume changed audio bytes") }
            catch (_: cp.player.core.playback.AudioRepresentationChanged) { }
            assertArrayEquals("A fresh source must get only the new representation", b, read(source()))
            assertEquals(1024L, cache.getCachedLength(keyA, 0, a.size.toLong()))
        } finally {
            cache.removeResource(keyA)
            cache.removeResource(resource.cacheKey("cache-fixture", id, "lossless"))
        }
    }

}
