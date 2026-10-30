package cp.player.core.lyrics

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun cacheSurvivesNewRepositoryWithoutCallingSource() = runBlocking {
        val directory = temporary.newFolder()
        val expected = LyricsText("[00:01]晴空", "[00:01]蓝天")
        assertEquals(expected, LyricsRepository(directory) { _, _ -> expected }.get("source", "track"))
        assertEquals(expected, LyricsRepository(directory) { _, _ -> error("Source must stay offline") }
            .get("source", "track"))
    }

    @Test fun simultaneousRequestsPublishOnlyCompleteJsonAndLeaveNoHalfFiles() = runBlocking {
        val directory = temporary.newFolder()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = LyricsText("[00:01]" + "空".repeat(65536))
        val second = LyricsText("[00:01]" + "紙".repeat(65536))
        val a = async { LyricsRepository(directory) { _, _ -> entered.complete(Unit); release.await(); first }
            .get("same", "track") }
        entered.await()
        val b = async { LyricsRepository(directory) { _, _ -> second }.get("same", "track") }
        assertEquals(second, b.await())
        release.complete(Unit)
        assertEquals(first, a.await())
        val files = directory.listFiles()!!.toList()
        assertEquals(1, files.size)
        assertTrue(files.single().name.endsWith(".json"))
        assertEquals(first, LyricsRepository(directory) { _, _ -> error("No refetch") }.get("same", "track"))
    }

    @Test fun cancelledLoaderCannotPublishEvenIfItSwallowsCancellation() = runBlocking {
        val directory = temporary.newFolder()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val request = launch {
            LyricsRepository(directory) { _, _ ->
                entered.complete(Unit)
                try { gate.await() } catch (_: CancellationException) { }
                LyricsText("[00:01]cancelled")
            }.get("source", "track")
        }
        entered.await()
        request.cancelAndJoin()
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun malformedCacheRefetchesAndUnavailableLyricsAreNotCached() = runBlocking {
        val directory = temporary.newFolder()
        val expected = LyricsText("[00:01]valid")
        LyricsRepository(directory) { _, _ -> expected }.get("source", "track")
        val file = directory.listFiles()!!.single()
        for (broken in listOf("{", "null", "{}", "{\"original\":null,\"translated\":\"\"}")) {
            file.writeText(broken)
            assertEquals(expected, LyricsRepository(directory) { _, _ -> expected }.get("source", "track"))
        }
        var calls = 0
        val missing = LyricsRepository(directory) { _, _ -> calls++; LyricsText("暂无歌词") }
        missing.get("other", "missing"); missing.get("other", "missing")
        assertEquals(2, calls)
        assertEquals(1, directory.listFiles()!!.size)
    }
}
