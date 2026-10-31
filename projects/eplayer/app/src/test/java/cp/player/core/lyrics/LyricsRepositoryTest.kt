package cp.player.core.lyrics

import java.io.File
import java.security.MessageDigest
import com.google.gson.Gson
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
    private val fixedNow = 1_000_000L
    private fun repository(directory: File, load: suspend (String, String) -> LyricsText) =
        LyricsRepository(directory, { fixedNow }, load)

    @Test fun cacheSurvivesNewRepositoryWithoutCallingSource() = runBlocking {
        val directory = temporary.newFolder()
        val expected = LyricsText("[00:01]晴空", "[00:01]蓝天", fetchedAt = fixedNow)
        assertEquals(expected, repository(directory) { _, _ -> expected }.get("source", "track"))
        assertEquals(expected, repository(directory) { _, _ -> error("Source must stay offline") }
            .get("source", "track"))
    }

    @Test fun simultaneousRequestsPublishOnlyCompleteJsonAndLeaveNoHalfFiles() = runBlocking {
        val directory = temporary.newFolder()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = LyricsText("[00:01]" + "空".repeat(65536), fetchedAt = fixedNow)
        val second = LyricsText("[00:01]" + "紙".repeat(65536), fetchedAt = fixedNow)
        val a = async { repository(directory) { _, _ -> entered.complete(Unit); release.await(); first }
            .get("same", "track") }
        entered.await()
        val b = async { repository(directory) { _, _ -> second }.get("same", "track") }
        assertEquals(second, b.await())
        release.complete(Unit)
        assertEquals(first, a.await())
        val files = directory.listFiles()!!.toList()
        assertEquals(1, files.size)
        assertTrue(files.single().name.endsWith(".json"))
        assertEquals(first, repository(directory) { _, _ -> error("No refetch") }.get("same", "track"))
    }

    @Test fun cancelledLoaderCannotPublishEvenIfItSwallowsCancellation() = runBlocking {
        val directory = temporary.newFolder()
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val request = launch {
            repository(directory) { _, _ ->
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
        val expected = LyricsText("[00:01]valid", fetchedAt = fixedNow)
        repository(directory) { _, _ -> expected }.get("source", "track")
        val file = directory.listFiles()!!.single()
        for (broken in listOf("{", "null", "{}", "{\"original\":null,\"translated\":\"\"}")) {
            file.writeText(broken)
            assertEquals(expected, repository(directory) { _, _ -> expected }.get("source", "track"))
        }
        var calls = 0
        val missing = repository(directory) { _, _ -> calls++; LyricsText("暂无歌词") }
        missing.get("other", "missing"); missing.get("other", "missing")
        assertEquals(2, calls)
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test fun ttmlOnlyCacheSurvivesOfflineWithoutDiscardingRichFields() = runBlocking {
        val directory = temporary.newFolder()
        val text = LyricsText("", wordTimed = "[0,1000](0,1000,0)空", romanized = "sora",
            ttml = """<tt><body><p begin="0s" end="1s">空</p></body></tt>""",
            ttmlSource = "https://example.invalid/fixture", fetchedAt = fixedNow)
        assertEquals(text, repository(directory) { _, _ -> text }.get("fixture", "rich"))
        assertEquals(text, repository(directory) { _, _ -> error("Rich cache must work offline") }.get("fixture", "rich"))
    }

    @Test fun expiredSourceTtmlRefreshesWithTheInjectedClockAndKeepsRichTimings() = runBlocking {
        val directory = temporary.newFolder()
        var time = fixedNow
        var requests = 0
        val repository = LyricsRepository(directory, { time }) { _, _ ->
            requests++
            LyricsText("[00:00]空$requests", wordTimed = "[0,1000](0,1000,0)空$requests",
                ttml = """<tt><body><p begin="0s" end="1s">空$requests</p></body></tt>""")
        }
        val first = repository.get("fixture", "stale")
        assertEquals(fixedNow, first.fetchedAt)
        time += 8L * 24 * 60 * 60 * 1000
        val refreshed = repository.get("fixture", "stale")
        assertEquals(2, requests)
        assertEquals(time, refreshed.fetchedAt)
        assertTrue(refreshed.wordTimed.endsWith("空2"))
        assertTrue(refreshed.ttml.contains("空2"))
        assertEquals(refreshed, repository.get("fixture", "stale"))
        assertEquals(2, requests)
    }


    @Test fun freshCommunityTtmlDoesNotRefetchAnOlderSourcePayload() = runBlocking {
        val directory = temporary.newFolder()
        val time = 2_000_000_000L
        val cached = LyricsText("[00:00]原歌词", ttml = "<tt/>", fetchedAt = time - 8L * 86400000,
            communityCheckedAt = time - 86400000)
        val key = MessageDigest.getInstance("SHA-256").digest("netease-cloudmusic-rs:123".toByteArray())
            .joinToString("") { "%02x".format(it) }
        File(directory, "$key.json").writeText(Gson().toJson(cached))
        var requests = 0
        val repository = LyricsRepository(directory, { time }) { _, _ ->
            requests++
            error("Fresh community lyrics must remain usable without a source request")
        }
        assertEquals(cached, repository.get("netease-cloudmusic-rs", "123"))
        assertEquals(0, requests)
    }

}
