package cp.player.core.lyrics

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise actual Android filesystem publication without a source module or user cache entry. */
@RunWith(AndroidJUnit4::class)
class LyricsCacheTest {
    @Test fun atomicCacheCanBeReadOfflineAndMalformedDataIsReplaced() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "lyrics-core-${UUID.randomUUID()}")
        assertFalse(directory.exists())
        try {
            val now = 1_000_000L
            val text = LyricsText("[00:01.50]晴れた空", "[00:01.50]晴朗天空", fetchedAt = now)
            assertEquals(text, LyricsRepository(directory, { now }) { _, _ -> text }.get("fixture", "track"))
            assertEquals(text, LyricsRepository(directory, { now }) { _, _ -> error("Offline cache must not fetch") }
                .get("fixture", "track"))
            val file = directory.listFiles()!!.single()
            file.writeText("{}")
            assertEquals(text, LyricsRepository(directory, { now }) { _, _ -> text }.get("fixture", "track"))
            assertEquals(1, directory.listFiles()!!.size)
            assertEquals(listOf(TimedLine(1500, "晴れた空")), LyricParser.parse(text.original))
        } finally {
            directory.deleteRecursively() // Only the UUID-owned fixture directory.
        }
    }
}
