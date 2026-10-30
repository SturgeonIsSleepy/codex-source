package cp.player.core.library

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player

/** Read-only recent browser projection. It never prepares a player, resolves a source or owns a queue. */
internal object RecentMediaCatalog {
    const val ROOT = "eplayer_recent"
    fun root(): MediaItem = MediaItem.Builder().setMediaId(ROOT).setMediaMetadata(
        MediaMetadata.Builder().setTitle("最近播放").setIsBrowsable(true).setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()

    fun items(context: Context, player: Player): List<MediaItem> {
        val current = player.currentMediaItem?.takeIf { it.localConfiguration != null }
        val recent = current ?: RecentStore.read(context).firstOrNull { it.uri != null }?.mediaItem()
        return listOfNotNull(recent?.let { item ->
            val title = item.mediaMetadata.title?.toString()?.takeIf(String::isNotBlank)
                ?: "未命名曲目"
            item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setTitle(title)
                .setIsBrowsable(false).setIsPlayable(true).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build()).build()
        })
    }
}
