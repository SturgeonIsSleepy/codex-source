package cp.player.app

import cp.player.core.library.Track

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
internal fun CloudShortcuts(cloud: CloudViewModel, playRadio: (List<Track>) -> Unit) {
    val home by cloud.home.collectAsState()
    val source = home.source ?: return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("每日推荐" to Icons.Default.Today, "私人 FM" to Icons.Default.Radio,
            "排行榜" to Icons.Default.Leaderboard, "歌单广场" to Icons.Default.Explore).forEachIndexed { index, item ->
            Column(Modifier.weight(1f).clickable {
                when (index) {
                    0 -> cloud.open(MusicEntry("daily", "每日推荐", source, MusicKind.DAILY))
                    1 -> cloud.radio(playRadio)
                    2 -> cloud.open(MusicEntry("charts", "排行榜", source, MusicKind.CHARTS))
                    else -> cloud.open(MusicEntry("browse", "歌单广场", source, MusicKind.BROWSE))
                }
            }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(item.second, null, Modifier.size(28.dp)); Spacer(Modifier.height(8.dp))
                Text(item.first, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun MusicEntryRow(entry: MusicEntry, open: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(entry.image, null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(if (entry.special) "我喜欢的音乐" else entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            if (entry.subtitle.isNotBlank()) Text(entry.subtitle, maxLines = 1, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
internal fun RecommendationRow(entries: List<MusicEntry>, open: (MusicEntry) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        entries.forEach { entry ->
            Column(Modifier.weight(1f).clickable { open(entry) }) {
                AsyncImage(entry.image, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (entries.size == 1) Spacer(Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CloudDetailScreen(cloud: CloudViewModel, player: EPlayerViewModel, showPlayer: () -> Unit,
    songOptions: (Track, MusicEntry?) -> Unit) {
    val pages by cloud.pages.collectAsState()
    val page = pages.lastOrNull() ?: return
    val home by cloud.home.collectAsState()
    val playback by player.playback.collectAsState()
    val busy by cloud.busy.collectAsState()
    val target = page.data?.entry ?: page.target
    val own = target.owner != null && target.owner == home.account?.id
    var menu by remember(target.id) { mutableStateOf(false) }
    var rename by remember(target.id) { mutableStateOf(false) }
    var delete by remember(target.id) { mutableStateOf(false) }
    var name by remember(target.id, target.name) { mutableStateOf(target.name) }
    TravelDialog(onDismissRequest = cloud::back,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        destination = { pages.getOrNull(pages.lastIndex - 1)?.let { CloudReturnPreview(it) } }) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = cloud::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                    Text(target.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (own && !target.special && target.kind == MusicKind.PLAYLIST) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "歌单操作") }
                        TravelMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; rename = true })
                            DropdownMenuItem(text = { Text("删除歌单") }, onClick = { menu = false; delete = true })
                        }
                    }
                }
                val ranked = target.kind == MusicKind.RECORD_ALL || target.kind == MusicKind.RECORD_WEEK
                if (ranked) Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    MusicSegments(listOf("所有时间", "最近一周"), if (target.kind == MusicKind.RECORD_WEEK) 1 else 0) { period ->
                        val kind = if (period == 1) MusicKind.RECORD_WEEK else MusicKind.RECORD_ALL
                        if (kind != target.kind) cloud.replaceTop(target.copy(kind = kind))
                    }
                }
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                LaunchedEffect(page.target) { listState.scrollToItem(0) }
                LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (target.image != null) item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(target.image, null, Modifier.size(112.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                Text(target.name, style = MaterialTheme.typography.titleLarge)
                                if (target.subtitle.isNotBlank()) Text(target.subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    page.data?.description?.takeIf(String::isNotBlank)?.let { description -> item {
                        var expanded by remember(page.target) { mutableStateOf(false) }
                        Text(description, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { expanded = !expanded })
                    } }
                    val tracks = page.data?.tracks.orEmpty()
                    if (tracks.isNotEmpty()) item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(onClick = { player.play(tracks.first(), tracks); showPlayer() }) {
                                Icon(Icons.Default.PlayArrow, null); Text("播放全部")
                            }
                            Spacer(Modifier.weight(1f))
                            if (target.kind in setOf(MusicKind.PLAYLIST, MusicKind.ALBUM, MusicKind.ARTIST) && !own) TextButton(
                                enabled = !busy, onClick = { cloud.subscribe(target) }) { Text(if (target.subscribed) "取消收藏" else "收藏") }
                        }
                    }
                    if (ranked) {
                        items(tracks.size) { index ->
                            val track = tracks[index]
                            val record = page.data?.records?.getOrNull(index)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("${index + 1}", Modifier.width(30.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                                Box(Modifier.weight(1f)) {
                                    SongRow(track, playback.song?.id == track.song.id, { player.play(track, tracks); showPlayer() }) {
                                        record?.playCount?.takeIf { it > 0 }?.let { Text("${it} 次", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        IconButton(onClick = { songOptions(track, null) }) { Icon(Icons.Default.MoreVert, "歌曲操作") }
                                    }
                                }
                            }
                        }
                        if (!page.loading && page.error == null && page.data != null && tracks.isEmpty()) item {
                            Text("暂无听歌排行", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else items(tracks, key = { "${it.source}:${it.song.id}" }) { track ->
                        SongRow(track, playback.song?.id == track.song.id, { player.play(track, tracks); showPlayer() }) {
                            IconButton(onClick = { songOptions(track, if (own && target.kind == MusicKind.PLAYLIST) target else null) }) { Icon(Icons.Default.MoreVert, "歌曲操作") }
                        }
                    }
                    page.data?.let { data -> if (data.total > data.tracks.size && data.tracks.isNotEmpty()) item {
                        TextButton(enabled = !page.loading, onClick = cloud::more, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                    } }
                    if (page.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    page.error?.let { error -> item {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = cloud::retry) { Text("重试") }
                    } }
                    items(page.data?.entries.orEmpty(), key = { "${it.kind}:${it.id}" }) { entry -> MusicEntryRow(entry) { cloud.open(entry) } }
                }
            }
        }
    }
    if (rename) TravelAlertDialog(onDismissRequest = { rename = false }, title = { Text("重命名歌单") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank() && !busy, onClick = { cloud.rename(target, name); rename = false }) { Text("保存") } },
        dismissButton = { TextButton(onClick = { rename = false }) { Text("取消") } })
    if (delete) TravelAlertDialog(onDismissRequest = { delete = false }, title = { Text("删除 ${target.name}？") },
        confirmButton = { TextButton(enabled = !busy, onClick = { cloud.delete(target); delete = false }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongOptions(track: Track, playlist: MusicEntry?, cloud: CloudViewModel, player: EPlayerViewModel,
    comments: (Track) -> Unit, open: (MusicEntry) -> Unit, close: () -> Unit) {
    val home by cloud.home.collectAsState()
    val likes by cloud.likes.collectAsState()
    var choosePlaylist by remember { mutableStateOf(false) }
    TravelSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            Text(track.song.name, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { player.addNext(track); close() }) { Text("下一首播放") }
            TextButton(onClick = { player.addQueue(track); close() }) { Text("加入播放队列") }
            if (track.source != null) {
                TextButton(onClick = { cloud.like(track) }) { Text(if ((track.remoteId ?: track.song.id) in likes[track.source].orEmpty()) "取消喜欢" else "喜欢") }
                TextButton(onClick = { choosePlaylist = true }) { Text("添加到歌单") }
                TextButton(onClick = { player.download(track); close() }) { Text("下载") }
                TextButton(onClick = { comments(track); close() }) { Text("评论") }
                track.song.artistId?.let { id -> TextButton(onClick = { open(MusicEntry(id, track.song.artist, track.source, MusicKind.ARTIST)); close() }) { Text("查看歌手") } }
                track.song.albumId?.let { id -> TextButton(onClick = { open(MusicEntry(id, track.song.album, track.source, MusicKind.ALBUM)); close() }) { Text("查看专辑") } }
            }
            if (playlist != null) TextButton(onClick = { cloud.removeFromPlaylist(playlist, track); close() }) { Text("从歌单移除") }
        }
    }
    if (choosePlaylist) TravelAlertDialog(onDismissRequest = { choosePlaylist = false }, title = { Text("添加到歌单") }, text = {
        LazyColumn { items(home.playlists.filter { it.owner == home.account?.id && it.source == track.source }) { target ->
            TextButton(onClick = { cloud.addToPlaylist(target, track); choosePlaylist = false; close() }, modifier = Modifier.fillMaxWidth()) { Text(target.name) }
        } }
    }, confirmButton = { TextButton(onClick = { choosePlaylist = false }) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsSheet(track: Track, close: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val source = track.source ?: return
    val repo = remember(source) { MusicRepository(context.applicationContext, source) }
    var comments by remember { mutableStateOf<List<MusicComment>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf<MusicComment?>(null) }
    val scope = rememberCoroutineScope()
    val id = track.remoteId ?: track.song.id
    suspend fun refresh() { comments = repo.comments(id) }
    LaunchedEffect(source, id) {
        try { refresh() } catch (failure: Exception) { error = failure.message }
        finally { loading = false }
    }
    TravelSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).imePadding().padding(horizontal = 20.dp)) {
            Text("评论", style = MaterialTheme.typography.titleLarge)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp)) {
                items(comments, key = { it.id }) { comment ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(comment.user, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { scope.launch {
                                try { repo.likeComment(id, comment.id, !comment.liked); refresh() } catch (failure: Exception) { error = failure.message }
                            } }) { Text("${if (comment.liked) "♥" else "♡"} ${comment.likes}") }
                        }
                        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { reply = comment }) { Text("回复") }
                    }
                }
            }
            reply?.let { selected -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("回复 ${selected.user}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { reply = null }) { Icon(Icons.Default.Close, "取消回复") }
            } }
            Row(Modifier.padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("写评论") }, maxLines = 4)
                TextButton(enabled = text.isNotBlank() && !loading, onClick = { scope.launch {
                    loading = true
                    try { repo.sendComment(id, text.trim(), reply?.id); text = ""; reply = null; refresh() }
                    catch (failure: Exception) { error = failure.message }
                    finally { loading = false }
                } }) { Text("发送") }
            }
        }
    }
}

@Composable
private fun CloudReturnPreview(page: DetailPage) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
            Text(page.target.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 16.dp))
            LazyColumn {
                items(page.data?.entries.orEmpty()) { entry -> MusicEntryRow(entry) {} }
                items(page.data?.tracks.orEmpty()) { track -> SongRow(track, false, {}) }
            }
        }
    }
}
