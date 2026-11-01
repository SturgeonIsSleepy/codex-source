package cp.player.app

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import cp.player.util.JsonUtils

enum class MusicKind { PLAYLIST, ALBUM, ARTIST, LIKED, DAILY, CHARTS, BROWSE, CLOUD, RECORD_ALL, RECORD_WEEK }
data class Account(val id: String, val name: String, val avatar: String?)
data class MusicEntry(val id: String, val name: String, val source: String, val kind: MusicKind,
    val subtitle: String = "", val image: String? = null, val owner: String? = null,
    val count: Int = 0, val subscribed: Boolean = false, val special: Boolean = false)
data class MusicDetail(val entry: MusicEntry, val tracks: List<Track> = emptyList(),
    val entries: List<MusicEntry> = emptyList(), val description: String = "", val total: Int = 0, val records: List<ListeningRecord> = emptyList())
data class MusicSearch(val tracks: List<Track> = emptyList(), val entries: List<MusicEntry> = emptyList())
data class MusicComment(val id: String, val user: String, val text: String, val avatar: String?, val liked: Boolean, val likes: Int)

internal fun JsonObject.str(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
internal fun JsonObject.array(key: String): JsonArray = get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
internal fun JsonObject.number(key: String): Int = str(key)?.toIntOrNull() ?: 0
internal fun JsonObject.flag(key: String): Boolean = str(key) == "true"

/** NCM-compatible capabilities over the source gateway; no account data is logged. */
class MusicRepository(private val context: Context, val source: String) {
    private suspend fun request(method: String, params: Map<String, String> = emptyMap()): JsonObject {
        val response = SourceStore.call(context, source, method, params)
        val code = response.str("code")?.toIntOrNull()
        check(code == null || code in setOf(0, 200, 201)) {
            if (code == 301) "登录已失效，请重新登录" else response.str("msg") ?: response.str("message") ?: "请求失败（$code）"
        }
        return response
    }
    private fun tracks(array: JsonArray): List<Track> = array.mapNotNull { runCatching { JsonUtils.parseSong(it) }.getOrNull() }
        .map { Track(it, source = source) }
    private fun entry(value: JsonElement, kind: MusicKind): MusicEntry? {
        if (!value.isJsonObject) return null
        val item = value.asJsonObject
        val creator = item.obj("creator")
        val artist = item.obj("artist") ?: item.array("artists").firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
        return MusicEntry(item.str("id") ?: return null, item.str("name") ?: return null, source, kind,
            creator?.str("nickname") ?: artist?.str("name").orEmpty(),
            item.str("coverImgUrl") ?: item.str("picUrl") ?: item.str("img1v1Url"), creator?.str("userId"),
            item.number("trackCount").takeIf { it > 0 } ?: item.number("size"), item.flag("subscribed") || item.flag("followed"), item.number("specialType") == 5)
    }
    private fun entries(array: JsonArray, kind: MusicKind): List<MusicEntry> = array.mapNotNull { entry(it, kind) }
    suspend fun account(): Account? {
        val response = request("login/status")
        val profile = (response.obj("data") ?: response).obj("profile") ?: return null
        return Account(profile.str("userId") ?: return null, profile.str("nickname").orEmpty(), profile.str("avatarUrl"))
    }
    suspend fun playlists(uid: String): List<MusicEntry> = entries(request("user/playlist", mapOf("uid" to uid, "limit" to "1000")).array("playlist"), MusicKind.PLAYLIST)
    suspend fun liked(uid: String): Set<String> = request("likelist", mapOf("uid" to uid)).array("ids").map { it.asString }.toSet()
    suspend fun recommendations(loggedIn: Boolean): List<MusicEntry> {
        val response = request(if (loggedIn) "recommend/resource" else "personalized", mapOf("limit" to "20"))
        return entries(if (loggedIn) response.array("recommend") else response.array("result"), MusicKind.PLAYLIST)
    }
    suspend fun daily(): List<Track> {
        val response = request("recommend/songs")
        return tracks(response.obj("data")?.array("dailySongs") ?: response.array("recommend"))
    }
    suspend fun radio(): List<Track> = tracks(request("personal_fm").array("data"))
    suspend fun suggestions(query: String): List<String> {
        val result = request("search/suggest", mapOf("keywords" to query, "type" to "mobile")).obj("result") ?: return emptyList()
        return result.array("allMatch").mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject?.str("keyword") }.distinct().take(10)
    }
    suspend fun search(query: String, type: Int): MusicSearch {
        val result = request("cloudsearch", mapOf("keywords" to query, "type" to type.toString(), "limit" to "50")).obj("result") ?: JsonObject()
        return when (type) {
            1000 -> MusicSearch(entries = entries(result.array("playlists"), MusicKind.PLAYLIST))
            10 -> MusicSearch(entries = entries(result.array("albums"), MusicKind.ALBUM))
            100 -> MusicSearch(entries = entries(result.array("artists"), MusicKind.ARTIST))
            else -> MusicSearch(tracks = tracks(result.array("songs")))
        }
    }
    suspend fun detail(target: MusicEntry, offset: Int = 0): MusicDetail = when (target.kind) {
        MusicKind.PLAYLIST -> {
            val header = request("playlist/detail", mapOf("id" to target.id)).obj("playlist") ?: error("歌单不存在")
            val songs = request("playlist/track/all", mapOf("id" to target.id, "limit" to "200", "offset" to offset.toString()))
            MusicDetail(entry(header, target.kind) ?: target, tracks(songs.array("songs")),
                description = header.str("description").orEmpty(), total = header.number("trackCount"))
        }
        MusicKind.ALBUM -> {
            val response = request("album", mapOf("id" to target.id))
            val album = response.obj("album")
            val information = runCatching { request("album/detail/dynamic", mapOf("id" to target.id)) }.getOrNull()
            val albumEntry = album?.let { entry(it, target.kind) } ?: target
            MusicDetail(albumEntry.copy(subscribed = information?.flag("isSub") ?: albumEntry.subscribed), tracks(response.array("songs")), description = album?.str("description").orEmpty())
        }
        MusicKind.ARTIST -> {
            val response = request("artists", mapOf("id" to target.id))
            val artist = response.obj("artist")
            val albums = request("artist/album", mapOf("id" to target.id, "limit" to "50"))
            MusicDetail(artist?.let { entry(it, target.kind) } ?: target, tracks(response.array("hotSongs")),
                entries(albums.array("hotAlbums"), MusicKind.ALBUM), artist?.str("briefDesc").orEmpty())
        }
        MusicKind.DAILY -> MusicDetail(target, daily())
        MusicKind.LIKED -> {
            val ids = liked(target.id).toList()
            val batch = ids.drop(offset).take(200)
            val songs = if (batch.isEmpty()) emptyList() else tracks(request("song/detail", mapOf("ids" to batch.joinToString(","))).array("songs"))
            MusicDetail(target, songs, total = ids.size)
        }
        MusicKind.CHARTS -> MusicDetail(target, entries = entries(request("toplist").array("list"), MusicKind.PLAYLIST))
        MusicKind.BROWSE -> when (target.id) {
            "new" -> MusicDetail(target, entries = entries(request("album/newest").array("albums"), MusicKind.ALBUM))
            "genres" -> MusicDetail(target, entries = request("playlist/catlist").array("sub").mapNotNull { item ->
                item.takeIf { it.isJsonObject }?.asJsonObject?.str("name")?.let { MusicEntry("cat:$it", it, source, MusicKind.BROWSE) }
            })
            else -> MusicDetail(target, entries = entries(request("top/playlist", mapOf("cat" to if (target.id.startsWith("cat:")) target.id.removePrefix("cat:") else "全部", "limit" to "50")).array("playlists"), MusicKind.PLAYLIST))
        }
        MusicKind.RECORD_ALL, MusicKind.RECORD_WEEK -> {
            val weekly = target.kind == MusicKind.RECORD_WEEK
            val response = request("user/record", mapOf("uid" to target.id, "type" to if (weekly) "1" else "0"))
            val records = ListeningRankParser.parse(response, weekly)
            MusicDetail(target, records.map { Track(it.song, source = source) }, total = records.size, records = records)
        }
        MusicKind.CLOUD -> {
            val response = request("user/cloud", mapOf("limit" to "200", "offset" to offset.toString()))
            val songs = response.array("data").mapNotNull { value ->
                val cloud = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val song = cloud.obj("simpleSong")?.let(JsonUtils::parseSong) ?: JsonUtils.parseCloudSongItem(cloud)
                song?.let { Track(it.copy(id = cloud.str("songId") ?: it.id.removePrefix("cloud_")), source = source) }
            }
            MusicDetail(target, songs, total = response.number("count"))
        }
    }
    suspend fun similar(id: String): List<Track> = tracks(request("simi/song", mapOf("id" to id)).array("songs"))
    suspend fun songs(ids: List<String>): List<Track> {
        val result = mutableListOf<Track>()
        ids.chunked(500).forEach { chunk -> result += tracks(request("song/detail", mapOf("ids" to chunk.joinToString(","))).array("songs")) }
        val byId = result.associateBy { it.remoteId ?: it.song.id }
        return ids.mapNotNull(byId::get)
    }
    suspend fun playlistIds(id: String): List<String> {
        val header = request("playlist/detail", mapOf("id" to id)).obj("playlist") ?: error("歌单不存在")
        val ids = header.array("trackIds").mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject?.str("id") }
        check(ids.size == header.number("trackCount")) { "歌单未完整加载，请重试" }
        return ids
    }
    suspend fun like(id: String, liked: Boolean) { request("like", mapOf("id" to id, "like" to liked.toString())) }
    suspend fun createPlaylist(name: String, private: Boolean = false): MusicEntry {
        val response = request("playlist/create", mapOf("name" to name, "privacy" to if (private) "10" else "0"))
        return response.obj("playlist")?.let { entry(it, MusicKind.PLAYLIST) } ?: error("创建歌单失败")
    }
    suspend fun renamePlaylist(id: String, name: String) { request("playlist/name/update", mapOf("id" to id, "name" to name)) }
    suspend fun deletePlaylist(id: String) { request("playlist/delete", mapOf("id" to id)) }
    suspend fun changeTracks(playlist: String, ids: List<String>, add: Boolean) {
        request("playlist/tracks", mapOf("op" to if (add) "add" else "del", "pid" to playlist, "tracks" to ids.joinToString(",")))
    }
    suspend fun movePlaylistTrack(playlist: String, fromId: String, toId: String) {
        val header = request("playlist/detail", mapOf("id" to playlist)).obj("playlist") ?: error("歌单不存在")
        val ids = header.array("trackIds").mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject?.str("id") }.toMutableList()
        check(ids.size == header.number("trackCount")) { "歌单未完整加载，请重试" }
        val from = ids.indexOf(fromId); val to = ids.indexOf(toId)
        check(from >= 0 && to >= 0) { "歌单已更新，请刷新重试" }
        if (from == to) return
        ids.add(to, ids.removeAt(from))
        // Submit the complete current server order, including songs beyond the loaded UI page.
        val array = JsonArray().apply { ids.forEach { add(it.toBigDecimal()) } }
        request("song/order/update", mapOf("pid" to playlist, "ids" to array.toString()))
    }
    suspend fun subscribe(target: MusicEntry, value: Boolean) {
        val method = when (target.kind) { MusicKind.ALBUM -> "album/sub"; MusicKind.ARTIST -> "artist/sub"; else -> "playlist/subscribe" }
        request(method, mapOf("id" to target.id, "t" to if (value) "1" else "2"))
    }
    suspend fun comments(song: String, offset: Int = 0): List<MusicComment> = request("comment/music", mapOf("id" to song, "limit" to "30", "offset" to offset.toString()))
        .array("comments").mapNotNull { value ->
            val comment = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val user = comment.obj("user") ?: JsonObject()
            MusicComment(comment.str("commentId") ?: return@mapNotNull null, user.str("nickname").orEmpty(), comment.str("content").orEmpty(),
                user.str("avatarUrl"), comment.flag("liked"), comment.number("likedCount"))
        }
    suspend fun sendComment(song: String, content: String, reply: String? = null) {
        request("comment", mapOf("t" to if (reply == null) "1" else "2", "type" to "0", "id" to song, "content" to content) +
            (reply?.let { mapOf("commentId" to it) } ?: emptyMap()))
    }
    suspend fun likeComment(song: String, comment: String, liked: Boolean) {
        request("comment/like", mapOf("id" to song, "cid" to comment, "t" to if (liked) "1" else "0", "type" to "0"))
    }
}
