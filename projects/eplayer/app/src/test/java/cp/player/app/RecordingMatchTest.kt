package cp.player.app

import org.junit.Assert.*
import org.junit.Test

class RecordingMatchTest {
    private val song = Recording("ytm", "アイドル", listOf("YOASOBI"), 213000, "アイドル")
    @Test fun titleAloneCannotMatchAnotherArtistOrVersion() {
        for (wrong in listOf(song.copy(id = "1", artists = listOf("其他歌手")), song.copy(id = "2", title = "アイドル (Live)"),
            song.copy(id = "3", title = "アイドル (Instrumental)"), song.copy(id = "4", title = "アイドル (Remix)"),
            song.copy(id = "5", durationMs = 225000), song.copy(id = "6", album = "演唱会"))) {
            assertNull(RecordingMatch.choose(song, listOf(wrong)))
        }
    }
    @Test fun unknownDurationOrArtistNeverBecomesAnAutomaticMatch() {
        assertNull(RecordingMatch.choose(song.copy(durationMs = 0), listOf(song.copy(id = "1"))))
        assertNull(RecordingMatch.choose(song, listOf(song.copy(id = "1", durationMs = 0))))
        assertNull(RecordingMatch.choose(song.copy(artists = emptyList()), listOf(song.copy(id = "1"))))
    }
    @Test fun ambiguityIsSkippedAndAlbumCanResolveIt() {
        val a = song.copy(id = "netease-1")
        val b = song.copy(id = "netease-2", album = "别的专辑")
        assertEquals(a.id, RecordingMatch.choose(song, listOf(a, b)))
        assertNull(RecordingMatch.choose(song.copy(album = ""), listOf(a, b)))
        assertNull(RecordingMatch.choose(song, listOf(a, a.copy(id = "duplicate-recording"))))
    }
    @Test fun unicodeCasePunctuationAndArtistOrderAreHandledWithoutChangingVersions() {
        val expected = Recording("ytm", "Ｔｅｓｔ — Song", listOf("Artist A", "Artist B"), 180000)
        val actual = expected.copy(id = "123", title = "test song", artists = listOf("Artist B / Artist A"), durationMs = 181000)
        assertEquals("123", RecordingMatch.choose(expected, listOf(actual)))
        assertNull(RecordingMatch.choose(expected, listOf(actual.copy(title = "test song acoustic"))))
    }
}
