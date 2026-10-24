package cp.player.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DownloadsStatus(model: EPlayerViewModel) {
    val jobs by model.downloads.collectAsState()
    if (jobs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        jobs.forEach { job ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(job.record.track.song.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (job.record.error != null) TextButton(onClick = { model.retryDownload(job.record) }) { Text("重试") }
                    IconButton(onClick = { model.cancelDownload(job.record.key) }) { Icon(Icons.Default.Close, "取消下载") }
                }
                if (job.record.error != null) Text(job.record.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else if (job.total > 0) LinearProgressIndicator(progress = { (job.bytes.toDouble() / job.total).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
