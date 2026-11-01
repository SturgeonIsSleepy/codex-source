package cp.player.core.playback

import android.os.Bundle
import androidx.media3.common.MediaItem

/** A manual play-next request is independent of the player's shuffle traversal. */
class PlayNextOrder {
    private val played = mutableSetOf<String>()
    fun consume(item: MediaItem?) { token(item)?.let { played += it } }
    fun remember(tokens: Set<String>) { played += tokens }
    fun consumed(): Set<String> = played.toSet()
    fun pending(items: List<MediaItem>, current: Int): List<Int> {
        played.retainAll(items.mapNotNull(::token).toSet())
        return nextPriorityIndices(items.map(::token), current, played)
    }
    companion object {
        private const val KEY = "eplayer.play_next_token"
        fun token(item: MediaItem?): String? = item?.mediaMetadata?.extras?.getString(KEY)
        fun mark(item: MediaItem): MediaItem = item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon()
            .setExtras(Bundle(item.mediaMetadata.extras ?: Bundle()).apply { putString(KEY, java.util.UUID.randomUUID().toString()) }).build()).build()
    }
}
