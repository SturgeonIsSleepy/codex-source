package cp.player.app

import cp.player.core.library.Track

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cp.player.provider.ProviderManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudHome(val source: String? = null, val account: Account? = null, val playlists: List<MusicEntry> = emptyList(),
    val recommendations: List<MusicEntry> = emptyList(), val loading: Boolean = false, val error: String? = null)
data class DetailPage(val target: MusicEntry, val data: MusicDetail? = null, val loading: Boolean = true, val error: String? = null)

class CloudViewModel(private val app: Application) : AndroidViewModel(app) {
    val home = MutableStateFlow(CloudHome())
    val likes = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val searchResult = MutableStateFlow(MusicSearch())
    val searching = MutableStateFlow(false)
    val searched = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)
    val searchType = MutableStateFlow(1)
    val pages = MutableStateFlow<List<DetailPage>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var searchGeneration = 0
    private var libraryEnabled = false
    private var discoveryVisible = false

    init {
        viewModelScope.launch { ProviderManager.currentProviderFlow.collect { provider ->
            home.value = CloudHome(source = provider?.id)
            pages.value = emptyList(); clearSearch()
            if (libraryEnabled) refresh()
        } }
        viewModelScope.launch { SourceStore.accountChanges.collect { source -> if (source == home.value.source) {
            home.value = CloudHome(source = source); pages.value = emptyList(); clearSearch()
            likes.update { it - source }; if (libraryEnabled) refresh()
        } } }
    }
    private suspend fun <T> optional(action: suspend () -> T): T? = try { action() } catch (error: CancellationException) { throw error } catch (_: Exception) { null }
    fun ensureLibrary() {
        if (libraryEnabled) return
        libraryEnabled = true
        refresh()
    }
    fun setDiscoveryVisible(visible: Boolean) {
        discoveryVisible = visible
        if (visible) { libraryEnabled = true; refresh() }
    }
    fun refresh() {
        if (!libraryEnabled) return
        refreshJob?.cancel()
        val source = home.value.source ?: return
        refreshJob = viewModelScope.launch {
            home.value = home.value.copy(loading = true, error = null)
            try {
                val repo = MusicRepository(app, source)
                val account = repo.account()
                coroutineScope {
                    val playlists = async { if (account == null) emptyList() else repo.playlists(account.id) }
                    val recommendations = async { if (discoveryVisible) optional { repo.recommendations(account != null) }.orEmpty() else home.value.recommendations }
                    val liked = async { if (account == null) emptySet() else optional { repo.liked(account.id) }.orEmpty() }
                    val result = CloudHome(source, account, playlists.await(), recommendations.await())
                    val likedIds = liked.await()
                    likes.update { it + (source to likedIds) }
                    home.value = result
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { home.value = home.value.copy(loading = false, error = error.message ?: "加载失败") }
        }
    }
    fun search(query: String, type: Int = searchType.value) {
        searchJob?.cancel()
        val source = home.value.source ?: run { message.value = "请先添加音源"; return }
        if (query.isBlank()) return
        val generation = ++searchGeneration
        searchType.value = type; searching.value = true; searched.value = true; searchError.value = null
        searchResult.value = MusicSearch()
        searchJob = viewModelScope.launch {
            try { searchResult.value = MusicRepository(app, source).search(query.trim(), type) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { searchError.value = error.message ?: "搜索失败" }
            finally { if (generation == searchGeneration) searching.value = false }
        }
    }
    fun clearSearch() { searchGeneration++; searchJob?.cancel(); searching.value = false; searched.value = false; searchError.value = null; searchResult.value = MusicSearch() }
    fun open(target: MusicEntry) {
        pages.update { it + DetailPage(target) }
        load(target)
    }
    fun back() { pages.update { it.dropLast(1) } }
    fun retry() { pages.value.lastOrNull()?.let { load(it.target) } }
    private fun updatePage(target: MusicEntry, transform: (DetailPage) -> DetailPage) { pages.update { list -> list.map { if (it.target == target) transform(it) else it } } }
    private fun load(target: MusicEntry) = viewModelScope.launch {
        updatePage(target) { it.copy(loading = true, error = null) }
        try {
            val result = MusicRepository(app, target.source).detail(target)
            updatePage(target) { it.copy(data = result, loading = false) }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { updatePage(target) { it.copy(loading = false, error = error.message ?: "加载失败") } }
    }
    fun more() {
        val page = pages.value.lastOrNull() ?: return
        val detail = page.data ?: return
        if (page.loading) return
        viewModelScope.launch {
            updatePage(page.target) { it.copy(loading = true) }
            try {
                val next = MusicRepository(app, page.target.source).detail(page.target, detail.tracks.size)
                updatePage(page.target) { it.copy(loading = false, data = detail.copy(tracks = (detail.tracks + next.tracks).distinctBy { it.song.id }, total = if (next.tracks.isEmpty()) detail.tracks.size else detail.total)) }
            } catch (error: Exception) { updatePage(page.target) { it.copy(loading = false, error = error.message) } }
        }
    }
    fun like(track: Track) = action {
        val source = track.source ?: return@action
        val id = track.remoteId ?: track.song.id
        val value = id !in likes.value[source].orEmpty()
        MusicRepository(app, source).like(id, value)
        likes.update { values -> values + (source to values[source].orEmpty().toMutableSet().apply { if (value) add(id) else remove(id) }) }
    }
    fun create(name: String, private: Boolean = false) = action {
        require(name.isNotBlank()) { "请输入歌单名称" }
        val source = home.value.source ?: error("请先选择音源")
        val created = MusicRepository(app, source).createPlaylist(name.trim(), private)
        refresh(); open(created)
    }
    fun rename(target: MusicEntry, name: String) = action {
        require(name.isNotBlank()) { "请输入歌单名称" }
        MusicRepository(app, target.source).renamePlaylist(target.id, name.trim())
        refresh(); load(target)
    }
    fun delete(target: MusicEntry) = action {
        MusicRepository(app, target.source).deletePlaylist(target.id)
        back(); refresh()
    }
    fun subscribe(target: MusicEntry) = action {
        MusicRepository(app, target.source).subscribe(target, !target.subscribed)
        refresh(); load(target)
    }
    fun addToPlaylist(target: MusicEntry, track: Track) = action {
        require(target.source == track.source) { "歌曲与歌单必须属于同一个音源" }
        if (target.special) MusicRepository(app, target.source).like(track.remoteId ?: track.song.id, true)
        else MusicRepository(app, target.source).changeTracks(target.id, listOf(track.remoteId ?: track.song.id), true)
        message.value = "已添加到歌单"
    }
    fun removeFromPlaylist(target: MusicEntry, track: Track) = action {
        if (target.special) {
            MusicRepository(app, target.source).like(track.remoteId ?: track.song.id, false)
            likes.update { values -> values + (target.source to (values[target.source].orEmpty() - (track.remoteId ?: track.song.id))) }
        } else MusicRepository(app, target.source).changeTracks(target.id, listOf(track.remoteId ?: track.song.id), false)
        load(target)
    }
    fun radio(play: (List<Track>) -> Unit) = action {
        val source = home.value.source ?: error("请先选择音源")
        val tracks = MusicRepository(app, source).radio()
        check(tracks.isNotEmpty()) { "暂无推荐" }; play(tracks)
    }
    fun logout() = action { home.value.source?.let { SourceStore.logout(app, it) } }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        try { block() } catch (error: CancellationException) { throw error }
        catch (error: Exception) { message.value = error.message ?: "操作失败" }
        finally { busy.value = false }
    }
}
