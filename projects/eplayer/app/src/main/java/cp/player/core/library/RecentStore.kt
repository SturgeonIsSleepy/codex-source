package cp.player.core.library

import android.content.Context
import androidx.media3.common.Player
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import cp.player.util.toSong

object RecentStore {
    private val gson = Gson()
    private val type = object : TypeToken<List<Track>>() {}.type
    fun read(context: Context): List<Track> = runCatching {
        gson.fromJson<List<Track>>(context.getSharedPreferences("eplayer_recent", 0).getString("items", "[]"), type)
    }.getOrNull().orEmpty()
    fun add(context: Context, player: Player) {
        val item = player.currentMediaItem ?: return
        val track = Track(item.toSong(), item.localConfiguration?.uri?.toString(), item.mediaMetadata.extras?.getString("source"), item.mediaMetadata.extras?.getString("remoteId"))
        val next = (listOf(track) + read(context)).distinctBy { it.source to it.song.id }.take(100)
        context.getSharedPreferences("eplayer_recent", 0).edit().putString("items", gson.toJson(next)).apply()
    }
    fun remove(context: Context, id: String) {
        context.getSharedPreferences("eplayer_recent", 0).edit().putString("items", gson.toJson(read(context).filterNot { it.song.id == id })).apply()
    }
}
