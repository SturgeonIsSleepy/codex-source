package cp.player.app

import kotlinx.coroutines.CancellationException

internal enum class RecommendationBackend(val title: String) { YTM("YTM"), NETEASE("网易云"), PERSONAL("网易云私人 FM") }

/** Only the selected backend may run. Empty results and errors never select a different algorithm. */
internal suspend fun requestRecommendations(
    backend: RecommendationBackend,
    seeds: List<Track>,
    ytm: suspend (List<Track>) -> List<Track>,
    similar: suspend (Track) -> List<Track>,
    personal: suspend () -> List<Track>,
    accept: (List<Track>) -> List<Track>
): List<Track> = when (backend) {
    RecommendationBackend.YTM -> accept(ytm(seeds))
    RecommendationBackend.PERSONAL -> accept(personal())
    RecommendationBackend.NETEASE -> {
        var failure: Exception? = null
        var completed = false
        var result = emptyList<Track>()
        for (seed in seeds) {
            try {
                val candidate = accept(similar(seed))
                completed = true
                if (candidate.isNotEmpty()) { result = candidate; break }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { failure = error }
        }
        if (!completed && failure != null) throw failure
        result
    }
}
