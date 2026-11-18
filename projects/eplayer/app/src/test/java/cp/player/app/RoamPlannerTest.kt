package cp.player.app

import cp.player.model.Song
import org.junit.Assert.*
import org.junit.Test

class RoamPlannerTest {
    private fun track(id: String, artist: String = "A", source: String = "ncm") = Track(Song(id, "同名歌曲", artist, album = "专辑"), source = source)
    @Test fun sameTitlesKeepTheirNativeIdsAndForeignSourcesCannotEnter() {
        val planner = RoamPlanner("ncm", listOf(track("seed")))
        val fresh = planner.fresh(listOf(track("1"), track("2"), track("3", source = "spotify")), emptySet(), emptyList())
        assertEquals(setOf("1", "2"), fresh.map { it.song.id }.toSet())
    }
    @Test fun trimmedQueueStillExcludesHeardAndOriginalPlaylistAndDislikedSongs() {
        val planner = RoamPlanner("ncm", listOf(track("seed")), setOf("ncm:unplayed-in-playlist"))
        planner.remember(listOf(track("heard")))
        val fresh = planner.fresh(listOf(track("heard"), track("unplayed-in-playlist"), track("disliked"), track("queued"), track("new")),
            setOf("ncm:disliked"), listOf(track("queued")))
        assertEquals(listOf("new"), fresh.map { it.song.id })
    }
    @Test fun originalSeedsRotateInsteadOfDriftingOnlyFromTheLastRecommendation() {
        val planner = RoamPlanner("ncm", (0..5).map { track("seed-$it") })
        val a = planner.seeds(track("last")); val b = planner.seeds(track("last"))
        assertNotEquals(a, b)
        assertTrue((a + b).all { it.song.id.startsWith("seed-") })
    }
    @Test fun batchingDiversifiesArtistsAndBoundsQueueGrowth() {
        val candidates = (0..7).map { track("a-$it") } + track("b", "B")
        val result = diverseTracks(candidates, limit = 4)
        assertEquals(4, result.size)
        assertNotEquals(result[0].song.artist, result[1].song.artist)
        assertEquals(4, result.map { it.recommendationKey() }.distinct().size)
    }
}
