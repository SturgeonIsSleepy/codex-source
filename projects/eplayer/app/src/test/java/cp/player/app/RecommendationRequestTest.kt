package cp.player.app

import cp.player.model.Song
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecommendationRequestTest {
    private val seed = Track(Song("1", "seed", "artist", album = "album"), source = "ncm")
    @Test fun emptyOrRejectedYtmResultsNeverCallNativeRecommendations() = runBlocking {
        for (raw in listOf(emptyList(), listOf(seed))) {
            var native = 0
            val result = requestRecommendations(RecommendationBackend.YTM, listOf(seed), { raw },
                { native++; listOf(seed) }, { native++; listOf(seed) }, { emptyList() })
            assertTrue(result.isEmpty()); assertEquals(0, native)
        }
    }
    @Test fun ytmExceptionAndCancellationPropagateWithoutChangingBackend() = runBlocking {
        for (error in listOf(IOException("unavailable"), CancellationException("cancel"))) {
            var native = 0
            try {
                requestRecommendations(RecommendationBackend.YTM, listOf(seed), { throw error },
                    { native++; listOf(seed) }, { native++; listOf(seed) }, { it })
                fail("Failure must remain a failure")
            } catch (actual: Exception) { assertSame(error, actual) }
            assertEquals(0, native)
        }
    }
    @Test fun exhaustedNativeSeedsDoNotSilentlyStartPersonalFm() = runBlocking {
        var similar = 0; var fm = 0
        val result = requestRecommendations(RecommendationBackend.NETEASE, listOf(seed, seed.copy(remoteId = "2")),
            { error("YTM must not run") }, { similar++; emptyList() }, { fm++; listOf(seed) }, { it })
        assertTrue(result.isEmpty()); assertEquals(2, similar); assertEquals(0, fm)
    }
    @Test fun nativeCanTryAnotherOriginalSeedWithinTheChosenAlgorithm() = runBlocking {
        var calls = 0
        val result = requestRecommendations(RecommendationBackend.NETEASE, listOf(seed, seed.copy(remoteId = "2")),
            { error("YTM must not run") }, { if (++calls == 1) throw IOException("seed failed") else listOf(seed) },
            { error("FM must not run") }, { it })
        assertEquals(listOf(seed), result); assertEquals(2, calls)
    }
    @Test fun personalFmRunsOnlyWhenExplicitlySelected() = runBlocking {
        var calls = 0
        val result = requestRecommendations(RecommendationBackend.PERSONAL, listOf(seed),
            { error("YTM must not run") }, { error("Similar must not run") }, { calls++; listOf(seed) }, { it })
        assertEquals(listOf(seed), result); assertEquals(1, calls)
    }
    @Test fun cancellationStopsNativeSeedIteration() = runBlocking {
        var calls = 0
        try {
            requestRecommendations(RecommendationBackend.NETEASE, listOf(seed, seed), { emptyList() },
                { calls++; throw CancellationException("stop") }, { error("FM must not run") }, { it })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }
}
