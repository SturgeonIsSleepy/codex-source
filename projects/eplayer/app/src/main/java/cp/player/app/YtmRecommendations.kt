package cp.player.app

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Two-way identity checks. Only the matched NCM Track ever reaches the playback queue. */
internal class YtmRecommendations(context: Context, private val source: String) {
    private val ncm = MusicRepository(context, source)
    private val ytm = YtmRadio()
    companion object {
        private data class Match(val at: Long, val track: Track?)
        private val matches = linkedMapOf<String, Match>()
        @Synchronized fun forgetUnmatched() { matches.entries.removeAll { it.value.track == null } }
        @Synchronized private fun cached(key: String) = matches[key]?.takeIf { System.currentTimeMillis() - it.at < 30 * 60_000 }
        @Synchronized private fun cache(key: String, track: Track?) {
            matches[key] = Match(System.currentTimeMillis(), track)
            while (matches.size > 192) matches.remove(matches.keys.first())
        }
    }
    suspend fun recommend(seeds: List<Track>): List<Track> = withContext(Dispatchers.IO) {
        val candidates = linkedMapOf<String, Recording>()
        val requested = seeds.filter { it.source == source }.take(2)
        // Older saved queues only stored the first performer. Refresh native IDs before crossing catalogs.
        val verified = ncm.songs(requested.map { it.remoteId ?: it.song.id }).associateBy { it.remoteId ?: it.song.id }
        for (input in requested) {
            val seed = verified[input.remoteId ?: input.song.id] ?: continue
            val expected = seed.recording()
            val id = RecordingMatch.choose(expected, ytm.search(expected)) ?: continue
            ytm.radio(id).filter { it.id != id }.forEach { candidates.putIfAbsent(it.id, it) }
            if (candidates.size >= 12) break
        }
        val permits = Semaphore(2)
        coroutineScope {
            candidates.values.take(12).map { expected -> async {
                permits.withPermit {
                    val key = "$source:${expected.id}:${expected.title}:${expected.artists}:${expected.durationMs}:${expected.album}"
                    cached(key)?.let { return@withPermit it.track }
                    try {
                        val found = ncm.search("${expected.title} ${expected.artists.joinToString(" ")}", 1).tracks.filter { it.source == source }
                        val id = RecordingMatch.choose(expected, found.map { it.recording() })
                        val chosen = found.firstOrNull { (it.remoteId ?: it.song.id) == id }
                        cache(key, chosen)
                        chosen
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                }
            } }.awaitAll().filterNotNull().distinctBy { it.recommendationKey() }
        }
    }
}
