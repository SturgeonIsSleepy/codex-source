package cp.player.app

import android.Manifest
import android.os.Build
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import cp.player.BuildConfig
import cp.player.manager.WitchPlaylist
import cp.player.provider.BackendProvider
import cp.player.ui.artwork.WitchPlayerView
import cp.player.util.UserPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EPlayerApp(model: EPlayerViewModel, showPlayer: Boolean, onShowPlayer: (Boolean) -> Unit) {
    val state by model.playback.collectAsState()
    val library by model.library.collectAsState()
    val results by model.results.collectAsState()
    val searching by model.searching.collectAsState()
    val searched by model.searched.collectAsState()
    val searchFailure by model.searchFailure.collectAsState()
    val downloading by model.downloading.collectAsState()
    val importing by model.importing.collectAsState()
    val providers by model.providers.collectAsState()
    val active by model.activeSource.collectAsState()
    val sourceIssue by model.sourceIssue.collectAsState()
    val favorites by model.favorites.collectAsState()
    val message by model.message.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BackendProvider?>(null) }
    var pendingTrackDelete by remember { mutableStateOf<Track?>(null) }
    var witchTarget by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listStates = remember { List(3) { androidx.compose.foundation.lazy.LazyListState() } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) model.setVisible(true)
            if (event == Lifecycle.Event.ON_STOP) model.setVisible(false)
        }
        lifecycle.addObserver(observer)
        model.setVisible(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); model.setVisible(false) }
    }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.message.value = null } }
    val importAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), model::importAudio)
    val importSource = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importSource) }
    val importWitch = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> model.importWitch(witchTarget, uri) } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        if (allowed) model.scanLibrary() else model.message.value = "未获得音频读取权限"
    }
    BackHandler(enabled = page != 0 && !showPlayer) { page = 0 }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (BuildConfig.INTERNAL_BUILD) "ePlayer 自用" else "ePlayer", style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "设置") }
            }
        },
        bottomBar = {
            Column {
                state.song?.let { song ->
                    Surface(onClick = { onShowPlayer(true) }, color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                if (song.artist.isNotBlank()) Text(song.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = model::playPause) {
                                if (state.buffering) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                else Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (state.playing) "暂停" else "播放")
                            }
                            IconButton(onClick = model::next, enabled = state.queue.size > 1) { Icon(Icons.Default.SkipNext, "下一首") }
                        }
                    }
                }
                NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                    listOf("旅途" to Icons.Default.Explore, "曲库" to Icons.Default.LibraryMusic, "音源" to Icons.Default.Radio).forEachIndexed { index, item ->
                        NavigationBarItem(selected = page == index, onClick = { page = index },
                            icon = { Icon(item.second, null) }, label = { Text(item.first) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (page == 1) FloatingActionButton(onClick = { importAudio.launch(arrayOf("audio/*")) }) { Icon(Icons.Default.Add, "导入音频") }
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listStates[page], contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (page == 0) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(query, { query = it }, placeholder = { Text("搜索音乐") }, singleLine = true,
                            modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { model.search(query) }),
                            trailingIcon = { IconButton(onClick = { model.search(query) }) { Icon(Icons.Default.Search, "搜索") } })
                    }
                }
                if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (results.isNotEmpty()) {
                    items(results, key = { "${it.source}:${it.song.id}" }) { track ->
                        SongRow(track, state.song?.id == track.song.id, { model.play(track, results); onShowPlayer(true) }) {
                            IconButton(enabled = "${track.source}:${track.song.id}" !in downloading, onClick = { model.download(track) }) {
                                if ("${track.source}:${track.song.id}" in downloading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.Download, "下载 ${track.song.name}")
                            }
                        }
                    }
                } else if (searched) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (!searching) Text(searchFailure ?: "没有找到歌曲")
                            TextButton(onClick = { query = ""; model.clearSearch() }) { Text("返回旅途") }
                        }
                    }
                } else {
                    item {
                        Box(Modifier.fillMaxWidth().height(150.dp)) {
                            AsyncImage("file:///android_asset/travel-sky.png", null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                            Text("旅途随声而行", style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp))
                        }
                    }
                    item {
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(Modifier.padding(16.dp)) {
                                Text("魔女の旅々", style = MaterialTheme.typography.headlineSmall)
                                WitchPlaylist.songs.forEachIndexed { index, song ->
                                    val available = remember(importing, library) { model.witchTracks() }
                                    val track = available.find { it.song.id == song.id }
                                    SongRow(Track(song), state.song?.id == song.id, {
                                        if (track != null) { model.play(track, available); onShowPlayer(true) }
                                        else { witchTarget = index; importWitch.launch(arrayOf("audio/*")) }
                                    }) {
                                        TextButton(enabled = !importing, onClick = { witchTarget = index; importWitch.launch(arrayOf("audio/*")) }) {
                                            Text(if (track == null) "导入" else "更换")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (library.isNotEmpty()) {
                        item { Text("最近加入", style = MaterialTheme.typography.titleLarge) }
                        items(library.take(5), key = { it.song.id }) { track -> SongRow(track, state.song?.id == track.song.id,
                            { model.play(track, library); onShowPlayer(true) }) }
                    }
                }
            } else if (page == 1) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("我的曲库", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        IconToggleButton(onlyFavorites, { onlyFavorites = it }) { Icon(if (onlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "只看收藏") }
                        IconButton(onClick = { permission.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) }) {
                            Icon(Icons.Default.FolderOpen, "读取手机音乐")
                        }
                    }
                }
                if (importing) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                val tracks = if (onlyFavorites) library.filter { it.song.id in favorites } else library
                if (tracks.isEmpty()) item {
                    OutlinedButton(onClick = { importAudio.launch(arrayOf("audio/*")) }, Modifier.fillMaxWidth().height(64.dp)) {
                        Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("导入音乐")
                    }
                }
                items(tracks, key = { it.song.id }) { track ->
                    SongRow(track, state.song?.id == track.song.id, { model.play(track, tracks); onShowPlayer(true) }) {
                        IconButton(onClick = { model.favorite(track.song) }) {
                            Icon(if (track.song.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏 ${track.song.name}")
                        }
                        if (track.song.id.startsWith("import_")) IconButton(onClick = { pendingTrackDelete = track }) { Icon(Icons.Default.DeleteOutline, "删除 ${track.song.name}") }
                    }
                }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("音乐源", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = { importSource.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("导入") }
                    }
                }
                sourceIssue?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
                items(providers, key = { it.id }) { source ->
                    Surface(onClick = { model.selectSource(source) }, shape = RoundedCornerShape(18.dp),
                        color = if (active?.id == source.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(active?.id == source.id, { model.selectSource(source) })
                            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                Text(if (source.id == "netease-cloudmusic-rs") "网易云音乐" else source.name, style = MaterialTheme.typography.titleMedium)
                                Text(source.version, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { pendingDelete = source }) { Icon(Icons.Default.DeleteOutline, "移除 ${source.name}") }
                        }
                    }
                }
                if (active != null) item { SourceLogin(active!!.id) }
            }
        }
    }
    if (settings) Settings(model) { settings = false }
    pendingDelete?.let { source -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("移除 ${source.name}？") },
        confirmButton = { TextButton(onClick = { model.removeSource(source); pendingDelete = null }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } }) }
    pendingTrackDelete?.let { track -> AlertDialog(onDismissRequest = { pendingTrackDelete = null },
        title = { Text("删除 ${track.song.name}？") },
        confirmButton = { TextButton(onClick = { model.removeImported(track); pendingTrackDelete = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { pendingTrackDelete = null }) { Text("取消") } }) }
    if (showPlayer) Player(model, state) { onShowPlayer(false) }
}

@Composable
private fun SongRow(track: Track, selected: Boolean, play: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = play).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selected) { Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(track.song.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (track.song.artist.isNotBlank()) Text(track.song.artist, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Player(model: EPlayerViewModel, state: Playback, close: () -> Unit) {
    val animate by model.animate.collectAsState()
    val artworkContext = LocalContext.current.applicationContext
    val artworkReady by produceState(initialValue = !animate, key1 = animate) {
        if (animate) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { WitchPlayerView.prepareAssets(artworkContext) }
        value = true
    }
    val favorites by model.favorites.collectAsState()
    val currentPosition by model.position.collectAsState()
    DisposableEffect(model) { model.setProgressVisible(true); onDispose { model.setProgressVisible(false) } }
    var queue by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<String?>(null) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.song?.id) { seeking = null }
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close) { Icon(Icons.Default.KeyboardArrowDown, "收起播放器") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { scope.launch { lyrics = model.lyrics() } }) { Icon(Icons.Default.Lyrics, "歌词") }
                IconButton(onClick = { queue = true }) { Icon(Icons.Default.QueueMusic, "播放队列") }
            }
            Text(state.song?.name ?: "未选择音乐", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (!state.song?.artist.isNullOrBlank()) Text(state.song!!.artist,
                modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (animate && !artworkReady) CircularProgressIndicator(Modifier.align(Alignment.Center))
                else if (animate) AndroidView(factory = { WitchPlayerView(it).apply { setArtworkOnly(true) } }, modifier = Modifier.fillMaxSize(), update = { view ->
                    view.update(state.song?.name.orEmpty(), state.playing, state.buffering, currentPosition, state.duration, object : WitchPlayerView.Controls {
                        override fun playPause() = model.playPause()
                        override fun previous() = model.previous()
                        override fun next() = model.next()
                        override fun seek(position: Long) = model.seek(position)
                        override fun queue() { queue = true }
                    })
                }) else AsyncImage("file:///android_asset/elaina-layer.png", null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
            if (state.buffering) LinearProgressIndicator(Modifier.fillMaxWidth())
            Slider(value = seeking ?: currentPosition.coerceAtMost(state.duration).toFloat(), onValueChange = { seeking = it },
                onValueChangeFinished = { seeking?.let { model.seek(it.toLong()) }; seeking = null },
                valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(), enabled = state.duration > 0)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(time((seeking ?: currentPosition.toFloat()).toLong()), style = MaterialTheme.typography.labelMedium)
                Text(time(state.duration), style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = model::shuffle) { Icon(Icons.Default.Shuffle, "随机播放", tint = if (state.shuffle) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline) }
                IconButton(onClick = model::previous, enabled = state.queue.size > 1) { Icon(Icons.Default.SkipPrevious, "上一首", Modifier.size(34.dp)) }
                FilledIconButton(onClick = model::playPause, modifier = Modifier.size(72.dp)) { Icon(if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (state.playing) "暂停" else "播放", Modifier.size(38.dp)) }
                IconButton(onClick = model::next, enabled = state.queue.size > 1) { Icon(Icons.Default.SkipNext, "下一首", Modifier.size(34.dp)) }
                IconButton(onClick = model::repeat) { Icon(if (state.repeat == 1) Icons.Default.RepeatOne else Icons.Default.Repeat, "循环模式",
                    tint = if (state.repeat != 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline) }
            }
            state.song?.let { song -> IconButton(onClick = { model.favorite(song) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(if (song.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏")
            } }
        }
    }
    if (queue) ModalBottomSheet(onDismissRequest = { queue = false }) {
        LazyColumn(contentPadding = PaddingValues(20.dp)) {
            items(state.queue.size) { index -> SongRow(Track(state.queue[index]), state.queue[index].id == state.song?.id,
                { model.queueIndex(index); queue = false }) }
        }
    }
    lyrics?.let { text -> ModalBottomSheet(onDismissRequest = { lyrics = null }) {
        LazyColumn(contentPadding = PaddingValues(24.dp)) { items(text.lines()) { Text(it, modifier = Modifier.padding(vertical = 8.dp)) } }
    } }
}

@Composable
private fun Settings(model: EPlayerViewModel, close: () -> Unit) {
    val context = LocalContext.current
    val animate by model.animate.collectAsState()
    var engine by remember { mutableIntStateOf(UserPreferences.getAudioEngine(context)) }
    AlertDialog(onDismissRequest = close, title = { Text("设置") }, text = {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("角色动画", Modifier.weight(1f)); Switch(animate, model::setAnimate) }
            listOf("系统音频", "Rust 音频").forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().clickable { engine = index; model.setEngine(index) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(engine == index, { engine = index; model.setEngine(index) }); Text(label)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("完成") } })
}

@Composable
private fun SourceLogin(source: String) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var open by rememberSaveable(source) { mutableStateOf(false) }
    var key by rememberSaveable(source) { mutableStateOf<String?>(null) }
    var url by rememberSaveable(source) { mutableStateOf<String?>(null) }
    var image by remember(source) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var status by rememberSaveable(source) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var logged by remember(source) { mutableStateOf(!UserPreferences.getPrefs(context).getString("cookie_$source", null).isNullOrBlank()) }
    fun close() { open = false; key = null; url = null; image = null }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(if (logged) "切换账号" else "登录") }
    LaunchedEffect(open, source, refresh) {
        if (!open) return@LaunchedEffect
        status = "加载中"
        try {
            if (key == null || url == null) {
                key = QrLogin.key(SourceStore.call(context, source, "login/qr/key"))
                url = QrLogin.url(SourceStore.call(context, source, "login/qr/create", mapOf("key" to key!!)))
            }
            image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { QrLogin.bitmap(url!!) }
            status = "等待扫码"
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (status == "已登录" || status == "二维码已过期") return@repeatOnLifecycle
                while (open) {
                    delay(2000)
                    val response = SourceStore.call(context, source, "login/qr/check", mapOf("key" to key!!))
                    when (response.get("code")?.asInt) {
                        800 -> { status = "二维码已过期"; image = null; break }
                        801 -> status = "等待扫码"
                        802 -> status = "等待确认"
                        803 -> {
                            val cookie = response.get("cookie")?.takeIf { it.isJsonPrimitive }?.asString
                            check(!cookie.isNullOrBlank()) { "登录未返回会话，请刷新重试" }
                            UserPreferences.getPrefs(context).edit().putString("cookie_$source", cookie).apply()
                            SourceStore.clearResolved(source)
                            logged = true; status = "已登录"; image = null; break
                        }
                        else -> error(response.get("msg")?.takeIf { it.isJsonPrimitive }?.asString ?: "登录检查失败，请刷新重试")
                    }
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { status = error.message ?: "登录失败，请重试" }
    }
    if (open) AlertDialog(onDismissRequest = ::close, title = { Text("扫码登录") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            image?.let { androidx.compose.foundation.Image(it.asImageBitmap(), "登录二维码",
                Modifier.fillMaxWidth().aspectRatio(1f), filterQuality = androidx.compose.ui.graphics.FilterQuality.None) }
            Text(status)
        }
    }, confirmButton = { TextButton(onClick = ::close) { Text(if (status == "已登录") "完成" else "关闭") } },
        dismissButton = { if (status != "已登录") TextButton(onClick = { key = null; url = null; image = null; refresh++ }) { Text("刷新") } })
}

private fun time(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}
