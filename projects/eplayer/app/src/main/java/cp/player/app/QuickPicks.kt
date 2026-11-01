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

internal fun quickPicks(home: CloudHome, recent: List<Track>, saved: List<QuickPick>): List<QuickPick> {
    val source = home.source ?: return emptyList()
    val tracks = recent.filter { it.source == source }
    val artists = tracks.mapNotNull { track -> track.song.artistId?.takeIf { it.isNotBlank() }?.let {
        QuickPick("artist:$it", track.song.artist, track.song.albumArtUrl, MusicEntry(it, track.song.artist, source, MusicKind.ARTIST))
    } }.distinctBy { it.key }.take(4)
    val albums = tracks.mapNotNull { track -> track.song.albumId?.takeIf { it.isNotBlank() }?.let {
        QuickPick("album:$it", track.song.album, track.song.albumArtUrl, MusicEntry(it, track.song.album, source, MusicKind.ALBUM))
    } }.distinctBy { it.key }.take(4)
    val playlists = home.playlists.take(6).map { QuickPick("playlist:${it.id}", if (it.special) "我喜欢的音乐" else it.name, it.image, it) }
    val singles = tracks.take(6).map { QuickPick("song:${it.remoteId ?: it.song.id}", it.song.name, it.song.albumArtUrl, track = it) }
    val mixed = (0 until 6).flatMap { i -> listOfNotNull(playlists.getOrNull(i), artists.getOrNull(i), albums.getOrNull(i), singles.getOrNull(i)) }
    return (saved.filter { it.source == source } + mixed).distinctBy { it.key.lowercase() }.take(24)
}

@Composable
internal fun QuickPickGrid(picks: List<QuickPick>, choose: (QuickPick) -> Unit, openPicker: (Int) -> Unit) {
    var filter by remember { mutableIntStateOf(0) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("一键点歌", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
        IconButton(onClick = { openPicker(if (filter == 0) 1 else filter) }) { Icon(Icons.Default.Add, "添加漫游种子") }
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
    val visible = picks.filter { filter == 0 || it.kind == filter }.take(9)
    if (visible.isEmpty()) OutlinedButton(onClick = { openPicker(if (filter == 0) 1 else filter) }, modifier = Modifier.fillMaxWidth()) { Text(when (filter) { 100 -> "选择歌手"; 1000 -> "选择歌单"; 10 -> "选择专辑"; else -> "选择歌曲" }) }
    visible.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { pick ->
                Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { choose(pick) }) {
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
