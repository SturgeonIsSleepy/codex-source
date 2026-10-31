package cp.player.app

import android.text.format.Formatter
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import cp.player.core.playback.PlaybackCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun StorageSettings(close: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableStateOf<Long?>(null) }
    var busy by remember { mutableStateOf(true) }
    var issue by remember { mutableStateOf<String?>(null) }
    suspend fun update(clear: Boolean) {
        busy = true
        issue = null
        try { bytes = if (clear) PlaybackCache.clear(context) else PlaybackCache.size(context) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            Log.w("EPlayerStorage", "Cache maintenance failed", error)
            issue = if (clear) "清理缓存失败，请重试" else "读取缓存失败，请重试"
        }
        finally { busy = false }
    }
    LaunchedEffect(Unit) { update(false) }
    TravelDialog(onDismissRequest = close) {
        Surface(Modifier.fillMaxSize().testTag("storage-panel")) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("存储空间", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = close) { Text("完成") }
                }
                HorizontalDivider()
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("流媒体缓存", style = MaterialTheme.typography.titleLarge)
                    Text(bytes?.let { Formatter.formatFileSize(context, it) } ?: "正在读取…",
                        style = MaterialTheme.typography.headlineLarge)
                    Text("最多 ${Formatter.formatFileSize(context, PlaybackCache.LIMIT_BYTES)}，空间达到上限后自动整理。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("在线音频会在继续播放时重新缓存。导入音频和已下载音乐仍保存在曲库中。")
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    issue?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(enabled = !busy, onClick = { scope.launch { update(false) } }) { Text("刷新") }
                        Button(enabled = !busy && (bytes ?: 0L) > 0,
                            onClick = { scope.launch { update(true) } }) { Text("清理缓存") }
                    }
                }
            }
        }
    }
}
