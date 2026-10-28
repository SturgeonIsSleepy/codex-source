package cp.player.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import cp.player.BuildConfig
import cp.player.manager.WitchPlaylist
import cp.player.provider.BackendProvider
import cp.player.ui.glass.LiquidGlass
import cp.player.ui.glass.recordGlassScene

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EPlayerApp(model: EPlayerViewModel, cloud: CloudViewModel, showPlayer: Boolean, onShowPlayer: (Boolean) -> Unit) {
    val state by model.playback.collectAsState()
    val library by model.library.collectAsState()
    val recent by model.recent.collectAsState()
    val stats by model.listeningStats.collectAsState()
    val home by cloud.home.collectAsState()
    val found by cloud.searchResult.collectAsState()
    val searched by cloud.searched.collectAsState()
    val searching by cloud.searching.collectAsState()
    val searchType by cloud.searchType.collectAsState()
    val searchError by cloud.searchError.collectAsState()
    val providers by model.providers.collectAsState()
    val active by model.activeSource.collectAsState()
    val sourceIssue by model.sourceIssue.collectAsState()
    val importing by model.importing.collectAsState()
    val message by model.message.collectAsState()
    val cloudMessage by cloud.message.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var discovery by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var libraryMode by rememberSaveable { mutableIntStateOf(1) }
    var createPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var privatePlaylist by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<Pair<Track, MusicEntry?>?>(null) }
    var commentTrack by remember { mutableStateOf<Track?>(null) }
    var pendingDelete by remember { mutableStateOf<BackendProvider?>(null) }
    var pendingTrackDelete by remember { mutableStateOf<Track?>(null) }
    var logout by remember { mutableStateOf(false) }
    var playbackSettings by remember { mutableStateOf(false) }
    var witchTarget by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scene = rememberGraphicsLayer()
    val lists = remember { List(3) { LazyListState() } }
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), model::importAudio)
    val importSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importSource) }
    val importWitch = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.importWitch(witchTarget, it) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) model.scanLibrary() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) model.setVisible(true)
            if (event == Lifecycle.Event.ON_STOP) model.setVisible(false)
        }
        lifecycle.addObserver(observer)
        model.setVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); model.setVisible(false) }
    }
    LaunchedEffect(page) { if (page != 0) cloud.ensureLibrary() }
    DisposableEffect(discovery) {
        cloud.setDiscoveryVisible(discovery)
        onDispose { if (discovery) cloud.setDiscoveryVisible(false) }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.message.value = null } }
    LaunchedEffect(cloudMessage) { cloudMessage?.let { snackbar.showSnackbar(it); cloud.message.value = null } }
    BackHandler(enabled = discovery && !showPlayer) { discovery = false }
    BackHandler(enabled = !discovery && page != 0 && !showPlayer && !searched) { page = 0 }
    BackHandler(enabled = searched && !showPlayer) { query = ""; cloud.clearSearch() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().recordGlassScene(scene)) {
            LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), state = if (discovery) rememberLazyListState() else lists[page],
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = if (state.song != null) 192.dp else 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (discovery) {
                    item { Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { discovery = false }) { Icon(Icons.Default.ChevronLeft, "返回首页", tint = MusicRed) }
                        Text("网易云", style = MaterialTheme.typography.headlineLarge)
                    } }
                    if (home.source == null) item { SettingsRow("添加音源") { discovery = false; page = 2 } }
                    else {
                        item { CloudShortcuts(cloud) { tracks -> model.play(tracks.first(), tracks, radio = true); onShowPlayer(true) } }
                        if (home.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        home.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                        items(home.recommendations.chunked(2)) { entries -> RecommendationRow(entries, cloud::open) }
                    }
                } else when (page) {
                    0 -> {
                        item { MusicHeading("首页") }
                        item { ListeningSummary(stats) }
                        item { Text("继续播放", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 18.dp)) }
                        item {
                            val track = state.song?.let { Track(it, source = state.source, remoteId = state.remoteId) } ?: recent.firstOrNull()
                            if (track == null) SettingsRow("前往曲库") { page = 1 }
                            else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow).clickable {
                                    if (state.song == null) model.play(track, recent)
                                    else if (!state.playRequested) model.playPause()
                                    onShowPlayer(true)
                                }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                MusicCover(track.song, Modifier.size(76.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                    Text(track.song.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(track.song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (state.position > 0) Text(playerTime(state.position), style = MaterialTheme.typography.labelMedium, color = MusicRed)
                                }
                                Icon(Icons.Default.PlayArrow, "继续播放", Modifier.size(30.dp), tint = MusicRed)
                            }
                        }
                        item { Spacer(Modifier.height(12.dp)); SettingsRow("网易云") { discovery = true } }
                    }
                    1 -> {
                        item { MusicHeading("曲库") {
                            IconButton(onClick = { importAudio.launch(arrayOf("audio/*")) }) { Icon(Icons.Default.Add, "导入音乐", tint = MusicRed) }
                        } }
                        item { MusicSearch(query, { query = it }, { cloud.search(query) }, { query = ""; cloud.clearSearch() }) }
                        if (searched) {
                            item { MusicSegments(listOf("歌曲", "歌单", "专辑", "歌手"), listOf(1,1000,10,100).indexOf(searchType).coerceAtLeast(0)) {
                                cloud.search(query, listOf(1,1000,10,100)[it])
                            } }
                            if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            items(found.entries, key = { "${it.kind}:${it.id}" }) { entry -> MusicEntryRow(entry) { cloud.open(entry) } }
                            items(found.tracks, key = { "${it.source}:${it.song.id}" }) { track ->
                                SongRow(track, track.song.id == state.song?.id, { model.play(track, found.tracks); onShowPlayer(true) }) {
                                    IconButton(onClick = { selectedTrack = track to null }) { Icon(Icons.Default.MoreHoriz, "歌曲操作") }
                                }
                            }
                            if (!searching && found.tracks.isEmpty() && found.entries.isEmpty()) item { Text(searchError ?: "没有找到音乐", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            item { MusicSegments(listOf("歌单", "歌曲", "最近"), libraryMode) { libraryMode = it } }
                            when (libraryMode) {
                                0 -> {
                                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text(home.account?.name ?: "我的音乐", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                        IconButton(onClick = cloud::refresh) { Icon(Icons.Default.Refresh, "刷新账号和歌单", tint = MusicRed) }
                                        if (home.account != null) IconButton(onClick = { createPlaylist = true }) { Icon(Icons.Default.PlaylistAdd, "创建歌单", tint = MusicRed) }
                                    } }
                                    if (home.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                                    if (home.account == null) item { SettingsRow("登录网易云") { page = 2 } }
                                    home.account?.let { account ->
                                        item { SettingsRow("我喜欢的音乐") { cloud.open(MusicEntry(account.id, "我喜欢的音乐", home.source!!, MusicKind.LIKED)) } }
                                        item { SettingsRow("音乐云盘") { cloud.open(MusicEntry(account.id, "音乐云盘", home.source!!, MusicKind.CLOUD)) } }
                                    }
                                    items(home.playlists, key = { it.id }) { entry -> MusicEntryRow(entry) { cloud.open(entry) } }
                                }
                                1 -> {
                                    item { Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("魔女の旅々", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                        TextButton(onClick = { permission.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) }) { Text("扫描") }
                                    } }
                                    items(WitchPlaylist.songs.indices.toList()) { index ->
                                        val song = WitchPlaylist.songs[index]
                                        val available = remember(importing, library) { model.witchTracks() }
                                        val track = available.find { it.song.id == song.id }
                                        SongRow(Track(song), state.song?.id == song.id, {
                                            if (track == null) { witchTarget = index; importWitch.launch(arrayOf("audio/*")) }
                                            else { model.play(track, available); onShowPlayer(true) }
                                        }) { TextButton(enabled = !importing, onClick = { witchTarget = index; importWitch.launch(arrayOf("audio/*")) }) { Text(if (track == null) "导入" else "更换") } }
                                    }
                                    if (library.isNotEmpty()) item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                                    items(library, key = { it.song.id }) { track -> SongRow(track, state.song?.id == track.song.id, { model.play(track, library); onShowPlayer(true) }) {
                                        IconButton(onClick = { if (track.song.id.startsWith("import_")) pendingTrackDelete = track else selectedTrack = track to null }) { Icon(Icons.Default.MoreHoriz, "歌曲操作") }
                                    } }
                                    item { DownloadsStatus(model) }
                                }
                                else -> items(recent, key = { "${it.source}:${it.song.id}" }) { track -> SongRow(track, state.song?.id == track.song.id, { model.play(track, recent); onShowPlayer(true) }) {
                                    IconButton(onClick = { selectedTrack = track to null }) { Icon(Icons.Default.MoreHoriz, "歌曲操作") }
                                } }
                            }
                        }
                    }
                    else -> {
                        item { MusicHeading("设置") }
                        item { Text(home.account?.name ?: "账号与音源", style = MaterialTheme.typography.titleLarge) }
                        active?.let { source -> item { SourceLogin(source.id) } }
                        if (home.account != null) item { TextButton(onClick = { logout = true }) { Text("退出登录") } }
                        item { QualitySetting(model); HorizontalDivider(); SettingsRow("播放与声音") { playbackSettings = true } }
                        item { Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("音源", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = { importSource.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("导入") }
                        } }
                        sourceIssue?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                        items(providers, key = { it.id }) { source -> Row(Modifier.fillMaxWidth().clickable { model.selectSource(source) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (source.id == "netease-cloudmusic-rs") "网易云音乐" else source.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (active?.id == source.id) Icon(Icons.Default.Check, "当前音源", tint = MusicRed)
                            IconButton(onClick = { pendingDelete = source }) { Icon(Icons.Default.RemoveCircleOutline, "移除 ${source.name}", tint = MaterialTheme.colorScheme.outline) }
                        } }
                        item { HorizontalDivider(); Text("ePlayer ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)) }
                    }
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.song != null) LiquidGlass(scene, Modifier.fillMaxWidth().height(62.dp), dark) {
                Row(Modifier.fillMaxSize().clickable { onShowPlayer(true) }.padding(start = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MusicCover(state.song, Modifier.size(42.dp))
                    Text(state.song!!.name, Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = model::playPause) { Icon(if (state.playRequested) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playRequested) "暂停" else "播放") }
                    IconButton(onClick = model::next) { Icon(Icons.Default.SkipNext, "下一首") }
                }
            }
            LiquidGlass(scene, Modifier.fillMaxWidth().height(66.dp), dark) {
                Row(Modifier.fillMaxSize().padding(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("首页" to Icons.Default.Home, "曲库" to Icons.Default.LibraryMusic, "设置" to Icons.Default.Settings).forEachIndexed { index, (label, icon) ->
                        Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(40.dp))
                            .background(if (page == index && !discovery) MaterialTheme.colorScheme.surface.copy(alpha = .65f) else Color.Transparent)
                            .clickable { page = index; discovery = false }
                            .semantics { contentDescription = label; role = Role.Tab; selected = page == index && !discovery },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(icon, null, Modifier.size(24.dp), tint = if (page == index && !discovery) MusicRed else MaterialTheme.colorScheme.onSurface)
                            Text(label, fontSize = 11.sp, color = if (page == index && !discovery) MusicRed else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp))
    }
    if (playbackSettings) Settings(model) { playbackSettings = false }
    pendingDelete?.let { source -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("移除 ${source.name}？") },
        confirmButton = { TextButton(onClick = { model.removeSource(source); pendingDelete = null }) { Text("移除") } }, dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }) }
    pendingTrackDelete?.let { track -> AlertDialog(onDismissRequest = { pendingTrackDelete = null }, title = { Text("删除 ${track.song.name}？") },
        confirmButton = { TextButton(onClick = { model.removeImported(track); pendingTrackDelete = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { pendingTrackDelete = null }) { Text("取消") } }) }
    if (createPlaylist) AlertDialog(onDismissRequest = { createPlaylist = false }, title = { Text("创建歌单") }, text = { Column {
        OutlinedTextField(playlistName, { playlistName = it }, singleLine = true, placeholder = { Text("歌单名称") })
        Row(verticalAlignment = Alignment.CenterVertically) { Text("私密", Modifier.weight(1f)); Switch(privatePlaylist, { privatePlaylist = it }) }
    } }, confirmButton = { TextButton(enabled = playlistName.isNotBlank(), onClick = { cloud.create(playlistName, privatePlaylist); createPlaylist = false; playlistName = "" }) { Text("创建") } }, dismissButton = { TextButton(onClick = { createPlaylist = false }) { Text("取消") } })
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("退出当前账号？") }, confirmButton = { TextButton(onClick = { cloud.logout(); logout = false }) { Text("退出登录") } }, dismissButton = { TextButton(onClick = { logout = false }) { Text("取消") } })
    CloudDetailScreen(cloud, model, { onShowPlayer(true) }) { track, playlist -> selectedTrack = track to playlist }
    if (showPlayer) PlayerScreen(model, cloud, state, { selectedTrack = model.currentTrack()?.let { it to null } }, { commentTrack = model.currentTrack() }) { onShowPlayer(false) }
    selectedTrack?.let { (track, playlist) -> SongOptions(track, playlist, cloud, model, { commentTrack = it }, { entry -> onShowPlayer(false); cloud.open(entry) }) { selectedTrack = null } }
    commentTrack?.let { track -> CommentsSheet(track) { commentTrack = null } }
}

@Composable
private fun ListeningSummary(stats: ListeningStats) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("近 7 天", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${stats.minutes}", fontSize = 54.sp, lineHeight = 62.sp, fontWeight = FontWeight.SemiBold)
            Text("分钟", Modifier.padding(start = 8.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("${stats.songs} 首  ·  ${stats.activeDays} 天", Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val maximum = (stats.days.maxOfOrNull { it.milliseconds } ?: 0).coerceAtLeast(60000)
        Row(Modifier.fillMaxWidth().height(104.dp).padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            stats.days.forEachIndexed { index, day -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height((4 + day.milliseconds.toFloat() / maximum * 64).dp).clip(RoundedCornerShape(5.dp))
                        .background(if (index == stats.days.lastIndex) MusicRed else MusicRed.copy(alpha = .25f)))
                }
                Text(listOf("一","二","三","四","五","六","日")[day.date.dayOfWeek.value - 1], style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            } }
        }
    }
}

@Composable
internal fun SongRow(track: Track, selected: Boolean, play: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = play).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        MusicCover(track.song, Modifier.size(44.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.song.name, style = MaterialTheme.typography.titleMedium, color = if (selected) MusicRed else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (track.song.artist.isNotBlank()) Text(track.song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}
