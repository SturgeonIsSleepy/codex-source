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
    private val app = context.applicationContext
    private val ncm = MusicRepository(app, source)
    companion object {
        private data class Match(val at: Long, val track: Track?)
        private val matches = linkedMapOf<String, Match>()
        private data class Alias(val at: Long, val names: List<String>)
        private val aliases = linkedMapOf<String, Alias>()
        @Synchronized private fun artistAlias(key: String) = aliases[key]?.takeIf { System.currentTimeMillis() - it.at < 24 * 60 * 60_000 }?.names
        @Synchronized private fun rememberAlias(key: String, names: List<String>) {
            aliases[key] = Alias(System.currentTimeMillis(), names)
            while (aliases.size > 192) aliases.remove(aliases.keys.first())
        }
        @Synchronized fun forgetUnmatched() { matches.entries.removeAll { it.value.track == null } }
        @Synchronized private fun cached(key: String) = matches[key]?.takeIf { System.currentTimeMillis() - it.at < 30 * 60_000 }
        @Synchronized private fun cache(key: String, track: Track?) {
            matches[key] = Match(System.currentTimeMillis(), track)
            while (matches.size > 192) matches.remove(matches.keys.first())
        }
    }
    suspend fun recommend(seeds: List<Track>): List<Track> = withContext(Dispatchers.IO) {
        // Only YTM uses the selected proxy; audio uses the independent playback route.
        YtmRadio.configure(app)
        val ytm = YtmRadio()
        val candidates = linkedMapOf<String, Recording>()
        // A small per-refill budget; names come from the catalog, never a guessed transliteration.
        val aliasBudget = java.util.concurrent.atomic.AtomicInteger(4)
        suspend fun identity(track: Track): Recording {
            val recording = track.recording()
            val names = mutableListOf<String>()
            val ids = track.song.artists.orEmpty().map { it.id }.ifEmpty { listOfNotNull(track.song.artistId) }.take(2)
            for (id in ids) {
                val key = "$source:$id"
                val saved = artistAlias(key)
                if (saved != null) names += saved
                else if (aliasBudget.getAndDecrement() > 0) {
                    try {
                        val found = ncm.artistAliases(id)
                        rememberAlias(key, found); names += found
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Optional alias lookup must not block the canonical match. */ }
                }
            }
            return recording.copy(artistAliases = (recording.artistAliases + names).distinct())
        }
        val requested = seeds.filter { it.source == source }.take(2)
        // Older saved queues only stored the first performer. Refresh native IDs before crossing catalogs.
        val verified = ncm.songs(requested.map { it.remoteId ?: it.song.id }).associateBy { it.remoteId ?: it.song.id }
        android.util.Log.i("EPlayerRoam", "stage=native-seed count=${verified.size}")
        if (verified.isEmpty()) throw RecommendationUnavailable("音源未返回种子歌曲资料，请重新点播后重试")
        var seedMatches = 0
        for (input in requested) {
            val seed = verified[input.remoteId ?: input.song.id] ?: continue
            val expected = seed.recording()
            val searched = ytm.search(expected).toMutableList()
            android.util.Log.i("EPlayerRoam", "stage=seed-search count=${searched.size}")
            var id = RecordingMatch.choose(expected, searched)
            if (id == null) {
                searched += ytm.search(expected, expected.title)
                id = RecordingMatch.choose(identity(seed), searched)
            }
            if (id == null) continue
            seedMatches++
            ytm.radio(id).filter { it.id != id }.forEach { candidates.putIfAbsent(it.id, it) }
            if (candidates.size >= 12) break
        }
        android.util.Log.i("EPlayerRoam", "stage=radio seeds=$seedMatches candidates=${candidates.size}")
        if (seedMatches == 0) throw RecommendationUnavailable("YTM 未找到对应原曲，请更换种子后重试")
        if (candidates.isEmpty()) throw RecommendationUnavailable("YTM 未返回新的推荐曲目，请稍后重试")
        val permits = Semaphore(2)
        val mapped = coroutineScope {
            candidates.values.take(12).map { expected -> async {
                permits.withPermit {
                    val key = "$source:${expected.id}:${expected.title}:${expected.artists}:${expected.durationMs}:${expected.album}"
                    cached(key)?.let { return@withPermit it.track }
                    try {
                        val found = linkedMapOf<String, Track>()
                        var chosen: Track? = null
                        val queries = listOf("${expected.title} ${expected.artists.joinToString(" ")}", expected.title).distinct()
                        for (query in queries) {
                            ncm.search(query, 1).tracks.filter { it.source == source }.forEach { found[it.remoteId ?: it.song.id] = it }
                            val id = RecordingMatch.choose(expected, found.values.map { it.recording() })
                            chosen = found[id]
                            if (chosen != null) break
                        }
                        if (chosen == null) {
                            val plausible = found.values.filter { track ->
                                kotlin.math.abs(track.song.durationMs - expected.durationMs) <= 6000 &&
                                    (RecordingMatch.text(track.song.name) == RecordingMatch.text(expected.title) ||
                                        track.song.titleAliases.orEmpty().any { RecordingMatch.text(it) == RecordingMatch.text(expected.title) })
                            }.take(3)
                            val identified = plausible.map { identity(it) }
                            val id = RecordingMatch.choose(expected, identified)
                            chosen = plausible.firstOrNull { (it.remoteId ?: it.song.id) == id }
                        }
                        cache(key, chosen)
                        chosen
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                }
            } }.awaitAll().filterNotNull().distinctBy { it.recommendationKey() }
        }
        android.util.Log.i("EPlayerRoam", "stage=native-match count=${mapped.size}")
        if (mapped.isEmpty()) throw RecommendationUnavailable("已取得 YTM 推荐，但未匹配到网易云原曲")
        mapped
    }
}
