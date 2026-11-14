package cp.player.app

import com.google.gson.JsonObject
import cp.player.model.Song
import cp.player.util.JsonUtils

/** The upstream order is the ranking; score is relative weight, never a play count. */
data class ListeningRecord(val song: Song, val playCount: Long?, val score: Int?)
object ListeningRankParser {
    fun parse(response: JsonObject, weekly: Boolean): List<ListeningRecord> {
        val code = response.str("code")?.toIntOrNull()
        require(code == null || code == 200) { response.str("msg") ?: response.str("message") ?: "无法读取听歌排行" }
        val key = if (weekly) "weekData" else "allData"
        val data = response.get(key)
        require(data != null && data.isJsonArray) { "音源未返回听歌排行，请检查登录或访问权限" }
        return data.asJsonArray.mapNotNull { value ->
            val item = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val song = item.get("song")?.let(JsonUtils::parseSong) ?: return@mapNotNull null
            ListeningRecord(song, item.str("playCount")?.toLongOrNull()?.takeIf { it >= 0 },
                item.str("score")?.toIntOrNull()?.takeIf { it >= 0 })
        }
    }
}
