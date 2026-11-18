package cp.player.app

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class YtmMetadataTest {
    private val row = """{"videoId":"abcdefghijk","title":{"runs":[{"text":"Song"}]},"lengthText":{"runs":[{"text":"3:00"}]},"longBylineText":{"runs":[{"text":"Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}},{"text":"Album","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREalbum"}}}]},"navigationEndpoint":{"watchEndpoint":{"watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}}}}"""
    @Test fun radioReadsOnlySongMetadataAndNeverProvidesAPlaybackUrl() {
        val json = JsonParser.parseString("""{"contents":[{"playlistPanelVideoRenderer":$row}]}""").asJsonObject
        val result = YtmMetadata.radio(json).single()
        assertEquals("abcdefghijk", result.id); assertEquals(listOf("Artist"), result.artists)
        assertEquals(180000L, result.durationMs); assertEquals("Album", result.album)
    }
    @Test fun musicVideosMissingTimingAndUnavailableRowsAreSkipped() {
        for (invalid in listOf(row.replace("MUSIC_VIDEO_TYPE_ATV", "MUSIC_VIDEO_TYPE_OMV"), row.replace("3:00", ""), row.replace("\"videoId\"", "\"unplayableText\""))) {
            val json = JsonParser.parseString("""{"contents":[{"playlistPanelVideoRenderer":$invalid}]}""").asJsonObject
            assertTrue(YtmMetadata.radio(json).isEmpty())
        }
    }
    @Test fun songSearchPreservesIdentityAndNoResultsMessagesStayEmpty() {
        val json = JsonParser.parseString("""{"contents":[{"musicResponsiveListItemRenderer":{"playlistItemData":{"videoId":"abcdefghijk"},"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song"}]}}},{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}},{"text":"Album","navigationEndpoint":{"browseEndpoint":{"browseId":"MPREalbum"}}},{"text":"3:00"}]}}}]}}]}""").asJsonObject
        val item = YtmMetadata.search(json).single()
        assertEquals("abcdefghijk", item.id); assertEquals("Song", item.title)
        assertEquals(listOf("Artist"), item.artists); assertEquals("Album", item.album); assertEquals(180000L, item.durationMs)
        val empty = JsonParser.parseString("""{"contents":{"messageRenderer":{"text":{"runs":[{"text":"No results"}]}}}}""").asJsonObject
        assertTrue(YtmMetadata.search(empty).isEmpty())
    }
}
