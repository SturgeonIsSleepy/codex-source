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
    val providers by model.providers.collectAsState()
    val active by model.activeSource.collectAsState()
    val sourceIssue by model.sourceIssue.collectAsState()
    val importing by model.importing.collectAsState()
    val message by model.message.collectAsState()
    val cloudMessage by cloud.message.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(model.initialTab) }
    val discovery = page == 1
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var roamPicker by remember { mutableStateOf<Int?>(null) }
    val savedPicks by model.roamHistory.collectAsState()
    val busy by cloud.busy.collectAsState()
    val picks = remember(home.playlists, home.source, recent, savedPicks) { quickPicks(home, recent, savedPicks) }
    fun roam(pick: QuickPick) { cloud.roam(pick) { tracks -> model.startRoaming(pick, tracks); onShowPlayer(true) } }
    var query by rememberSaveable { mutableStateOf("") }
    var libraryMode by rememberSaveable { mutableIntStateOf(0) }
    var createPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var privatePlaylist by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<Pair<Track, MusicEntry?>?>(null) }
    var selectedQueueIndex by remember { mutableStateOf<Int?>(null) }
    fun openQueueOptions(index: Int) {
        model.currentQueueTracks().getOrNull(index)?.let { selectedQueueIndex = index; selectedTrack = it to null }
    }
    var commentTrack by remember { mutableStateOf<Track?>(null) }
    var pendingDelete by remember { mutableStateOf<BackendProvider?>(null) }
    var pendingTrackDelete by remember { mutableStateOf<Track?>(null) }
    var logout by remember { mutableStateOf(false) }
    var settingsPath by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val settingsSection = settingsPath.lastOrNull()
    fun openSettings(section: String) { settingsPath = openSettingsPath(settingsPath, section) }
    fun backSettings() { settingsPath = previousSettingsPath(settingsPath) }
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
        cloud.ensureLibrary()
        if (page == 0) model.refreshPlaybackSnapshot()
    }
    DisposableEffect(discovery) {
        cloud.setDiscoveryVisible(discovery)
        onDispose { if (discovery) cloud.setDiscoveryVisible(false) }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.message.value = null } }
    LaunchedEffect(cloudMessage) { cloudMessage?.let { snackbar.showSnackbar(it); cloud.message.value = null } }

    var revealingBack by remember { mutableStateOf(false) }
    val revealBack: (Boolean) -> Unit = remember { { revealingBack = it } }
    val chromeVisible = (!showPlayer && detailPages.isEmpty() && !playbackSettings && !visualSettings && settingsSection == null && !searchOpen) || revealingBack
    @Composable
    fun RootContents() {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().testTag("root-content-$page"), state = lists[page],
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = if (state.song != null) 132.dp else 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { AppHeader(when (page) { 1 -> "探索"; 2 -> "媒体库"; else -> "ePlayer" }, home.account?.avatar,
                { openSettings("profile") }, { searchOpen = true }) }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            when (page) {
                0 -> {
                    item { ListeningSummary(stats) }
                    item { Column { QuickPickGrid(picks, ::roam) { roamPicker = it } } }
                }
                1 -> {
                    if (home.source == null) item { SettingsRow("添加音源") { openSettings("source") } }
                    else {
                        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("新发行" to "new", "排行榜" to "charts", "心情与流派" to "genres").forEach { (label, id) ->
                                Column(Modifier.weight(1f).height(112.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .clickable { cloud.open(MusicEntry(id, label, home.source!!, if (id == "charts") MusicKind.CHARTS else MusicKind.BROWSE)) }
                                    .padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                    Icon(when (id) { "new" -> Icons.Default.NewReleases; "charts" -> Icons.Default.TrendingUp; else -> Icons.Default.Mood }, null)
                                    Text(label, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        } }
                        item { Text("为你推荐", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp)) }
                        if (home.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        home.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                        items(home.recommendations.chunked(2)) { entries -> RecommendationRow(entries, cloud::open) }
                    }
                }
                else -> {
                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { MusicSegments(listOf("歌单", "歌曲", "最近"), libraryMode) { libraryMode = it } }
                        IconButton(onClick = { importAudio.launch(arrayOf("audio/*")) }) { Icon(Icons.Default.Add, "导入音乐") }
                    } }
                            when (libraryMode) {
                                0 -> {
                                    item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(Modifier.weight(1f))
                                        IconButton(onClick = cloud::refresh) { Icon(Icons.Default.Refresh, "刷新账号和歌单", tint = MusicRed) }
                                        if (home.account != null) IconButton(onClick = { createPlaylist = true }) { Icon(Icons.Default.PlaylistAdd, "创建歌单", tint = MusicRed) }
                                    } }
                                    if (home.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                                    if (home.account == null) item { SettingsRow("登录网易云") { openSettings("account") } }
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
        }
    }
    CompositionLocalProvider(LocalBackPreviewVisibility provides revealBack) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ThemeBackdrop(Modifier.matchParentSize())
            key(page) {
                val entrance = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(Unit) { entrance.animateTo(1f, androidx.compose.animation.core.tween(150)) }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = .84f + entrance.value * .16f; translationY = (1 - entrance.value) * 7.dp.toPx() }) { RootContents() }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = if (chromeVisible) 1f else 0f }.navigationBarsPadding()) {
            MusicDock(state, page, discovery, { target ->
                if (target != page) { query = ""; cloud.clearSearch(); if (target == 2) libraryMode = 0 }
                page = target; searchOpen = false
            },
                { onShowPlayer(true) }, model::playPause, model::next, { queueOpen = true })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp))
    }
    settingsSection?.let { section ->
        SettingsPanel(when (section) { "profile" -> "账号"; "settings" -> "设置"; "account" -> "账号"; "source" -> "音源"; else -> "关于" }, ::backSettings) {
          key(settingsPath.size, section) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                when (section) {
                    "profile" -> {
                        Row(Modifier.fillMaxWidth().clickable { openSettings("account") }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (home.account?.avatar != null) coil3.compose.AsyncImage(home.account!!.avatar, null, Modifier.size(52.dp).clip(CircleShape))
                            else Icon(Icons.Default.AccountCircle, null, Modifier.size(52.dp))
                            Text(home.account?.name ?: "登录网易云音乐", Modifier.weight(1f).padding(start = 14.dp), style = MaterialTheme.typography.titleLarge)
                            Icon(Icons.Default.ChevronRight, null)
                        }
                        HorizontalDivider()
                        SettingsRow("下载内容") { settingsPath = emptyList(); page = 2; libraryMode = 1 }
                        SettingsRow("历史记录") { settingsPath = emptyList(); page = 2; libraryMode = 2 }
                        SettingsRow("设置") { openSettings("settings") }
                    }
                    "settings" -> {
                        SettingsRow("音源") { openSettings("source") }
                        SettingsRow("播放设置") { playbackSettings = true }
                        SettingsRow("画面设置") { visualSettings = true }
                        SettingsRow("关于", BuildConfig.VERSION_NAME) { openSettings("about") }
                    }
                    "account" -> {
                        home.account?.let { Text(it.name, style = MaterialTheme.typography.titleLarge) }
                        val source = active
                        if (source != null) SourceLogin(source.id)
                        else SettingsCard { SettingsRow("添加音源") { openSettings("source") } }
                        if (home.account != null) TextButton(onClick = { logout = true }) { Text("退出登录") }
                    }
                    "source" -> {
                        FilledTonalButton(onClick = { importSource.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("导入音源") }
                        sourceIssue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (providers.isNotEmpty()) SettingsCard {
                            providers.forEachIndexed { index, source ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                                Row(Modifier.fillMaxWidth().clickable { model.selectSource(source) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (source.id == "netease-cloudmusic-rs") "网易云音乐" else source.name, Modifier.weight(1f))
                                    if (active?.id == source.id) Icon(Icons.Default.Check, "当前音源", tint = MusicRed)
                                    IconButton(onClick = { pendingDelete = source }) { Icon(Icons.Default.RemoveCircleOutline, "移除 ${source.name}", tint = MaterialTheme.colorScheme.outline) }
                                }
                            }
                        }
                    }
                    else -> {
                        Text("ePlayer", style = MaterialTheme.typography.headlineLarge)
                        Text(BuildConfig.VERSION_NAME)
                        Text("基于 CPPlayer · MIT")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    }
    if (searchOpen) SearchScreen(query, { query = it }, cloud, model, { onShowPlayer(true) }, { track -> selectedTrack = track to null }) {
        searchOpen = false; query = ""; cloud.clearSearch()
    }
    roamPicker?.let { type -> RoamPicker(home.source, type, ::roam) { roamPicker = null } }
    if (queueOpen) QueueSheet(state, model::queueIndex, model::moveQueue, model::removeQueue, model::clearQueue, removeSelected = model::removeQueueItems, options = ::openQueueOptions) { queueOpen = false }
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
    CloudDetailScreen(cloud, model, { onShowPlayer(true) }, selectedTab = page, queueOptions = ::openQueueOptions, chooseTab = { tab ->
        page = tab; searchOpen = false; query = ""; cloud.clearSearch()
        if (tab == 2) libraryMode = 0
    }) { track, playlist -> selectedTrack = track to playlist }
    if (showPlayer) PlayerScreen(model, cloud, state, { selectedTrack = model.currentTrack()?.let { it to null } }, { commentTrack = model.currentTrack() }, queueOptions = ::openQueueOptions) { onShowPlayer(false) }
    selectedTrack?.let { (track, playlist) -> SongOptions(track, playlist, cloud, model, { commentTrack = it }, { entry -> onShowPlayer(false); cloud.open(entry) }, removeFromQueue = selectedQueueIndex?.let { index -> {
        if (model.currentQueueTracks().getOrNull(index) == track) model.removeQueue(index) else model.message.value = "播放列表已变化，请重新打开"
    } }) { selectedTrack = null; selectedQueueIndex = null } }
    commentTrack?.let { track -> CommentsSheet(track) { commentTrack = null } }
    }
}

@Composable
private fun ListeningSummary(stats: ListeningStats) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("近 7 天", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${stats.minutes}", fontSize = 44.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold)
            Text("分钟", Modifier.padding(start = 8.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("${stats.songs} 首  ·  ${stats.activeDays} 天", Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val maximum = (stats.days.maxOfOrNull { it.milliseconds } ?: 0).coerceAtLeast(60000)
        Row(Modifier.fillMaxWidth().height(84.dp).padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
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
internal fun SongRow(track: Track, selected: Boolean, play: () -> Unit, enabled: Boolean = true, interaction: Boolean = true, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().then(if (interaction) Modifier.clickable(enabled = enabled, onClick = play) else Modifier).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        MusicCover(track.song, Modifier.size(40.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.song.name, style = MaterialTheme.typography.titleMedium, color = if (selected) MusicRed else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (track.song.artist.isNotBlank()) Text(track.song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}
