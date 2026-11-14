package cp.player.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import cp.player.MainActivity
import cp.player.app.QueueStore
import cp.player.core.playback.togglePlayback
import cp.player.core.playback.playbackOrder
import cp.player.core.playback.PlaybackSleepTimer
import cp.player.engine.CPPlayerManager
import cp.player.util.UserPreferences
import cp.player.widget.MusicWidgetProvider

/** One audio player, event-driven notifications and no background UI/lyric polling. */
class MusicService : MediaSessionService() {
    private lateinit var player: Player
    private var session: MediaSession? = null
    private lateinit var audio: AudioManager
    private var rustFocus: RustAudioFocus? = null
    private var engine = 0
    private val playNextOrder = cp.player.core.playback.PlayNextOrder()
    private var lastPriorityOrder: String? = null
    private fun updatePriorityShuffle() {
        playNextOrder.consume(player.currentMediaItem)
        val items = (0 until player.mediaItemCount).map(player::getMediaItemAt)
        val current = player.currentMediaItemIndex
        val pending = playNextOrder.pending(items, current)
        val exo = player as? androidx.media3.exoplayer.ExoPlayer ?: return
        if (!exo.shuffleModeEnabled || pending.isEmpty() || current !in items.indices) { lastPriorityOrder = null; return }
        val signature = "$current:${items.size}:${pending.map { cp.player.core.playback.PlayNextOrder.token(items[it]) }}"
        if (lastPriorityOrder == signature) return
        lastPriorityOrder = signature
        val fixed = (listOf(current) + pending).toSet()
        val order = listOf(current) + pending + player.playbackOrder().filter { it !in fixed }
        exo.setShuffleOrder(androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
    }
    private val handler = Handler(Looper.getMainLooper())
    private val listeningClock = cp.player.app.ListeningClock()
    private val listeningCheckpoint = Runnable { checkpointListening(saveQueue = true) }
    private fun checkpointListening(finishing: Boolean = false, saveQueue: Boolean = false) {
        val id = player.currentMediaItem?.let { item ->
            "${item.mediaMetadata.extras?.getString("source").orEmpty()}:${item.mediaId}"
        }
        listeningClock.update(SystemClock.elapsedRealtime(), id, !finishing && player.isPlaying)?.let { (track, elapsed) ->
            cp.player.app.ListeningStatsStore.record(this, track, elapsed)
        }
        if (saveQueue) QueueStore.save(this, player, queueChanged = false)
        handler.removeCallbacks(listeningCheckpoint)
        if (!finishing && player.isPlaying) handler.postDelayed(listeningCheckpoint, 30000)
    }
    private val sleepTimer = PlaybackSleepTimer(SystemClock::elapsedRealtime, { delay, action ->
        val task = Runnable { action() }
        handler.postDelayed(task, delay)
        val cancel: () -> Unit = { handler.removeCallbacks(task) }
        cancel
    }, { pausePlayer() }, { deadline ->
        session?.setSessionExtras(Bundle().apply { putLong(PlaybackSleepTimer.DEADLINE, deadline) })
    })
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { pausePlayer() }
    }
    private var recoveredItem: androidx.media3.common.MediaItem? = null
    private var pendingRecovery: Runnable? = null
    private fun cancelRecovery() { pendingRecovery?.let(handler::removeCallbacks); pendingRecovery = null }
    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            Log.i("EPlayerPlayback", "request=$playWhenReady reason=$reason state=${player.playbackState}")
            if (!playWhenReady) cancelRecovery()
        }
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            recoveredItem = null
            cancelRecovery()
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val item = player.currentMediaItem ?: return
            val source = item.mediaMetadata.extras?.getString("source")
                ?: item.localConfiguration?.uri?.getQueryParameter("source")
            source?.let(cp.player.app.SourceStore::clearResolved)
            val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
            val retry = causes.any { it is cp.player.core.playback.AudioRepresentationChanged ||
                it is cp.player.core.playback.AudioResolutionException && it.retryable } ||
                error.errorCode in setOf(2001, 2002, 2004)
            Log.i("EPlayerPlayback", "error=${error.errorCode} recovery=$retry requested=${player.playWhenReady}")
            if (!retry || !player.playWhenReady || recoveredItem == item) return
            recoveredItem = item
            cancelRecovery()
            pendingRecovery = Runnable {
                pendingRecovery = null
                if (player.currentMediaItem == item && player.playWhenReady && player.playerError != null) {
                    // prepare() recreates the failed period/extractor, retaining queue and time.
                    player.prepare()
                }
            }.also { handler.postDelayed(it, 500) }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) cp.player.app.RoamPlayback.changed()
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) updatePriorityShuffle()
            if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION)) checkpointListening()
            if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)) {
                QueueStore.save(this@MusicService, player, queueChanged = events.contains(Player.EVENT_TIMELINE_CHANGED))
            }
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) cp.player.app.RecentStore.add(this@MusicService, player)
            if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_METADATA_CHANGED)) updateWidgets()
        }
    }

    override fun onCreate() {
        super.onCreate()
        cp.player.app.ListeningStatsStore.refresh(this)
        audio = getSystemService(AudioManager::class.java)
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        engine = UserPreferences.getAudioEngine(this)
        player = CPPlayerManager.createPlayer(this, engine)
        rustFocus = if (engine == 1) RustAudioFocus(audio, player, handler) else null
        QueueStore.restore(this, player)
        player.addListener(listener)
        cp.player.app.RoamPlayback.attach(this) { player }
        val activity = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).setAction("ACTION_SHOW_PLAYER"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(activity)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(SessionCommand("SET_ENGINE", Bundle.EMPTY))
                            .add(SessionCommand(cp.player.app.RoamPlayback.START, Bundle.EMPTY))
                            .add(SessionCommand("MOVE_SHUFFLE_ITEM", Bundle.EMPTY))
                            .add(SessionCommand(PlaybackSleepTimer.COMMAND, Bundle.EMPTY)).build()).build()
                }
                override fun onPlayerInteractionFinished(session: MediaSession,
                    controller: MediaSession.ControllerInfo, playerCommands: Player.Commands) {
                    if (playerCommands.contains(Player.COMMAND_CHANGE_MEDIA_ITEMS)) updatePriorityShuffle()
                    if (playerCommands.contains(Player.COMMAND_STOP)) {
                        // Only whitelist our fixture labels; never dump arbitrary connection hints.
                        val label = controller.connectionHints.getString("eplayer.qa.owner")
                            .takeIf { it in setOf("integration", "equalizer", "stream", "native-contract") } ?: "external"
                        Log.i("EPlayerSession", "Stop batch owner=$label package=${controller.packageName} " +
                            "sameUid=${controller.uid == android.os.Process.myUid()} version=${controller.controllerVersion} verified=${controller.isPackageNameVerified}")
                    }
                }
                override fun onMediaButtonEvent(session: MediaSession, controller: MediaSession.ControllerInfo,
                    intent: Intent): Boolean {
                    val key = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    if (key?.keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
                        val dismissal = intent.getBooleanExtra("androidx.media3.session.NOTIFICATION_DISMISSED_EVENT_KEY", false)
                        Log.i("EPlayerSession", "Callback media STOP key; sameUid=${controller.uid == android.os.Process.myUid()} notificationDismissal=$dismissal")
                    }
                    return super.onMediaButtonEvent(session, controller, intent)
                }
                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                    command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    if (command.customAction == cp.player.app.RoamPlayback.START) {
                        val source = args.getString("source") ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                        val type = object : com.google.gson.reflect.TypeToken<List<cp.player.app.Track>>() {}.type
                        val anchors = runCatching { com.google.gson.Gson().fromJson<List<cp.player.app.Track>>(args.getString("anchors"), type) }.getOrNull()
                            ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                        cp.player.app.RoamPlayback.start(source, anchors, args.getStringArrayList("original").orEmpty().toSet())
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    if (command.customAction == PlaybackSleepTimer.COMMAND) {
                        if (!args.containsKey(PlaybackSleepTimer.DURATION_MS))
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                        val duration = args.getLong(PlaybackSleepTimer.DURATION_MS, -1)
                        if (duration !in 0..PlaybackSleepTimer.MAX_DURATION_MS)
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                        sleepTimer.set(duration)
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    if (command.customAction == "MOVE_SHUFFLE_ITEM") {
                        val from = args.getInt("from", -1); val to = args.getInt("to", -1)
                        val order = player.playbackOrder().toMutableList()
                        val first = order.indexOf(from); val last = order.indexOf(to)
                        if (!player.shuffleModeEnabled || first < 0 || last < 0)
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                        order.add(last, order.removeAt(first))
                        when (val enginePlayer = player) {
                            is androidx.media3.exoplayer.ExoPlayer -> enginePlayer.setShuffleOrder(
                                androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
                            is cp.player.engine.FlickPlayer -> enginePlayer.setShufflePlaybackOrder(order)
                        }
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    if (command.customAction != "SET_ENGINE") return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                    val requested = args.getInt("engine").coerceIn(0, 1)
                    if (requested != engine) {
                        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
                        val index = player.currentMediaItemIndex.coerceAtLeast(0)
                        val position = player.currentPosition
                        val playing = player.playWhenReady
                        val repeat = player.repeatMode
                        val shuffle = player.shuffleModeEnabled
                        val volume = player.volume
                        val previous = player
                        rustFocus?.close(); rustFocus = null
                        previous.removeListener(listener)
                        previous.pause()
                        engine = requested
                        UserPreferences.saveAudioEngine(this@MusicService, engine)
                        player = CPPlayerManager.createPlayer(this@MusicService, engine)
                        lastPriorityOrder = null
                        (player as? cp.player.engine.FlickPlayer)?.rememberPlayedNextTokens(playNextOrder.consumed())
                        rustFocus = if (engine == 1) RustAudioFocus(audio, player, handler) else null
                        player.repeatMode = repeat
                        player.shuffleModeEnabled = shuffle
                        player.volume = volume
                        player.addListener(listener)
                        // Restore the replacement before publishing it to modern and framework controllers.
                        // Publishing its initial empty/IDLE state makes a live handover look like a stop.
                        if (items.isNotEmpty()) {
                            player.setMediaItems(items, index, position)
                            player.prepare()
                            player.playWhenReady = playing
                        }
                        session.setPlayer(player)
                        CPPlayerManager.releasePlayer(previous)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            val key = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            if (key?.keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
                val dismissal = intent.getBooleanExtra("androidx.media3.session.NOTIFICATION_DISMISSED_EVENT_KEY", false)
                Log.i("EPlayerSession", "Incoming media STOP key; keyAction=${key.action} notificationDismissal=$dismissal")
            }
        }
        when (intent?.action) {
            "ACTION_TOGGLE_PLAY" -> player.togglePlayback()
            "ACTION_NEXT" -> player.seekToNextMediaItem()
            "ACTION_PREVIOUS" -> player.seekToPreviousMediaItem()
        }
        return super.onStartCommand(intent, flags, startId)
    }
    override fun onTaskRemoved(rootIntent: Intent?) { if (!player.playWhenReady) stopSelf() }
    override fun onDestroy() {
        cancelRecovery()
        cp.player.app.RoamPlayback.detach()
        checkpointListening(finishing = true)
        sleepTimer.set(0)
        rustFocus?.close(); rustFocus = null
        QueueStore.save(this, player)
        unregisterReceiver(noisy)
        player.removeListener(listener)
        session?.release()
        CPPlayerManager.releasePlayer(player)
        super.onDestroy()
    }
    private fun pausePlayer() { rustFocus?.pause() ?: player.pause() }

    private fun updateWidgets() {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, MusicWidgetProvider::class.java))
        val metadata = player.mediaMetadata
        ids.forEach { MusicWidgetProvider.updateAppWidget(this, manager, it, metadata.title?.toString(),
            metadata.artist?.toString(), metadata.artworkUri?.toString(), player.isPlaying) }
    }
}
