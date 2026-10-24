package cp.player.app

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import cp.player.manager.DownloadRegistry
import cp.player.manager.WitchPlaylist
import cp.player.model.Song
import cp.player.provider.BackendProvider
import cp.player.provider.ModuleManager
import cp.player.provider.ProviderManager
import cp.player.service.MusicService
import cp.player.util.toSong
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class Playback(val song: Song? = null, val playing: Boolean = false, val buffering: Boolean = false,
    val position: Long = 0, val duration: Long = 0, val queue: List<Song> = emptyList(),
    val shuffle: Boolean = false, val repeat: Int = Player.REPEAT_MODE_OFF, val error: String? = null, val source: String? = null)

class EPlayerViewModel(private val app: Application) : AndroidViewModel(app) {
    private val catalog by lazy { LibraryStore(app) }
    private val _library = MutableStateFlow<List<Track>>(emptyList())
    val library = _library.asStateFlow()
    private var scanned = emptyList<Track>()
    private val _playback = MutableStateFlow(Playback())
    val playback = _playback.asStateFlow()
    val position = MutableStateFlow(0L)
    val searched = MutableStateFlow(false)
    val searchFailure = MutableStateFlow<String?>(null)
    val downloading = MutableStateFlow<Set<String>>(emptySet())
    val providers = ModuleManager.providersFlow
    val activeSource = ProviderManager.currentProviderFlow
    val sourceIssue = SourceStore.issue
    val results = MutableStateFlow<List<Track>>(emptyList())
    val searching = MutableStateFlow(false)
    val importing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val favorites = MutableStateFlow(app.getSharedPreferences("eplayer_ui", 0).getStringSet("favorites", emptySet()).orEmpty().toSet())
    val animate = MutableStateFlow(app.getSharedPreferences("eplayer_ui", 0).getBoolean("animate", true))
    private var controller: MediaController? = null
    private var pendingPlay: (() -> Unit)? = null
    private var visible = false
    private var progressVisible = false
    private var ticker: Job? = null
    private var search: Job? = null
    private var searchGeneration = 0
    private val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, MusicService::class.java))).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { publish() }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            message.value = "播放失败：${error.message ?: error.errorCodeName}"
        }
    }
    init {
        viewModelScope.launch { reloadLibrary() }
        future.addListener({
            runCatching { future.get() }.onSuccess {
                controller = it; it.addListener(listener); publish()
                pendingPlay?.invoke(); pendingPlay = null
            }.onFailure { message.value = "播放服务连接失败：${it.cause?.message ?: it.message}" }
        }, MoreExecutors.directExecutor())
        viewModelScope.launch {
            activeSource.collect { clearSearch() }
        }
    }
    fun setVisible(value: Boolean) { visible = value; restartTicker() }
    fun setProgressVisible(value: Boolean) { progressVisible = value; if (value) publish() else restartTicker() }
    private fun publish() {
        val player = controller ?: return
        _playback.value = Playback(player.currentMediaItem?.toSong(), player.isPlaying,
            player.playbackState == Player.STATE_BUFFERING, player.currentPosition.coerceAtLeast(0),
            player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() },
            player.shuffleModeEnabled, player.repeatMode, player.playerError?.message, player.currentMediaItem?.mediaMetadata?.extras?.getString("source"))
        position.value = player.currentPosition.coerceAtLeast(0)
        restartTicker()
    }
    private fun restartTicker() {
        if (!visible || !progressVisible || controller?.isPlaying != true) { ticker?.cancel(); ticker = null; return }
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(500)
                val player = controller ?: break
                position.value = player.currentPosition.coerceAtLeast(0)
            }
        }
    }
    private suspend fun reloadLibrary() {
        _library.value = withContext(Dispatchers.IO) {
            val oldDownloads = DownloadRegistry.downloadedSongsFlow.value.mapNotNull { item -> item.filePath?.let { Track(item.song, if (it.startsWith("content:")) it else Uri.fromFile(File(it)).toString()) } }
            (catalog.imported() + oldDownloads + scanned).distinctBy { it.song.id }
        }
    }
    fun importAudio(uris: List<Uri>) {
        if (importing.value || uris.isEmpty()) return
        importing.value = true
        viewModelScope.launch {
            var imported = 0
            try {
                for (uri in uris) {
                    withContext(Dispatchers.IO) { catalog.importAudio(uri) }
                    imported++
                }
            } catch (error: Exception) { message.value = "已导入 $imported 首，${error.message}" }
            finally { reloadLibrary(); importing.value = false }
        }
    }
    fun removeImported(track: Track) = viewModelScope.launch {
        try {
            controller?.let { player ->
                val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == track.song.id }
                if (index != null) player.removeMediaItem(index)
            }
            withContext(Dispatchers.IO) { catalog.removeImported(track) }
            reloadLibrary()
        } catch (error: Exception) { message.value = error.message ?: "删除失败" }
    }
    fun scanLibrary() = viewModelScope.launch {
        try { scanned = withContext(Dispatchers.IO) { catalog.scan() }; reloadLibrary() }
        catch (error: Exception) { message.value = error.message ?: "读取失败" }
    }
    fun witchTracks(): List<Track> = WitchPlaylist.songs.mapNotNull { song -> WitchPlaylist.uri(app, song.id)?.let { Track(song, it.toString()) } }
    fun importWitch(index: Int, uri: Uri) = viewModelScope.launch {
        importing.value = true
        try { withContext(Dispatchers.IO) { WitchPlaylist.import(app, index, uri) }; reloadLibrary() }
        catch (error: Exception) { message.value = error.message ?: "导入失败" }
        finally { importing.value = false }
    }
    fun play(track: Track, queue: List<Track>) {
        val action = {
            controller?.let { player ->
                val tracks = (if (queue.isEmpty()) listOf(track) else queue).distinctBy { it.song.id to it.source }
                val index = tracks.indexOf(track).coerceAtLeast(0)
                player.setMediaItems(tracks.map(Track::mediaItem), index, 0)
                player.prepare(); player.play()
            }
            Unit
        }
        if (controller == null) pendingPlay = action else action()
    }
    fun playPause() { controller?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); if (it.playbackState == Player.STATE_ENDED) it.seekToDefaultPosition(); it.play() } } }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun next() { controller?.seekToNextMediaItem() }
    fun seek(position: Long) { controller?.seekTo(position); publish() }
    fun queueIndex(index: Int) { controller?.let { it.seekToDefaultPosition(index); it.prepare(); it.play() } }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun repeat() { controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 } }
    fun favorite(song: Song) {
        val next = favorites.value.toMutableSet().apply { if (!add(song.id)) remove(song.id) }
        favorites.value = next
        app.getSharedPreferences("eplayer_ui", 0).edit().putStringSet("favorites", next).apply()
    }
    fun setAnimate(value: Boolean) { animate.value = value; app.getSharedPreferences("eplayer_ui", 0).edit().putBoolean("animate", value).apply() }
    fun setEngine(engine: Int) { controller?.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", engine) }) }
    fun search(query: String) {
        search?.cancel(); results.value = emptyList()
        val source = activeSource.value ?: run { message.value = "请先添加音源"; return }
        if (query.isBlank()) return
        val generation = ++searchGeneration
        searching.value = true; searched.value = true; searchFailure.value = null
        search = viewModelScope.launch {
            try { results.value = SourceStore.search(app, source.id, query.trim()) }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { searchFailure.value = error.message ?: "搜索失败" }
            finally { if (generation == searchGeneration) searching.value = false }
        }
    }
    fun clearSearch() { searchGeneration++; search?.cancel(); results.value = emptyList(); searching.value = false; searched.value = false; searchFailure.value = null }
    fun importSource(uri: Uri) = sourceAction { SourceStore.import(app, uri) }
    fun selectSource(source: BackendProvider) = sourceAction { SourceStore.select(app, source) }
    fun removeSource(source: BackendProvider) = sourceAction { SourceStore.remove(app, source) }
    private fun sourceAction(action: suspend () -> Unit) = viewModelScope.launch {
        try { action() } catch (error: Exception) { message.value = error.message ?: "音源操作失败" }
    }
    suspend fun lyrics(): String {
        val source = playback.value.source ?: return "暂无歌词"
        val song = playback.value.song ?: return "暂无歌词"
        return runCatching { SourceStore.call(app, source, "lyric/new", mapOf("id" to song.id))
            .getAsJsonObject("lrc")?.get("lyric")?.asString }.getOrNull() ?: "暂无歌词"
    }
    fun download(track: Track) = viewModelScope.launch {
        if (track.source == null || "${track.source}:${track.song.id}" in downloading.value) return@launch
        val downloadKey = "${track.source}:${track.song.id}"
        downloading.value = downloading.value + downloadKey
        try {
            val url = SourceStore.resolve(app, track.source, track.song.id)
            withContext(Dispatchers.IO) {
                val temporary = File.createTempFile("download-", ".audio", app.cacheDir)
                try {
                    OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { response ->
                        check(response.isSuccessful) { "下载失败：${response.code}" }
                        val body = response.body ?: error("下载内容为空")
                        body.byteStream().use { input -> temporary.outputStream().use { output ->
                            val buffer = ByteArray(65536)
                            while (true) { ensureActive(); val count = input.read(buffer); if (count < 0) break; output.write(buffer, 0, count) }
                        } }
                    }
                    catalog.importAudio(Uri.fromFile(temporary), track.song)
                } finally { temporary.delete() }
            }
            reloadLibrary(); message.value = "已保存到曲库"
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { message.value = error.message ?: "下载失败" }
        finally { downloading.value = downloading.value - downloadKey }
    }
    override fun onCleared() {
        ticker?.cancel(); search?.cancel()
        controller?.removeListener(listener)
        MediaController.releaseFuture(future)
        super.onCleared()
    }
}
