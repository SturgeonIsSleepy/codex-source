package cp.player.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import cp.player.ui.artwork.WitchPlayerView

@Composable
fun WitchPlayerPanel(
    title: String, artist: String, playing: Boolean, buffering: Boolean,
    audioUri: String? = null,
    position: Long, duration: Long, favorite: Boolean, shuffle: Boolean,
    repeat: Int, onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onSeek: (Long) -> Unit, onQueue: () -> Unit, onFavorite: () -> Unit,
    onShuffle: () -> Unit, onRepeat: () -> Unit, onMore: () -> Unit,
    onArtist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            factory = { WitchPlayerView(it) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            update = { view ->
                view.setAudioUri(audioUri)
                view.update(title, playing, buffering, position, duration, object : WitchPlayerView.Controls {
                    override fun playPause() = onPlayPause()
                    override fun previous() = onPrevious()
                    override fun next() = onNext()
                    override fun seek(position: Long) = onSeek(position)
                    override fun queue() = onQueue()
                })
            }
        )
        Text(
            artist,
            modifier = if (onArtist != null) Modifier.clickable(onClick = onArtist).padding(8.dp) else Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconToggleButton(checked = shuffle, onCheckedChange = { onShuffle() }) {
                Icon(Icons.Default.Shuffle, "随机播放")
            }
            IconButton(onClick = onRepeat) {
                Icon(if (repeat == 1) Icons.Default.RepeatOne else Icons.Default.Repeat, "循环模式",
                    tint = if (repeat != 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "收藏")
            }
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreHoriz, "更多歌曲操作") }
        }
    }
}
