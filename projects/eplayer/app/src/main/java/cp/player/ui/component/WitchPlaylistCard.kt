package cp.player.ui.component

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cp.player.manager.WitchPlaylist
import cp.player.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WitchPlaylistCard(onPlay: (Song, List<Song>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var revision by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var target by rememberSaveable { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val selected = target
            importing = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { WitchPlaylist.import(context, selected, uri) } }
                importing = false
                revision++
                Toast.makeText(context, result.fold({ "音频已导入" }, { it.message ?: "导入失败" }), Toast.LENGTH_LONG).show()
            }
        }
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            coil3.compose.AsyncImage(
                model = "file:///android_asset/travel-sky.png", contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(88.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
            )
            Text("魔女の旅々", style = MaterialTheme.typography.headlineMedium)
            Text("两首旅途中的歌", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            WitchPlaylist.songs.forEachIndexed { index, song ->
                val imported = remember(revision) { WitchPlaylist.isImported(context, index) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        Text(song.name, style = MaterialTheme.typography.titleMedium)
                        Text(song.artist, style = MaterialTheme.typography.bodySmall)
                    }
                    if (imported) IconButton(onClick = { onPlay(song, WitchPlaylist.available(context)) }) {
                        Icon(Icons.Default.PlayArrow, "播放 ${song.name}")
                    }
                    IconButton(enabled = !importing, onClick = { target = index; picker.launch(arrayOf("audio/*")) }) {
                        Icon(if (imported) Icons.Default.DriveFileRenameOutline else Icons.Default.FileOpen,
                            if (imported) "更换 ${song.name} 的音频" else "导入 ${song.name} 的音频")
                    }
                }
            }
            if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            else Text("导入你拥有的音频后即可离线播放", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
