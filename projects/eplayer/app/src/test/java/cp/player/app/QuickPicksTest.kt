package cp.player.app

import cp.player.model.Song
import org.junit.Assert.*
import org.junit.Test

class QuickPicksTest {
    private val recent = (0..8).map { Track(Song("$it", "歌曲 $it", "歌手 $it", artistId = "artist-$it", album = "专辑 $it", albumId = "album-$it"), source = "ncm") }
    private val home = CloudHome(source = "ncm", playlists = listOf(MusicEntry("likes", "喜欢", "ncm", MusicKind.PLAYLIST, special = true)))
    @Test fun savedArtistsCannotBuryPlaylistsAlbumsAndSongs() {
        val saved = (0..23).map { QuickPick("ARTIST:saved-$it", "旧歌手 $it", entry = MusicEntry("saved-$it", "旧歌手 $it", "ncm", MusicKind.ARTIST)) }
        val picks = quickPicks(home, recent, saved)
        assertEquals(setOf(1, 10, 100, 1000), picks.take(9).map { it.kind }.toSet())
        assertTrue(picks.size <= 24)
    }
    @Test fun sourceDislikesAndCanonicalSeedDuplicatesAreFiltered() {
        val track = recent.first()
        val saved = listOf(QuickPick("SONG:0", "旧名字", track = track), QuickPick("song:elsewhere", "其他音源", track = track.copy(source = "foreign")))
        val picks = quickPicks(home, recent, saved, setOf("ncm:1"))
        assertTrue(picks.all { it.source == "ncm" })
        assertEquals(1, picks.count { it.track?.song?.id == "0" })
        assertFalse(picks.any { it.track?.song?.id == "1" })
    }
    @Test fun serviceRecommendationsCanFillAnEmptyPersonalLibrary() {
        val candidate = MusicEntry("recommended", "推荐歌单", "ncm", MusicKind.PLAYLIST)
        val result = quickPicks(CloudHome(source = "ncm", recommendations = listOf(candidate)), emptyList(), emptyList())
        assertEquals(candidate, result.single().entry)
    }
}
