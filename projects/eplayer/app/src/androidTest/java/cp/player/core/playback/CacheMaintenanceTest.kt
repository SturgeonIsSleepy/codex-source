package cp.player.core.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.testing.SyntheticAudio
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** All bytes belong to a unique fixture; never clear the user's process-owned cache. */
@RunWith(AndroidJUnit4::class)
class CacheMaintenanceTest {
    @Test fun clearingCachePreservesAnOpenReaderAndAllowsDurableNewWrites() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "cache-maintenance-${UUID.randomUUID()}").apply { mkdirs() }
        val media = File(root, "media")
        val body = SyntheticAudio.wav(1)
        val imported = File(root, "imported.audio").apply { writeBytes(body) }
        val database = StandaloneDatabaseProvider(context)
        var cache: SimpleCache? = null
        fun open() = SimpleCache(media, LeastRecentlyUsedCacheEvictor(1024 * 1024), database)
        fun write(target: SimpleCache, key: String) {
            val hole = target.startReadWrite(key, 0, body.size.toLong())
            try {
                val file = target.startFile(key, 0, body.size.toLong())
                file.writeBytes(body)
                target.commitFile(file, body.size.toLong())
            } finally { target.releaseHoleSpan(hole) }
        }
        try {
            val live = open().also { cache = it }
            write(live, "old")
            val reader = CacheDataSource.Factory().setCache(live).createDataSource()
            try {
                reader.open(DataSpec.Builder().setUri(Uri.parse("https://fixture.invalid/audio.wav"))
                    .setKey("old").setLength(body.size.toLong()).build())
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                val first = reader.read(buffer, 0, buffer.size)
                assertTrue(first > 0)
                output.write(buffer, 0, first)
                PlaybackCache.removeResources(live)
                assertEquals(0L, live.cacheSpace)
                assertTrue(live.keys.isEmpty())
                while (true) {
                    val count = reader.read(buffer, 0, buffer.size)
                    if (count == C.RESULT_END_OF_INPUT) break
                    output.write(buffer, 0, count)
                }
                assertArrayEquals("Already-open audio must remain readable during cache cleanup", body, output.toByteArray())
            } finally { reader.close() }
            assertArrayEquals("Imported audio outside the streaming cache must remain intact", body, imported.readBytes())
            write(live, "new")
            assertEquals(body.size.toLong(), live.getCachedLength("new", 0, body.size.toLong()))
            live.release()
            cache = null
            val reopened = open().also { cache = it }
            assertTrue(reopened.getCachedSpans("old").isEmpty())
            assertArrayEquals(body, reopened.getCachedSpans("new").single().file!!.readBytes())
            PlaybackCache.removeResources(reopened)
        } finally {
            cache?.release()
            database.close()
            root.deleteRecursively()
        }
    }
}
