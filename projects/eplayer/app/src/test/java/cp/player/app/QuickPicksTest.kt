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
        assertTrue(picks.size <= 48)
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
    @Test fun sameArtworkIsNotRepeatedAcrossCategoriesOrCdnSizes() {
        val a = "https://p1.music.126.net/hash/123.jpg?param=300y300"
        val b = "http://p2.music.126.net/other/123.jpg?param=600y600"
        val picks = listOf(QuickPick("song:1", "one", a, track = recent[0]),
            QuickPick("album:1", "album", b, MusicEntry("1", "album", "ncm", MusicKind.ALBUM)),
            QuickPick("album:2", "other", "https://p1.music.126.net/hash/456.jpg", MusicEntry("2", "other", "ncm", MusicKind.ALBUM)))
        val visible = visibleQuickPicks(picks, 0)
        assertEquals(2, visible.size)
        assertEquals(2, visible.mapNotNull { coverIdentity(it.image) }.toSet().size)
        assertEquals(setOf(1, 10), visible.map { it.kind }.toSet())
        assertEquals(2, visibleQuickPicks(picks, 10).size)
    }
    @Test fun artIdentityKeepsFunctionalQueryParametersAndArtistsDoNotBorrowAlbumCovers() {
        assertNotEquals(coverIdentity("https://covers.test/art?id=1&w=200"), coverIdentity("https://covers.test/art?id=2&w=200"))
        val tracks = recent.map { it.copy(song = it.song.copy(albumArtUrl = "https://covers.test/album.jpg")) }
        assertTrue(quickPicks(home, tracks, emptyList()).filter { it.kind == 100 }.all { it.image == null })
        val populated = quickPicks(home, tracks, emptyList(), artistImages = mapOf("ncm:artist-0" to "https://covers.test/avatar.jpg"))
        assertEquals("https://covers.test/avatar.jpg", populated.first { it.kind == 100 }.image)
    }

    @Test fun oldArtistShortcutGetsSafeViewParameters() {
        val old = """[{"key":"artist:32062601","title":"花譜","entry":{"id":"32062601","name":"花譜","source":"ncm","kind":"ARTIST"}}]"""
        val pick = readQuickPickHistory(old).single()
        assertEquals(MusicKind.ARTIST, pick.entry!!.kind)
        assertEquals("", pick.entry.query); assertEquals("", pick.entry.section)
        assertTrue(readQuickPickHistory("""[{"broken":true}]""").isEmpty())
    }

}
