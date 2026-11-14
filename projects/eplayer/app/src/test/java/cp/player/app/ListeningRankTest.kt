package cp.player.app

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ListeningRankTest {
    @Test fun allTimeRankKeepsServerOrderAndDoesNotConfuseScoreWithPlayCount() {
        val json = JsonParser.parseString("""{"code":200,"allData":[{"song":{"id":9,"name":"first","ar":[{"name":"artist"}],"al":{"name":"album"}},"score":100},{"song":{"id":1,"name":"second","ar":[],"al":{}},"score":70,"playCount":12}]}""").asJsonObject
        val items = ListeningRankParser.parse(json, false)
        assertEquals(listOf("9", "1"), items.map { it.song.id })
        assertNull(items[0].playCount)
        assertEquals(100, items[0].score)
        assertEquals(12L, items[1].playCount)
    }
    @Test fun weeklyAndAllTimeAreSeparateAndZeroCountsAreNotInventedFromScores() {
        val json = JsonParser.parseString("""{"code":200,"allData":[],"weekData":[{"song":{"id":2,"name":"weekly"},"score":80,"playCount":0}]}""").asJsonObject
        assertTrue(ListeningRankParser.parse(json, false).isEmpty())
        val weekly = ListeningRankParser.parse(json, true).single()
        assertEquals("2", weekly.song.id)
        assertEquals(0L, weekly.playCount)
        assertEquals(80, weekly.score)
    }
    @Test fun permissionOrMissingDataCannotMasqueradeAsAnEmptyHistory() {
        for (raw in listOf("""{"code":-2,"msg":"无权限访问"}""", """{"code":301,"message":"login"}""", """{"code":200}""")) {
            try { ListeningRankParser.parse(JsonParser.parseString(raw).asJsonObject, false); fail("Must retain API failure") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
