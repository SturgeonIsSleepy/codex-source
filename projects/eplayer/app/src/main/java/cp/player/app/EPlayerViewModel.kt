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
import cp.player.core.playback.togglePlayback
import cp.player.core.playback.PlaybackSleepTimer
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Playback(val song: Song? = null, val playing: Boolean = false, val buffering: Boolean = false,
    val position: Long = 0, val duration: Long = 0, val queue: List<Song> = emptyList(),
    val shuffle: Boolean = false, val repeat: Int = Player.REPEAT_MODE_OFF, val error: String? = null, val source: String? = null, val remoteId: String? = null, val playRequested: Boolean = playing)

class EPlayerViewModel(private val app: Application) : AndroidViewModel(app) {
    internal val soundSettings = SoundSettingsStore(app, viewModelScope)
    private val catalog by lazy { LibraryStore(app) }
    private val _library = MutableStateFlow<List<Track>>(emptyList())
    val library = _library.asStateFlow()
    val recent = MutableStateFlow<List<Track>>(emptyList())
    private var scanned = emptyList<Track>()
    private val _playback = MutableStateFlow(Playback())
    val playback = _playback.asStateFlow()
    val position = MutableStateFlow(0L)
    val downloads = MutableStateFlow<List<DownloadProgress>>(emptyList())
    private val resolvingDownloads = mutableSetOf<String>()
    private val downloadStore by lazy { Downloads(app, catalog) }
    private var downloadObserver: Job? = null
    val providers = ModuleManager.providersFlow
    val activeSource = ProviderManager.currentProviderFlow
    val sourceIssue = SourceStore.issue
    val importing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val favorites = MutableStateFlow(app.getSharedPreferences("eplayer_ui", 0).getStringSet("favorites", emptySet()).orEmpty().toSet())
    val quality = MutableStateFlow(app.getSharedPreferences("eplayer_ui", 0).getString("quality", "standard") ?: "standard")
    val animate = MutableStateFlow(app.getSharedPreferences("eplayer_ui", 0).getBoolean("animate", true))
    val sleepDeadline = MutableStateFlow(0L)
    private var controller: MediaController? = null
    private var pendingPlay: (() -> Unit)? = null
    private var visible = false
    private var progressVisible = false
    private var ticker: Job? = null
    private var radioSource: String? = null
    private var loadingRadio = false
    private val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, MusicService::class.java))).setListener(object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            sleepDeadline.value = extras.getLong(PlaybackSleepTimer.DEADLINE)
        }
    }).buildAsync()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                viewModelScope.launch { recent.value = withContext(Dispatchers.IO) { RecentStore.read(app) } }
                refillRadio()
            }
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            playback.value.source?.let(SourceStore::clearResolved)
            message.value = "播放失败：${error.message ?: error.errorCodeName}"
        }
    }
    init {
        viewModelScope.launch { reloadLibrary(); recent.value = withContext(Dispatchers.IO) { RecentStore.read(app) } }
        future.addListener({
            runCatching { future.get() }.onSuccess {
                controller = it; sleepDeadline.value = it.sessionExtras.getLong(PlaybackSleepTimer.DEADLINE); it.addListener(listener); publish()
                pendingPlay?.invoke(); pendingPlay = null
            }.onFailure { message.value = "播放服务连接失败：${it.cause?.message ?: it.message}" }
        }, MoreExecutors.directExecutor())
    }
    fun setVisible(value: Boolean) { visible = value; restartTicker(); if (value) observeDownloads() else { downloadObserver?.cancel(); downloadObserver = null } }
    fun setProgressVisible(value: Boolean) { progressVisible = value; if (value) publish() else restartTicker() }
    private fun publish() {
        val player = controller ?: return
        _playback.value = Playback(player.currentMediaItem?.toSong(), player.isPlaying,
            player.playbackState == Player.STATE_BUFFERING, player.currentPosition.coerceAtLeast(0),
            player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() },
            player.shuffleModeEnabled, player.repeatMode, player.playerError?.message, player.currentMediaItem?.mediaMetadata?.extras?.getString("source"), player.currentMediaItem?.mediaMetadata?.extras?.getString("remoteId"),
            playRequested = player.playWhenReady && player.playbackState != Player.STATE_ENDED && player.playerError == null)
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
            withContext(Dispatchers.IO) { catalog.removeImported(track); RecentStore.remove(app, track.song.id) }
            recent.value = withContext(Dispatchers.IO) { RecentStore.read(app) }
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
    fun play(track: Track, queue: List<Track>, radio: Boolean = false) {
        radioSource = if (radio) track.source else null
        val action = {
            controller?.let { player ->
                val tracks = (if (queue.isEmpty()) listOf(track) else queue).distinctBy { it.song.id to it.source }
                val index = tracks.indexOf(track).coerceAtLeast(0)
                player.setMediaItems(tracks.map { it.mediaItem(quality.value) }, index, 0)
                player.prepare(); player.play()
            }
            Unit
        }
        if (controller == null) pendingPlay = action else action()
    }
    private fun refillRadio() {
        val source = radioSource ?: return
        val player = controller ?: return
        if (loadingRadio || player.currentMediaItemIndex < player.mediaItemCount - 2) return
        loadingRadio = true
        viewModelScope.launch {
            try {
                val next = MusicRepository(app, source).radio()
                if (radioSource == source) {
                    val current = controller ?: return@launch
                    current.addMediaItems(next.map { it.mediaItem(quality.value) })
                    if (current.currentMediaItemIndex > 20) current.removeMediaItems(0, current.currentMediaItemIndex - 5)
                }
            } catch (failure: Exception) { message.value = failure.message ?: "FM 加载失败" }
            finally { loadingRadio = false }
        }
    }
    fun playPause() { controller?.togglePlayback() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun next() { controller?.seekToNextMediaItem() }
    fun seek(position: Long) { controller?.seekTo(position); publish() }
    fun setQuality(value: String) { quality.value = value; app.getSharedPreferences("eplayer_ui", 0).edit().putString("quality", value).apply() }
    fun removeQueue(index: Int) { controller?.let { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) } }
    fun moveQueue(from: Int, to: Int) { controller?.let { if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to) } }
    fun addNext(track: Track) { controller?.let { it.addMediaItem((it.currentMediaItemIndex + 1).coerceIn(0, it.mediaItemCount), track.mediaItem(quality.value)) } }
    fun addQueue(track: Track) { controller?.addMediaItem(track.mediaItem(quality.value)) }
    fun currentTrack(): Track? = playback.value.song?.let { Track(it, source = playback.value.source, remoteId = playback.value.remoteId) }
    fun queueIndex(index: Int) { controller?.let { it.seekToDefaultPosition(index); it.prepare(); it.play() } }
    fun shuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun repeat() { controller?.let { it.repeatMode = (it.repeatMode + 1) % 3 } }
    fun favorite(song: Song) {
        val next = favorites.value.toMutableSet().apply { if (!add(song.id)) remove(song.id) }
        favorites.value = next
        app.getSharedPreferences("eplayer_ui", 0).edit().putStringSet("favorites", next).apply()
    }
    fun setAnimate(value: Boolean) { animate.value = value; app.getSharedPreferences("eplayer_ui", 0).edit().putBoolean("animate", value).apply() }
    fun setSleepTimer(minutes: Int) {
        val player = controller ?: run { message.value = "播放服务尚未连接"; return }
        val result = player.sendCustomCommand(SessionCommand(PlaybackSleepTimer.COMMAND, Bundle.EMPTY),
            Bundle().apply { putLong(PlaybackSleepTimer.DURATION_MS, minutes * 60000L) })
        result.addListener({
            runCatching { result.get() }.onSuccess {
                if (it.resultCode != androidx.media3.session.SessionResult.RESULT_SUCCESS) message.value = "无法设置定时停止"
            }.onFailure { message.value = "定时停止设置失败" }
        }, MoreExecutors.directExecutor())
    }
    fun setEngine(engine: Int) { controller?.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", engine) }) }
    fun importSource(uri: Uri) = sourceAction { SourceStore.import(app, uri) }
    fun selectSource(source: BackendProvider) = sourceAction { SourceStore.select(app, source) }
    fun removeSource(source: BackendProvider) = sourceAction { SourceStore.remove(app, source) }
    private fun sourceAction(action: suspend () -> Unit) = viewModelScope.launch {
        try { action() } catch (error: Exception) { message.value = error.message ?: "音源操作失败" }
    }
    suspend fun lyrics(): String {
        val source = playback.value.source ?: return "暂无歌词"
        val song = playback.value.song ?: return "暂无歌词"
        val id = playback.value.remoteId ?: song.id
        return runCatching { SourceStore.call(app, source, "lyric/new", mapOf("id" to id))
            .getAsJsonObject("lrc")?.get("lyric")?.asString }.getOrNull() ?: "暂无歌词"
    }
    fun download(track: Track) = viewModelScope.launch {
        val source = track.source ?: return@launch
        val id = track.remoteId ?: track.song.id
        val key = "$source:$id"
        if (!resolvingDownloads.add(key)) return@launch
        try {
            if (library.value.any { it.source == source && it.remoteId == id }) { message.value = "已在本地曲库中"; return@launch }
            val url = SourceStore.resolve(app, source, id, quality.value)
            withContext(Dispatchers.IO) { downloadStore.enqueue(track, url) }
            message.value = "已开始下载"
            observeDownloads()
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { message.value = error.message ?: "下载失败" }
        finally { resolvingDownloads.remove(key) }
    }
    fun cancelDownload(key: String) = viewModelScope.launch {
        withContext(Dispatchers.IO) { downloadStore.cancel(key) }
        val result = withContext(Dispatchers.IO) { downloadStore.reconcile() }
        downloads.value = result.first
    }
    fun retryDownload(record: DownloadRecord) = viewModelScope.launch {
        withContext(Dispatchers.IO) { downloadStore.cancel(record.key) }
        record.track.source?.let(SourceStore::clearResolved)
        download(record.track)
    }
    private fun observeDownloads() {
        if (!visible || downloadObserver?.isActive == true) return
        downloadObserver = viewModelScope.launch {
            try {
                while (isActive && visible) {
                    val result = withContext(Dispatchers.IO) { downloadStore.reconcile() }
                    downloads.value = result.first
                    if (result.second) reloadLibrary()
                    if (result.first.none { it.record.error == null && it.record.id > 0 }) break
                    delay(1000)
                }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { message.value = error.message ?: "读取下载任务失败" }
        }
    }
    override fun onCleared() {
        soundSettings.close()
        ticker?.cancel(); downloadObserver?.cancel()
        controller?.removeListener(listener)
        MediaController.releaseFuture(future)
        super.onCleared()
    }
}
