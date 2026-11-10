package cp.player.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pairs observed by probing YTM anonymously for real Chinese/Japanese library seeds. These are the
 * exact strings the two catalogs return, so the matcher is checked against catalog reality rather than
 * invented fixtures. Each case documents what the previous all-strict rule did with the same input.
 */
class RecordingMatchCatalogTest {
    private fun native(title: String, artist: String, ms: Long, album: String = "") =
        Recording("ncm", title, listOf(artist), ms, album)

    private fun ytm(id: String, title: String, artist: String, ms: Long, album: String = "") =
        Recording(id, title, listOf(artist), ms, album)

    @Test fun simplifiedLibraryReachesTraditionalYoutubeSpelling() {
        // Before: rejected, performer and album differed only by script.
        assertEquals("ytm", RecordingMatch.choose(native("晴天", "周杰伦", 269000, "叶惠美"),
            listOf(ytm("ytm", "晴天", "周傑倫", 270000, "葉惠美"))))
        assertEquals("ytm", RecordingMatch.choose(native("稻香", "周杰伦", 223000, "魔杰座"),
            listOf(ytm("ytm", "稻香", "周傑倫", 224000, "魔杰座"))))
    }

    @Test fun masteringGapBetweenCatalogsIsNotTreatedAsAnotherSong() {
        // 夜に駆ける: NCM 259 s, YTM 262 s. Before: rejected on the 2.5 s tolerance alone.
        assertEquals("ytm", RecordingMatch.choose(native("夜に駆ける", "YOASOBI", 259000, "THE BOOK"),
            listOf(ytm("ytm", "夜に駆ける", "YOASOBI", 262000, "夜に駆ける"))))
    }

    @Test fun translatedAlbumTitleDoesNotBlockTheSamePerformer() {
        // Lemon: YTM returns the same Han performer but localises the album name.
        assertEquals("ytm", RecordingMatch.choose(native("Lemon", "米津玄師", 256000, "STRAY SHEEP"),
            listOf(ytm("ytm", "Lemon", "米津玄師", 257000, "Lemon"))))
    }

    @Test fun commaSeparatedDuetStillResolvesWhenOneNameIsTranslated() {
        // 打上花火: YTM writes "DAOKO,Kenshi Yonezu" for "DAOKO,米津玄師".
        assertNotNull(RecordingMatch.choose(native("打上花火", "DAOKO", 289000, "打上花火").copy(artists = listOf("DAOKO", "米津玄師")),
            listOf(Recording("ytm", "打上花火", listOf("DAOKO", "Kenshi Yonezu"), 290000, "打上花火"))))
    }

    @Test fun studioVersionWinsOverADifferentLengthLiveRecording() {
        // 晴天 search returns a 250 s concert take and the 270 s studio take.
        val expected = native("晴天", "周杰伦", 269000, "叶惠美")
        val live = ytm("ytm-live", "晴天", "周傑倫", 250000, "周杰倫地表最強世界巡迴演唱會")
        val studio = ytm("ytm-studio", "晴天", "周傑倫", 270000, "葉惠美")
        assertEquals("ytm-studio", RecordingMatch.choose(expected, listOf(live, studio)))
    }

    @Test fun coverAndRomajiOnlyReleasesAreStillRefused() {
        // 紅蓮華: the only same-title hit is a cover by "aLf"; a romaji-only performer cannot be assumed.
        assertNull(RecordingMatch.choose(native("紅蓮華", "LiSA", 236000, "紅蓮華"),
            listOf(ytm("ytm", "紅蓮華", "aLf", 237000, "Gurenge"))))
        assertNull(RecordingMatch.choose(native("Lemon", "米津玄師", 256000, "STRAY SHEEP"),
            listOf(ytm("ytm", "Lemon", "Kenshi Yonezu", 257000, "Lemon"))))
        assertNull(RecordingMatch.choose(native("アイドル", "YOASOBI", 213000, "アイドル"),
            listOf(ytm("ytm", "アイドル (Cover Ver.) [Mixed]", "Amaris", 38000, ""))))
    }
}
