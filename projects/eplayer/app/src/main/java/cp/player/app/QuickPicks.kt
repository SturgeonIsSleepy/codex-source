package cp.player.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

internal data class QuickPick(val key: String, val title: String, val image: String? = null,
    val entry: MusicEntry? = null, val track: Track? = null) {
    val kind: Int get() = when (entry?.kind) { MusicKind.ARTIST -> 100; MusicKind.PLAYLIST -> 1000; MusicKind.ALBUM -> 10; else -> 1 }
    val source: String? get() = entry?.source ?: track?.source
}

internal fun readQuickPickHistory(raw: String?): List<QuickPick> = runCatching {
    com.google.gson.JsonParser.parseString(raw ?: "[]").asJsonArray.mapNotNull { value -> runCatching {
        val item = value.asJsonObject
        val key = item.str("key")?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val title = item.str("title")?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val entry = item.obj("entry")?.let { old ->
            val kind = runCatching { MusicKind.valueOf(old.str("kind").orEmpty()) }.getOrElse {
                when (key.substringBefore(':').lowercase()) { "artist" -> MusicKind.ARTIST; "album" -> MusicKind.ALBUM; else -> MusicKind.PLAYLIST }
            }
            MusicEntry(old.str("id") ?: return@runCatching null, old.str("name") ?: title,
                old.str("source") ?: return@runCatching null, kind, old.str("subtitle").orEmpty(), old.str("image"), old.str("owner"),
                old.number("count"), old.flag("subscribed"), old.flag("special"), old.str("section").orEmpty(), old.str("query").orEmpty())
        }
        val track = item.obj("track")?.let { com.google.gson.Gson().fromJson(it, Track::class.java) }
        QuickPick(key, title, item.str("image"), entry, track)
    }.getOrNull() }
}.getOrDefault(emptyList())

internal fun quickPicks(home: CloudHome, recent: List<Track>, saved: List<QuickPick>, excluded: Set<String> = emptySet(), artistImages: Map<String, String> = emptyMap()): List<QuickPick> {
    val source = home.source ?: return emptyList()
    val tracks = recent.filter { it.source == source && it.recommendationKey() !in excluded }.distinctBy { it.recommendationKey() }
    val artists = tracks.mapNotNull { track -> track.song.artistId?.takeIf { it.isNotBlank() }?.let {
        QuickPick("artist:$it", track.song.artists.orEmpty().firstOrNull()?.name ?: track.song.artist.substringBefore(" / "), artistImages["$source:$it"], MusicEntry(it, track.song.artist.substringBefore(" / "), source, MusicKind.ARTIST, image = artistImages["$source:$it"]))
    } }.distinctBy { it.key }.take(16)
    val albums = tracks.mapNotNull { track -> track.song.albumId?.takeIf { it.isNotBlank() }?.let {
        QuickPick("album:$it", track.song.album, track.song.albumArtUrl, MusicEntry(it, track.song.album, source, MusicKind.ALBUM))
    } }.distinctBy { it.key }.take(16)
    val playlists = (home.playlists + home.recommendations).distinctBy { it.id }.take(12).map { QuickPick("playlist:${it.id}", if (it.special) "我喜欢的音乐" else it.name, it.image, it) }
    val singles = tracks.take(32).map { QuickPick("song:${it.remoteId ?: it.song.id}", it.song.name, it.song.albumArtUrl, track = it) }
    val remembered = saved.filter { it.source == source && (it.track == null || it.track.recommendationKey() !in excluded) }.map {
        if (it.kind == 100) it.copy(image = artistImages["$source:${it.entry?.id}"]) else it
    }
    val candidates = remembered + playlists + artists + albums + singles
    fun identity(pick: QuickPick) = "${pick.kind}:${pick.entry?.id ?: pick.track?.remoteId ?: pick.track?.song?.id ?: pick.key}"
    val grouped = candidates.distinctBy(::identity).groupBy { it.kind }.mapValues { (_, values) ->
        values.sortedBy { pick ->
            val memory = remembered.indexOfFirst { identity(it) == identity(pick) }.takeIf { it >= 0 } ?: 100
            val recentRank = tracks.indexOfFirst { track -> when (pick.kind) {
                100 -> track.song.artistId == pick.entry?.id
                10 -> track.song.albumId == pick.entry?.id
                1 -> track.recommendationKey() == pick.track?.recommendationKey()
                else -> false
            } }.takeIf { it >= 0 } ?: 100
            minOf(memory * 3, recentRank * 2)
        }
    }
    // History can influence each category, but cannot bury every other category in the first grid.
    return (0 until 16).flatMap { i -> listOf(1000, 100, 1, 10).mapNotNull { grouped[it]?.getOrNull(i) } }.take(48)
}

internal fun coverIdentity(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return try {
        val uri = java.net.URI(url)
        val path = uri.path.orEmpty()
        if (uri.host.orEmpty().endsWith("music.126.net")) "ncm:" + path.substringAfterLast('/')
        else {
            val ignored = setOf("param", "w", "h", "width", "height", "quality", "thumbnail", "imageView")
            val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() && it.substringBefore('=') !in ignored }.sorted().joinToString("&")
            "${uri.host.orEmpty()}$path?$query"
        }
    } catch (_: Exception) { url.substringBefore('#') }
}

/** Choose an unused image from each category, so album/song duplicates do not erase a whole tab. */
internal fun visibleQuickPicks(picks: List<QuickPick>, filter: Int, limit: Int = 9): List<QuickPick> {
    val groups = picks.filter { filter == 0 || it.kind == filter }.groupBy { it.kind }.mapValues { it.value.toMutableList() }
    val seen = mutableSetOf<String>(); val result = mutableListOf<QuickPick>()
    while (result.size < limit) {
        var added = false
        for (kind in if (filter == 0) listOf(1000, 100, 1, 10) else listOf(filter)) {
            val group = groups[kind] ?: continue
            val pick = group.firstOrNull { coverIdentity(it.image)?.let { image -> image !in seen } != false } ?: continue
            group.remove(pick); coverIdentity(pick.image)?.let(seen::add); result += pick; added = true
            if (result.size == limit) break
        }
        if (!added) break
    }
    return result
}

@Composable
internal fun QuickPickGrid(picks: List<QuickPick>, choose: (QuickPick) -> Unit, openPicker: (Int) -> Unit, cellHeight: androidx.compose.ui.unit.Dp? = null, maxItems: Int = 9) {
    var filter by remember { mutableIntStateOf(0) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("一键点歌", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        IconButton(onClick = { openPicker(if (filter == 0) 1 else filter) }) { Icon(Icons.Default.Add, "查找点歌内容") }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("全部" to 0, "歌手" to 100, "歌单" to 1000, "专辑" to 10, "单曲" to 1).forEach { (label, value) ->
            Box(Modifier.clip(RoundedCornerShape(7.dp)).background(if (filter == value) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { filter = value }.padding(horizontal = 16.dp, vertical = 9.dp)) {
                Text(label, color = if (filter == value) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    val visible = remember(picks, filter, maxItems) { visibleQuickPicks(picks, filter, maxItems) }
    if (visible.isEmpty()) OutlinedButton(onClick = { openPicker(if (filter == 0) 1 else filter) }, modifier = Modifier.fillMaxWidth()) { Text(when (filter) { 100 -> "选择歌手"; 1000 -> "选择歌单"; 10 -> "选择专辑"; else -> "选择歌曲" }) }
    visible.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { pick ->
                Box(Modifier.weight(1f).then(if (cellHeight == null) Modifier.aspectRatio(1f) else Modifier.height(cellHeight)).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { choose(pick) }) {
                    AsyncImage(pick.image, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    if (pick.image.isNullOrBlank()) Icon(when (pick.kind) { 100 -> Icons.Default.Person; 1000 -> Icons.Default.QueueMusic; 10 -> Icons.Default.Album; else -> Icons.Default.MusicNote }, null, Modifier.align(Alignment.Center).size(36.dp))
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f)))))
                    Text(pick.title, Modifier.align(Alignment.BottomStart).padding(9.dp), color = Color.White,
                        style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
internal fun RoamPicker(source: String?, initialType: Int, choose: (QuickPick) -> Unit, close: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var query by remember { mutableStateOf("") }
    var type by remember { mutableIntStateOf(initialType) }
    var result by remember { mutableStateOf(MusicSearch()) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var task by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun search() {
        if (query.isBlank()) return
        task?.cancel()
        task = scope.launch {
            working = true; error = null
            try { result = MusicRepository(context, requireNotNull(source) { "请先添加音源" }).search(query, type) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "搜索失败" }
            finally { working = false }
        }
    }
    SettingsPanel("一键点歌", close) {
        MusicSegments(listOf("歌手", "歌单", "专辑", "单曲"), listOf(100,1000,10,1).indexOf(type).coerceAtLeast(0)) { type = listOf(100,1000,10,1)[it]; result = MusicSearch(); search() }
        Spacer(Modifier.height(12.dp))
        MusicSearch(query, { query = it }, ::search, { query = ""; task?.cancel(); result = MusicSearch() })
        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LazyColumn(Modifier.weight(1f)) {
            items(result.entries, key = { "${it.kind}:${it.id}" }) { entry -> MusicEntryRow(entry) {
                choose(QuickPick("${entry.kind}:${entry.id}", entry.name, entry.image, entry)); close()
            } }
            items(result.tracks, key = { "${it.source}:${it.song.id}" }) { track -> SongRow(track, false, {
                choose(QuickPick("song:${track.remoteId ?: track.song.id}", track.song.name, track.song.albumArtUrl, track = track)); close()
            }) }
        }
    }
}
