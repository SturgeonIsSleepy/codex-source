package cp.player.core.playback

import com.google.gson.JsonObject
import java.io.IOException
import java.net.URI

/** Identity belongs to the returned bytes, not the quality requested by the user. */
data class AudioResource(val url: String, val identity: String, val size: Long, val lifetimeSeconds: Long) {
    fun cacheKey(source: String, song: String, quality: String) = "audio-v2:$source:$song:$quality:$identity"

    companion object {
        fun parse(response: JsonObject): AudioResource {
            val data = response.get("data")
            val row = when {
                data?.isJsonArray == true -> data.asJsonArray.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                data?.isJsonObject == true -> data.asJsonObject
                else -> null
            }
            fun JsonObject?.text(key: String) = this?.get(key)?.takeIf { it.isJsonPrimitive }?.asString
            fun audioUrl(value: String?): String? = value?.takeIf {
                runCatching { URI(it).let { uri -> uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() } }.getOrDefault(false)
            }
            // Never recursively select album artwork, help links or a login URL as audio.
            val url = audioUrl(response.text("redirectUrl")) ?: audioUrl(row.text("url"))
                ?: audioUrl(response.text("url")) ?: throw AudioResolutionException(
                    response.text("msg") ?: "此歌曲暂不可播放，可能需要登录或会员权限",
                    response.text("code")?.toIntOrNull()?.let { it >= 500 } == true)
            val size = row.text("size")?.toLongOrNull()?.coerceAtLeast(0) ?: 0L
            val md5 = row.text("md5")?.takeIf { it.isNotBlank() && it != "0" }
            // This is the provider's existing content identifier; no digest is computed here.
            // Providers without one get the exact URL, favouring safety over cache hits.
            val identity = if (md5 != null && size > 0) listOf(md5, size.toString(), row.text("br"),
                row.text("type"), row.text("level"), row?.get("freeTrialInfo")?.toString()).joinToString(":") else url
            return AudioResource(url, identity, size,
                (row.text("expi")?.toLongOrNull() ?: 300L).coerceIn(1, 3600))
        }
    }
}

class AudioResolutionException(message: String, val retryable: Boolean) : IOException(message)
class AudioRepresentationChanged : IOException("音频文件已更新，正在重新定位")
