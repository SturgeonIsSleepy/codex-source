package cp.player.app

internal fun Track.recommendationKey() = "$source:${remoteId ?: song.id}"

/** Session history survives queue trimming; recommendation seeds keep the original playlist's context. */
internal class RoamPlanner(val source: String, anchors: List<Track>, private val original: Set<String> = emptySet()) {
    private val anchors = anchors.filter { it.source == source }.distinctBy { it.recommendationKey() }.take(16)
    private val heard = linkedSetOf<String>()
    private var cursor = 0
    fun remember(tracks: List<Track>) {
        tracks.filter { it.source == source }.forEach { heard.add(it.recommendationKey()) }
        while (heard.size > 1000) heard.remove(heard.first())
    }
    fun seeds(current: Track?): List<Track> {
        val rotated = if (anchors.isEmpty()) emptyList() else List(minOf(3, anchors.size)) { anchors[(cursor + it) % anchors.size] }
        cursor++
        return (rotated + listOfNotNull(current)).filter { it.source == source }.distinctBy { it.recommendationKey() }.take(3)
    }
    fun fresh(candidates: List<Track>, excluded: Set<String>, queue: List<Track>, limit: Int = 16): List<Track> =
        diverseTracks(candidates.filter { it.source == source && it.recommendationKey() !in original && it.recommendationKey() !in heard && it.recommendationKey() !in excluded },
            queue.map { it.recommendationKey() }.toSet(), limit)
}

internal fun diverseTracks(candidates: List<Track>, excluded: Set<String> = emptySet(), limit: Int = 16): List<Track> {
    val pool = candidates.distinctBy { it.recommendationKey() }.filter { it.recommendationKey() !in excluded }.toMutableList()
    val result = mutableListOf<Track>()
    val artists = mutableMapOf<String, Int>()
    while (pool.isNotEmpty() && result.size < limit) {
        val selected = pool.indices.minByOrNull { index ->
            val artist = RecordingMatch.text(pool[index].song.artist)
            index + (artists[artist] ?: 0) * 12 + if (result.lastOrNull()?.song?.artist == pool[index].song.artist) 30 else 0
        } ?: break
        val track = pool.removeAt(selected)
        result += track
        val artist = RecordingMatch.text(track.song.artist)
        artists[artist] = (artists[artist] ?: 0) + 1
    }
    return result
}
