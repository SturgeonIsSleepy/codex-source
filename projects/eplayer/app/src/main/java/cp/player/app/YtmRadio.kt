package cp.player.app

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Metadata-only anonymous Radio. No player/stream requests, account cookies or remote playlist writes. */
internal class YtmRadio {
    companion object {
        /** Rebuilt whenever the configured proxy changes, so a new setting is never served by a stale client. */
        @Volatile private var client: OkHttpClient = build(null)
        private var currentProxy: YtmProxy.Config? = null
        private var routeGeneration = 0L

        private fun build(proxy: YtmProxy.Config?): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false)
            .apply { proxy?.let { proxy(it.toProxy()) } }
            .build()

        /** Applies the user's proxy and drops cached metadata tied to the previous route. */
        @Synchronized fun configure(context: android.content.Context) {
            val raw = if (cp.player.network.PlaybackNetwork.splitEnabled(context)) YtmProxy.raw(context) else ""
            val wanted = YtmProxy.parse(raw)
            require(raw.isBlank() || wanted != null) { "YTM 代理地址无效，请重新设置" }
            if (wanted == currentProxy) return
            currentProxy = wanted
            client.connectionPool.evictAll()
            client = build(wanted)
            routeGeneration++
            cache.clear()
            bootstrap = null
        }

        private data class Cached(val at: Long, val value: List<Recording>)
        private val cache = linkedMapOf<String, Cached>()
        private var bootstrap: Pair<Long, JsonObject>? = null
        @Synchronized private fun cached(key: String): List<Recording>? = cache[key]?.takeIf { System.currentTimeMillis() - it.at < 10 * 60_000 }?.value
        @Synchronized private fun store(key: String, value: List<Recording>) {
            if (value.isEmpty()) return
            cache[key] = Cached(System.currentTimeMillis(), value)
            while (cache.size > 32) cache.remove(cache.keys.first())
        }
    }
    private val route = synchronized(Companion) { client to routeGeneration }
    private val requestClient = route.first
    private val generation = route.second
    private suspend fun response(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = requestClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (!continuation.isCancelled) continuation.resumeWith(Result.failure(e)) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    check(it.isSuccessful) { "YTM 请求失败 (${it.code})" }
                    it.body.source().use { source ->
                        source.request(2_000_001)
                        check(source.buffer.size <= 2_000_000) { "YTM 响应过大" }
                        source.buffer.readUtf8()
                    }
                } }
                if (!continuation.isCancelled) continuation.resumeWith(result)
            }
        })
    }
    private suspend fun context(): JsonObject {
        synchronized(Companion) { bootstrap?.takeIf { System.currentTimeMillis() - it.first < 6 * 60 * 60_000 }?.let { return it.second } }
        val html = try { response(Request.Builder().url("https://music.youtube.com/").header("User-Agent", "Mozilla/5.0").header("Cookie", "SOCS=CAI").build()) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { "" }
        // ytmusicapi initialize_context uses a UTC date version; the landing page is optional.
        val version = Regex("\"(?:INNERTUBE_CLIENT_VERSION|INNERTUBE_CONTEXT_CLIENT_VERSION|clientVersion)\"\\s*:\\s*\"([0-9.]+)\"").find(html)?.groupValues?.get(1)
            ?: "1.${LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE)}.01.00"
        val visitor = Regex("\"VISITOR_DATA\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)
        val value = JsonObject().apply { add("client", JsonObject().apply {
            addProperty("clientName", "WEB_REMIX"); addProperty("clientVersion", version)
            addProperty("hl", "en"); visitor?.let { addProperty("visitorData", it) }
        }) }
        synchronized(Companion) { if (generation == routeGeneration) bootstrap = System.currentTimeMillis() to value }
        return value
    }
    private suspend fun request(endpoint: String, body: JsonObject): JsonObject {
        val context = context()
        body.add("context", context)
        val request = Request.Builder().url("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
            .header("User-Agent", "Mozilla/5.0").header("Origin", "https://music.youtube.com")
            // Public, non-login consent marker also used by ytmusicapi; no account cookies.
            .header("Cookie", "SOCS=CAI")
            .header("X-Youtube-Client-Name", "67").header("X-Youtube-Client-Version", context.obj("client")!!.str("clientVersion")!!)
        context.obj("client")?.str("visitorData")?.let { request.header("X-Goog-Visitor-Id", it) }
        val result = JsonParser.parseString(response(request.post(body.toString().toRequestBody("application/json".toMediaType())).build())).asJsonObject
        check(result.obj("error") == null) { "YTM 推荐暂不可用" }
        return result
    }
    suspend fun search(song: Recording, query: String = "${song.title} ${song.artists.joinToString(" ")}"): List<Recording> {
        val key = "search:$query"
        cached(key)?.let { return it }
        val result = YtmMetadata.search(request("search", JsonObject().apply {
            addProperty("query", query)
            addProperty("params", "EgWKAQIIAWoMEA4QChADEAQQCRAF")
        }))
        synchronized(Companion) { if (generation == routeGeneration) store(key, result) }; return result
    }
    suspend fun radio(id: String): List<Recording> {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}")))
        cached("radio:$id")?.let { return it }
        val result = YtmMetadata.radio(request("next", JsonObject().apply {
            addProperty("videoId", id); addProperty("playlistId", "RDAMVM$id"); addProperty("params", "wAEB")
            addProperty("isAudioOnly", true); addProperty("enablePersistentPlaylistPanel", true)
        }))
        synchronized(Companion) { if (generation == routeGeneration) store("radio:$id", result) }; return result
    }
}

/** Parse only song renderers with artist links and known duration; ignore video-only/advertisement nodes. */
internal object YtmMetadata {
    private fun all(root: JsonElement, name: String): List<JsonObject> {
        val found = mutableListOf<JsonObject>()
        var count = 0
        fun visit(element: JsonElement) {
            if (++count > 30_000) return
            if (element.isJsonObject) element.asJsonObject.entrySet().forEach { (key, value) ->
                if (key == name && value.isJsonObject) found += value.asJsonObject else visit(value)
            } else if (element.isJsonArray) element.asJsonArray.forEach(::visit)
        }
        visit(root); return found
    }
    private fun text(node: JsonObject?) = node?.str("simpleText") ?: node?.array("runs")?.joinToString("") { it.asJsonObject.str("text").orEmpty() }.orEmpty()
    private fun duration(value: String): Long = value.split(':').takeIf { it.size in 2..3 && it.all { v -> v.toLongOrNull() != null } }
        ?.fold(0L) { seconds, part -> seconds * 60 + part.toLong() }?.times(1000) ?: 0
    private fun recording(node: JsonObject, title: String, runs: List<JsonObject>, id: String?, length: String): Recording? {
        val artists = runs.filter { it.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")?.startsWith("UC") == true }
            .mapNotNull { it.str("text") }.distinct()
        val album = runs.firstOrNull { it.obj("navigationEndpoint")?.obj("browseEndpoint")?.str("browseId")?.startsWith("MPRE") == true }?.str("text").orEmpty()
        val videoTypes = all(node, "watchEndpointMusicConfig").mapNotNull { it.str("musicVideoType") }
        if (videoTypes.isNotEmpty() && "MUSIC_VIDEO_TYPE_ATV" !in videoTypes) return null
        if (id.isNullOrBlank() || title.isBlank() || artists.isEmpty() || duration(length) <= 0 || node.has("unplayableText")) return null
        return Recording(id, title, artists, duration(length), album)
    }
    fun search(root: JsonObject): List<Recording> = all(root, "musicResponsiveListItemRenderer").mapNotNull { row ->
        val columns = row.array("flexColumns").mapNotNull { it.asJsonObject.obj("musicResponsiveListItemFlexColumnRenderer")?.obj("text") }
        val runs = columns.drop(1).flatMap { it.array("runs").map { run -> run.asJsonObject } }
        val id = row.obj("playlistItemData")?.str("videoId") ?: all(row, "watchEndpoint").firstNotNullOfOrNull { it.str("videoId") }
        recording(row, text(columns.firstOrNull()), runs, id, runs.mapNotNull { it.str("text") }.firstOrNull { it.matches(Regex("\\d+:\\d{2}(?::\\d{2})?")) }.orEmpty())
    }.distinctBy { it.id }.take(20)
    fun radio(root: JsonObject): List<Recording> = all(root, "playlistPanelVideoRenderer").mapNotNull { row ->
        val runs = row.obj("longBylineText")?.array("runs")?.map { it.asJsonObject }.orEmpty()
        recording(row, text(row.obj("title")), runs, row.str("videoId"), text(row.obj("lengthText")))
    }.distinctBy { it.id }.take(32)
}
