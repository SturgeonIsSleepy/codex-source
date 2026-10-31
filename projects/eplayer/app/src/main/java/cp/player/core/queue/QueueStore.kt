package cp.player.core.queue

import android.content.Context
import androidx.media3.common.Player
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import cp.player.core.library.Track
import cp.player.manager.WitchPlaylist
import cp.player.util.toSong

/** Persists at queue/transport changes; never writes progress every frame. */
object QueueStore {
    private val gson = Gson()
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { work -> Thread(work, "ePlayer-state").apply { isDaemon = true } }
    fun save(context: Context, player: Player, queueChanged: Boolean = true) {
        val prefs = context.getSharedPreferences("eplayer_queue", Context.MODE_PRIVATE)
        prefs.edit().putInt("index", player.currentMediaItemIndex).putLong("position", player.currentPosition).apply()
        if (!queueChanged) return
        val items = (0 until player.mediaItemCount).map { i ->
            val item = player.getMediaItemAt(i)
            Track(item.toSong(), item.localConfiguration?.uri?.toString(), item.mediaMetadata.extras?.getString("source"), item.mediaMetadata.extras?.getString("remoteId"))
        }
        writer.execute { prefs.edit().putString("items", gson.toJson(items)).apply() }
    }
    fun restore(context: Context, player: Player) {
        val prefs = context.getSharedPreferences("eplayer_queue", Context.MODE_PRIVATE)
        val json = prefs.getString("items", null) ?: return
        val items = runCatching { gson.fromJson<List<Track>>(json, object : TypeToken<List<Track>>() {}.type) }.getOrNull().orEmpty()
        val valid = items.withIndex().filter { (_, track) ->
            track.uri != null && (!track.song.id.startsWith("witch_") || WitchPlaylist.uri(context, track.song.id) != null)
        }
        if (valid.isEmpty()) return
        val savedIndex = prefs.getInt("index", 0)
        val restoredIndex = valid.indexOfFirst { it.index >= savedIndex }.takeIf { it >= 0 } ?: valid.lastIndex
        // Filtering earlier entries changes the index, not the identity of the saved song.
        // If that song disappeared, resume the next (or last surviving) song from its beginning.
        val position = if (valid[restoredIndex].index == savedIndex) prefs.getLong("position", 0).coerceAtLeast(0) else 0L
        player.setMediaItems(valid.map { it.value.mediaItem() }, restoredIndex, position)
        // Opening the app must not silently start playback or networking.
    }
}
