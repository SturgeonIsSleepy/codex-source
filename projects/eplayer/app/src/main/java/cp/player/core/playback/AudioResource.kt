package cp.player.core.playback

import com.google.gson.JsonObject
import java.io.IOException
import java.net.URI

/** Identity belongs to the returned bytes, not the quality requested by the user. */
data class AudioResource(val url: String, val identity: String, val size: Long, val lifetimeSeconds: Long) {
    fun cacheKey(source: String, song: String, quality: String) = "audio-v2:$source:$song:$quality:$identity"

    companion object {
        private val qualityLevels = listOf("standard", "higher", "exhigh", "lossless", "hires", "jyeffect", "sky", "jymaster")
        private val lossyFormats = setOf("mp3", "aac", "ogg", "opus")

        fun parse(response: JsonObject, requestedQuality: String? = null): AudioResource {
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
                ?: audioUrl(response.text("url")) ?: run {
                    val code = row.text("code")?.toIntOrNull()?.takeUnless { it == 200 }
                        ?: response.text("code")?.toIntOrNull()
                    val message = when {
                        code in setOf(301, 401) -> "音源登录已失效，请重新登录"
                        code == 403 -> "当前账号无法播放这首歌"
                        code != null && code >= 500 -> "音源未返回播放链接，请检查网络后重试"
                        else -> "所选音质暂无播放链接"
                    }
                    throw AudioResolutionException(message, code != null && code >= 500)
                }
            val requested = qualityLevels.indexOf(requestedQuality)
            val returned = qualityLevels.indexOf(row.text("level")?.lowercase())
            val format = row.text("encodeType")?.lowercase() ?: row.text("type")?.lowercase()
            if ((returned >= 0 && returned < requested) ||
                (requested in 3..4 && format in lossyFormats)) {
                throw AudioResolutionException("所选音质不可用，请手动切换音质", retryable = false)
            }
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
