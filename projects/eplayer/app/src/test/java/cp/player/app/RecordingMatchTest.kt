package cp.player.app

import org.junit.Assert.*
import org.junit.Test

class RecordingMatchTest {
    private val song = Recording("ytm", "アイドル", listOf("YOASOBI"), 213000, "アイドル")

    @Test fun versionTakesLiveInstrumentalAndRemixStayUnmatched() {
        for (wrong in listOf(song.copy(id = "2", title = "アイドル (Live)"), song.copy(id = "3", title = "アイドル (Instrumental)"),
            song.copy(id = "4", title = "アイドル (Remix)"), song.copy(id = "5", durationMs = 260000),
            song.copy(id = "6", title = "アイドル (Cover Ver.) [Mixed]", artists = listOf("Amaris")))) {
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
        // Two identical spellings stay ambiguous only when nothing separates them.
        assertNull(RecordingMatch.choose(song.copy(album = ""), listOf(a, a.copy(id = "duplicate-recording"))))
    }

    @Test fun unicodeCasePunctuationAndArtistOrderAreHandledWithoutChangingVersions() {
        val expected = Recording("ytm", "Ｔｅｓｔ — Song", listOf("Artist A", "Artist B"), 180000)
        val actual = expected.copy(id = "123", title = "test song", artists = listOf("Artist B / Artist A"), durationMs = 181000)
        assertEquals("123", RecordingMatch.choose(expected, listOf(actual)))
        assertNull(RecordingMatch.choose(expected, listOf(actual.copy(title = "test song acoustic"))))
    }

    @Test fun nativeMetadataRetainsEveryPerformerAndBandNamesAreNotInventedCollaborations() {
        val native = cp.player.util.JsonUtils.parseSong(com.google.gson.JsonParser.parseString("""{"id":1,"name":"Duet","ar":[{"id":10,"name":"Artist A"},{"id":11,"name":"Artist B"}],"al":{"name":"Album"},"dt":180000}""").asJsonObject)!!
        val expected = Recording("ytm", "Duet", listOf("Artist B", "Artist A"), 180000, "Album")
        assertEquals("1", RecordingMatch.choose(expected, listOf(Track(native, source = "ncm").recording())))
        assertNull(RecordingMatch.choose(expected, listOf(expected.copy(id = "wrong", artists = listOf("Artist C")))))
        val band = expected.copy(artists = listOf("AC/DC"))
        assertNull(RecordingMatch.choose(band, listOf(band.copy(id = "wrong", artists = listOf("AC", "DC")))))
    }

    @Test fun traditionalAndSimplifiedSpellingsReachTheSameSong() {
        // Real pairs observed between a simplified NCM library and YTM metadata.
        assertNotNull(RecordingMatch.choose(Recording("ncm", "晴天", listOf("周杰伦"), 269000, "叶惠美"),
            listOf(Recording("ytm", "晴天", listOf("周杰倫"), 270000, "葉惠美"))))
        assertNotNull(RecordingMatch.choose(Recording("ncm", "稻香", listOf("周杰伦"), 223000, "魔杰座"),
            listOf(Recording("ytm", "稻香", listOf("周杰倫"), 224000, "魔杰座"))))
    }

    @Test fun katakanaAndHiraganaSpellingsCompareEqual() {
        assertNotNull(RecordingMatch.choose(Recording("ncm", "ヨルシカ", listOf("ヨルシカ"), 240000),
            listOf(Recording("ytm", "よるしか", listOf("よるしか"), 241000))))
    }

    @Test fun masteringLengthGapIsAcceptedButADifferentTakeIsNot() {
        // 夜に駆ける: NCM 259 s vs YTM 262 s is the same master, 300 s is a different edit.
        val expected = Recording("ncm", "夜に駆ける", listOf("YOASOBI"), 259000, "THE BOOK")
        assertEquals("ytm", RecordingMatch.choose(expected, listOf(Recording("ytm", "夜に駆ける", listOf("YOASOBI"), 262000, "夜に駆ける"))))
        assertNull(RecordingMatch.choose(expected, listOf(Recording("ytm", "夜に駆ける", listOf("YOASOBI"), 305000, "夜に駆ける"))))
    }

    @Test fun translatedAlbumTitleStillMatchesThroughTheArtistTier() {
        // Lemon: album is localised as "Lemon", performer spelled in Han here and romaji there.
        val expected = Recording("ncm", "Lemon", listOf("米津玄師"), 256000, "STRAY SHEEP")
        assertEquals("ytm-lemon", RecordingMatch.choose(expected, listOf(Recording("ytm-lemon", "Lemon", listOf("米津玄師"), 257000, "Lemon"))))
        // A romaji-only performer is a different string and cannot be invented as the same artist.
        assertNull(RecordingMatch.choose(expected, listOf(Recording("ytm-lemon", "Lemon", listOf("Kenshi Yonezu"), 257000, "Lemon"))))
    }

    @Test fun multipleCandidatesInsideOneTierStayUnmatched() {
        val expected = Recording("ncm", "晴天", listOf("周杰伦"), 269000, "叶惠美")
        val live = Recording("ytm-live", "晴天", listOf("周杰倫"), 250000, "周杰倫地表最強世界巡迴演唱會")
        val studio = Recording("ytm-studio", "晴天", listOf("周杰倫"), 270000, "葉惠美")
        assertEquals("ytm-studio", RecordingMatch.choose(expected, listOf(live, studio)))
        // Two studio-length takes remain ambiguous instead of picking arbitrarily.
        assertNull(RecordingMatch.choose(expected, listOf(live, studio, studio.copy(id = "ytm-studio-2"))))
    }
    @Test fun missingCandidateArtistCannotMatchViaAnEmptySubset() {
        assertNull(RecordingMatch.choose(song, listOf(song.copy(id = "bad", artists = emptyList()))))
        assertNull(RecordingMatch.choose(song, listOf(song.copy(id = "bad", artists = listOf("Unknown")))))
    }
    @Test fun catalogAliasesMatchWithoutHardcodedPerformerNames() {
        val native = Recording("ncm", "Lemon", listOf("米津玄師"), 256000, artistAliases = listOf("Kenshi Yonezu"))
        assertEquals("ytm", RecordingMatch.choose(native, listOf(Recording("ytm", "Lemon", listOf("Kenshi Yonezu"), 259000))))
        val translated = native.copy(title = "レモン", titleAliases = listOf("Lemon"))
        assertEquals("ytm", RecordingMatch.choose(translated, listOf(Recording("ytm", "Lemon", listOf("Kenshi Yonezu"), 259000))))
    }
    @Test fun fullPerformerIdentityBeatsOneSharedGuest() {
        val expected = song.copy(artists = listOf("A", "B"))
        assertEquals("exact", RecordingMatch.choose(expected, listOf(expected.copy(id = "guest", artists = listOf("A", "C")), expected.copy(id = "exact"))))
    }
    @Test fun promotionalSuffixDoesNotDeleteLettersInsideATitle() {
        val expected = song.copy(title = "MVP")
        assertEquals("same", RecordingMatch.choose(expected, listOf(expected.copy(id = "same", title = "MVP (Official Audio)"))))
        assertNull(RecordingMatch.choose(expected, listOf(expected.copy(id = "wrong", title = "P"))))
        assertNotEquals(RecordingMatch.text("魏"), RecordingMatch.text("未"))
    }

}
