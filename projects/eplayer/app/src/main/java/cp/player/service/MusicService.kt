package cp.player.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import cp.player.MainActivity
import cp.player.app.QueueStore
import cp.player.core.playback.togglePlayback
import cp.player.engine.CPPlayerManager
import cp.player.util.UserPreferences
import cp.player.widget.MusicWidgetProvider
import java.io.File

/** One audio player, event-driven notifications and no background UI/lyric polling. */
class MusicService : MediaSessionService() {
    private lateinit var player: Player
    private var session: MediaSession? = null
    private lateinit var audio: AudioManager
    private var focus: AudioFocusRequest? = null
    private var resumeAfterFocus = false
    private var engine = 0
    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { player.pause() }
    }
    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
            if (ready && engine == 1) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                    .setOnAudioFocusChangeListener { change ->
                        when (change) {
                            AudioManager.AUDIOFOCUS_LOSS -> { resumeAfterFocus = false; player.pause() }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                resumeAfterFocus = player.isPlaying; player.pause()
                            }
                            AudioManager.AUDIOFOCUS_GAIN -> if (resumeAfterFocus) {
                                resumeAfterFocus = false; player.play()
                            }
                        }
                    }.build()
                focus = request
                if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) player.pause()
            }
        }
        override fun onEvents(player: Player, events: Player.Events) {
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
        audio = getSystemService(AudioManager::class.java)
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        engine = UserPreferences.getAudioEngine(this)
        player = CPPlayerManager.createPlayer(this, engine)
        QueueStore.restore(this, player)
        player.addListener(listener)
        val activity = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).setAction("ACTION_SHOW_PLAYER"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player).setSessionActivity(activity)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                            .add(SessionCommand("SET_ENGINE", Bundle.EMPTY)).build()).build()
                }
                override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo,
                    command: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
                    if (command.customAction != "SET_ENGINE") return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
                    val requested = args.getInt("engine").coerceIn(0, 1)
                    if (requested != engine) {
                        val items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) }
                        val index = player.currentMediaItemIndex.coerceAtLeast(0)
                        val position = player.currentPosition
                        val playing = player.playWhenReady
                        val previous = player
                        previous.removeListener(listener)
                        previous.pause()
                        engine = requested
                        UserPreferences.saveAudioEngine(this@MusicService, engine)
                        player = CPPlayerManager.createPlayer(this@MusicService, engine)
                        player.addListener(listener)
                        session.setPlayer(player)
                        previous.release()
                        if (items.isNotEmpty()) { player.setMediaItems(items, index, position); player.prepare(); player.playWhenReady = playing }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_TOGGLE_PLAY" -> player.togglePlayback()
            "ACTION_NEXT" -> player.seekToNextMediaItem()
            "ACTION_PREVIOUS" -> player.seekToPreviousMediaItem()
        }
        return super.onStartCommand(intent, flags, startId)
    }
    override fun onTaskRemoved(rootIntent: Intent?) { if (!player.playWhenReady) stopSelf() }
    override fun onDestroy() {
        QueueStore.save(this, player)
        unregisterReceiver(noisy)
        player.removeListener(listener)
        session?.release()
        player.release()
        focus?.let(audio::abandonAudioFocusRequest)
        super.onDestroy()
    }
    private fun updateWidgets() {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, MusicWidgetProvider::class.java))
        val metadata = player.mediaMetadata
        ids.forEach { MusicWidgetProvider.updateAppWidget(this, manager, it, metadata.title?.toString(),
            metadata.artist?.toString(), metadata.artworkUri?.toString(), player.isPlaying) }
    }
    companion object {
        private var cache: SimpleCache? = null
        @Synchronized fun getCache(context: Context): SimpleCache = cache ?: SimpleCache(
            File(context.cacheDir, "media"), LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
            androidx.media3.database.StandaloneDatabaseProvider(context)).also { cache = it }
        @Synchronized fun clearAudioCache(context: Context) {
            cache?.release(); cache = null
            File(context.cacheDir, "media").deleteRecursively()
        }
    }
}
