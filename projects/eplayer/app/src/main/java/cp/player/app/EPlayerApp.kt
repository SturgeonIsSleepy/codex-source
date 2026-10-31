package cp.player.app

import android.Manifest
import android.os.Build
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cp.player.BuildConfig
import cp.player.provider.BackendProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EPlayerApp(model: EPlayerViewModel, cloud: CloudViewModel, showPlayer: Boolean, onShowPlayer: (Boolean) -> Unit) {
    val state by model.playback.collectAsState()
    val library by model.library.collectAsState()
    val recent by model.recent.collectAsState()
    val stats by model.listeningStats.collectAsState()
    val home by cloud.home.collectAsState()
    val detailPages by cloud.pages.collectAsState()
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
    var page by rememberSaveable { mutableIntStateOf(model.initialTab) }
    var discovery by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var libraryMode by rememberSaveable { mutableIntStateOf(0) }
    var createPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var privatePlaylist by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<Pair<Track, MusicEntry?>?>(null) }
    var commentTrack by remember { mutableStateOf<Track?>(null) }
    var pendingDelete by remember { mutableStateOf<BackendProvider?>(null) }
    var pendingTrackDelete by remember { mutableStateOf<Track?>(null) }
    var logout by remember { mutableStateOf(false) }
    var visualSettings by remember { mutableStateOf(false) }
    var playbackSettings by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lists = remember { List(3) { LazyListState() } }
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), model::importAudio)
    val importSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importSource) }
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
    LaunchedEffect(page, discovery) {
        model.rememberTab(page)
        if (page == 0 && !discovery) model.refreshPlaybackSnapshot()
        else if (page != 0) cloud.ensureLibrary()
    }
    DisposableEffect(discovery) {
        cloud.setDiscoveryVisible(discovery)
        onDispose { if (discovery) cloud.setDiscoveryVisible(false) }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.message.value = null } }
    LaunchedEffect(cloudMessage) { cloudMessage?.let { snackbar.showSnackbar(it); cloud.message.value = null } }
    val backPreview = rememberBackPreview()
    val visibleSearch = page == 1 && !discovery && searched
    PreviewBackHandler(backPreview, enabled = (discovery || visibleSearch) && !showPlayer) {
        when {
            visibleSearch -> { query = ""; cloud.clearSearch() }
            discovery -> discovery = false
            else -> Unit
        }
    }

    var revealingBack by remember { mutableStateOf(false) }
    val revealBack: (Boolean) -> Unit = remember { { revealingBack = it } }
    val chromeVisible = (!showPlayer && detailPages.isEmpty() && !playbackSettings && !visualSettings) || revealingBack
    @Composable
    fun RootContents(destination: Boolean) {
        val screenPage = page
        val screenDiscovery = discovery && !destination
        val screenSearched = visibleSearch && !destination
            LazyColumn(Modifier.fillMaxSize().statusBarsPadding().testTag(if (screenPage == 1) "library-content" else "root-content-$screenPage"), state = if (destination) rememberLazyListState(lists[screenPage].firstVisibleItemIndex, lists[screenPage].firstVisibleItemScrollOffset) else if (discovery) rememberLazyListState() else lists[page],
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = if (state.song != null) 164.dp else 108.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (screenDiscovery) {
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
                } else when (screenPage) {
                    0 -> {
                        item { MusicHeading("首页") }
                        item { ListeningSummary(stats) }
                        item { Text("继续播放", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 18.dp)) }
                        item {
                            val track = state.song?.let { Track(it, source = state.source, remoteId = state.remoteId) } ?: recent.firstOrNull()
                            if (track == null) SettingsRow("前往曲库") { libraryMode = 0; query = ""; cloud.clearSearch(); page = 1 }
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
                        item { MusicSearch(if (destination) "" else query, { query = it }, { cloud.search(query) }, { query = ""; cloud.clearSearch() }) }
                        if (screenSearched) {
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
                                        item { SettingsRow("听歌排行") { cloud.open(MusicEntry(account.id, "听歌排行", home.source!!, MusicKind.RECORD_ALL)) } }
                                    }
                                    items(home.playlists, key = { it.id }) { entry -> MusicEntryRow(entry) { cloud.open(entry) } }
                                    home.account?.let { account ->
                                        item { SettingsRow("音乐云盘") { cloud.open(MusicEntry(account.id, "音乐云盘", home.source!!, MusicKind.CLOUD)) } }
                                    }
                                }
                                1 -> {
                                    item { Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("本地音乐", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                        TextButton(onClick = { permission.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) }) { Text("扫描") }
                                    } }
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
                        home.account?.let { account -> item { Text(account.name, style = MaterialTheme.typography.titleLarge) } }
                        active?.let { source -> item { SourceLogin(source.id) } }
                        if (home.account != null) item { TextButton(onClick = { logout = true }) { Text("退出登录") } }
                        item { QualitySetting(model); HorizontalDivider(); SettingsRow("播放与声音") { playbackSettings = true }; SettingsRow("画面设置") { visualSettings = true } }
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
    CompositionLocalProvider(LocalBackPreviewVisibility provides revealBack) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (backPreview.progress.value > 0f) RootContents(true)
        Box(Modifier.fillMaxSize().backPreview(backPreview).background(MaterialTheme.colorScheme.background)) {
            RootContents(false)
        }
        Box(Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = if (chromeVisible) 1f else 0f }.navigationBarsPadding().padding(horizontal = 18.dp).padding(bottom = 8.dp)) {
            MusicDock(state, page, discovery, { target ->
                if (target == 1 && page != 1) { libraryMode = 0; query = ""; cloud.clearSearch() }
                page = target; discovery = false
            },
                { onShowPlayer(true) }, model::playPause, model::next, { queueOpen = true })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp))
    }
    if (queueOpen) QueueSheet(state, model::queueIndex, model::moveQueue, model::removeQueue, model::clearQueue) { queueOpen = false }
    if (visualSettings) VisualSettings(model) { visualSettings = false }
    if (playbackSettings) Settings(model) { playbackSettings = false }
    pendingDelete?.let { source -> TravelAlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("移除 ${source.name}？") },
        confirmButton = { TextButton(onClick = { model.removeSource(source); pendingDelete = null }) { Text("移除") } }, dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }) }
    pendingTrackDelete?.let { track -> TravelAlertDialog(onDismissRequest = { pendingTrackDelete = null }, title = { Text("删除 ${track.song.name}？") },
        confirmButton = { TextButton(onClick = { model.removeImported(track); pendingTrackDelete = null }) { Text("删除") } }, dismissButton = { TextButton(onClick = { pendingTrackDelete = null }) { Text("取消") } }) }
    if (createPlaylist) TravelAlertDialog(onDismissRequest = { createPlaylist = false }, title = { Text("创建歌单") }, text = { Column {
        OutlinedTextField(playlistName, { playlistName = it }, singleLine = true, placeholder = { Text("歌单名称") })
        Row(verticalAlignment = Alignment.CenterVertically) { Text("私密", Modifier.weight(1f)); Switch(privatePlaylist, { privatePlaylist = it }) }
    } }, confirmButton = { TextButton(enabled = playlistName.isNotBlank(), onClick = { cloud.create(playlistName, privatePlaylist); createPlaylist = false; playlistName = "" }) { Text("创建") } }, dismissButton = { TextButton(onClick = { createPlaylist = false }) { Text("取消") } })
    if (logout) TravelAlertDialog(onDismissRequest = { logout = false }, title = { Text("退出当前账号？") }, confirmButton = { TextButton(onClick = { cloud.logout(); logout = false }) { Text("退出登录") } }, dismissButton = { TextButton(onClick = { logout = false }) { Text("取消") } })
    CloudDetailScreen(cloud, model, { onShowPlayer(true) }) { track, playlist -> selectedTrack = track to playlist }
    if (showPlayer) PlayerScreen(model, cloud, state, { selectedTrack = model.currentTrack()?.let { it to null } }, { commentTrack = model.currentTrack() }) { onShowPlayer(false) }
    selectedTrack?.let { (track, playlist) -> SongOptions(track, playlist, cloud, model, { commentTrack = it }, { entry -> onShowPlayer(false); cloud.open(entry) }) { selectedTrack = null } }
    commentTrack?.let { track -> CommentsSheet(track) { commentTrack = null } }
    }
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
internal fun SongRow(track: Track, selected: Boolean, play: () -> Unit, enabled: Boolean = true, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = play).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        MusicCover(track.song, Modifier.size(44.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.song.name, style = MaterialTheme.typography.titleMedium, color = if (selected) MusicRed else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (track.song.artist.isNotBlank()) Text(track.song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}
