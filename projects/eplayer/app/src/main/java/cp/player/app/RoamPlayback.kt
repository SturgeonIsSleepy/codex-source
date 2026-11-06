package cp.player.app

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.Player
import cp.player.util.toSong
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** The playback service owns requests, so finishing an Activity does not stop continuous Radio. */
internal object RoamPlayback {
    val active = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    private var context: Context? = null
    private var player: (() -> Player)? = null
    private var scope: CoroutineScope? = null
    private var source: String? = null
    private var planner: RoamPlanner? = null
    private var job: Job? = null
    private var generation = 0
    private var loading = false
    private var nextAttempt = 0L
    private var personal = false
    private var fallbackNotified = false
    private var wanted = emptySet<String>()
    private var waitingForQueue = false
    private var initialRefill = false

    fun attach(context: Context, player: () -> Player) {
        detach()
        this.context = context.applicationContext; this.player = player
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    fun detach() { stop(); scope?.cancel(); scope = null; context = null; player = null }
    fun stop() {
        generation++; job?.cancel(); job = null; loading = false; nextAttempt = 0
        active.value = false; source = null; planner = null; fallbackNotified = false
        wanted = emptySet(); waitingForQueue = false; initialRefill = false
    }
    private fun queue(player: Player) = (0 until player.mediaItemCount).map { index ->
        val item = player.getMediaItemAt(index)
        Track(item.toSong(), source = item.mediaMetadata.extras?.getString("source"), remoteId = item.mediaMetadata.extras?.getString("remoteId"))
    }
    fun start(source: String, anchors: List<Track>, original: Set<String> = emptySet(), personal: Boolean = false) {
        stop()
        val player = player?.invoke() ?: run { messages.tryEmit("播放服务尚未连接"); return }
        this.source = source; this.personal = personal
        planner = RoamPlanner(source, anchors, original)
        wanted = anchors.filter { it.source == source }.map { it.recommendationKey() }.toSet()
        waitingForQueue = true; initialRefill = true
        active.value = true
        changed()
    }
    fun retry() {
        if (!active.value) return
        generation++; job?.cancel(); loading = false; nextAttempt = 0; fallbackNotified = false
        initialRefill = true
        refill(force = true)
    }
    fun changed() {
        if (!active.value) return
        val player = player?.invoke() ?: return
        if (waitingForQueue) {
            val current = queue(player)
            if (player.currentMediaItem?.mediaMetadata?.extras?.getString("source") != source || !current.map { it.recommendationKey() }.toSet().containsAll(wanted)) return
            waitingForQueue = false; planner?.remember(current)
        }
        if (player.mediaItemCount == 0 || player.currentMediaItem?.mediaMetadata?.extras?.getString("source") != source) { stop(); return }
        if (!player.playWhenReady) {
            if (loading) { generation++; job?.cancel(); loading = false }
            return
        }
        refill(force = initialRefill)
    }
    private fun refill(force: Boolean = false) {
        val source = source ?: return
        val context = context ?: return
        val player = player?.invoke() ?: return
        val planner = planner ?: return
        if (loading || !player.playWhenReady || player.mediaItemCount == 0 ||
            !force && (player.currentMediaItemIndex < player.mediaItemCount - 5 || SystemClock.elapsedRealtime() < nextAttempt)) return
        loading = true; initialRefill = false
        val owner = generation
        val existingQueue = queue(player)
        val seeds = planner.seeds(existingQueue.getOrNull(player.currentMediaItemIndex))
        val prefs = context.getSharedPreferences("eplayer_ui", 0)
        val useYtm = !personal && prefs.getString("recommendation_engine", "netease") == "ytm"
        job = scope?.launch {
            fun excluded() = prefs.getStringSet("no_interest", emptySet()).orEmpty().toSet()
            try {
                val repo = MusicRepository(context, source)
                var next = emptyList<Track>()
                if (useYtm) {
                    try { next = planner.fresh(YtmRecommendations(context, source).recommend(seeds), excluded(), existingQueue) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { }
                }
                if (next.isEmpty() && !personal) {
                    if (useYtm && owner == generation && !fallbackNotified) {
                        fallbackNotified = true; messages.tryEmit("YTM 暂不可用或无可靠匹配，使用网易云推荐")
                    }
                    for (seed in seeds) {
                        try { next = planner.fresh(repo.similar(seed.remoteId ?: seed.song.id), excluded(), existingQueue) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { continue }
                        if (next.isNotEmpty()) break
                    }
                }
                if (next.isEmpty()) next = repo.radio()
                if (!active.value || this@RoamPlayback.source != source || owner != generation) return@launch
                val current = this@RoamPlayback.player?.invoke() ?: return@launch
                val fresh = planner.fresh(next, excluded(), queue(current))
                if (fresh.isEmpty()) { nextAttempt = SystemClock.elapsedRealtime() + 30_000; return@launch }
                val ended = current.playbackState == Player.STATE_ENDED
                planner.remember(fresh)
                current.addMediaItems(fresh.map { it.mediaItem(prefs.getString("quality", "standard") ?: "standard") })
                if (ended && current.playWhenReady && current.hasNextMediaItem()) { current.seekToNextMediaItem(); current.prepare(); current.play() }
                if (current.currentMediaItemIndex > 20) current.removeMediaItems(0, current.currentMediaItemIndex - 5)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (owner == generation) { messages.tryEmit(failure.message ?: "漫游加载失败"); nextAttempt = SystemClock.elapsedRealtime() + 30_000 } }
            finally { if (owner == generation) loading = false }
        }
    }
}
