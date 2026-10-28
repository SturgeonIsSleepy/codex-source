package cp.player.app

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LyricsText(val original: String, val translated: String = "")
data class TimedLine(val time: Long, val text: String)

object LyricParser {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    fun parse(text: String): List<TimedLine> {
        val shift = offset.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val result = mutableListOf<TimedLine>()
        text.lines().forEach { line ->
            // NCM embeds author/composer credits as JSON among normal LRC lines.
            // Render the text fragments, never the transport representation.
            val credit = if (line.trimStart().startsWith("{")) runCatching {
                val value = JsonParser.parseString(line).asJsonObject
                val fragments = value.getAsJsonArray("c") ?: return@runCatching null
                val content = fragments.joinToString("") { it.asJsonObject.get("tx")?.asString.orEmpty() }.trim()
                TimedLine(((value.get("t")?.asLong ?: 0L) + shift).coerceAtLeast(0), content)
            }.getOrNull() else null
            if (credit != null) {
                if (credit.text.isNotBlank()) result += credit
                return@forEach
            }
            val stamps = stamp.findAll(line).toList()
            val content = stamp.replace(line, "").trim()
            if (stamps.isEmpty()) {
                if (content.isNotBlank() && !content.startsWith("[")) result += TimedLine(0, content)
            } else stamps.forEach { match ->
                val millis = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
                result += TimedLine((match.groupValues[1].toLong() * 60000 + match.groupValues[2].toLong() * 1000 + millis + shift).coerceAtLeast(0), content)
            }
        }
        return result.sortedBy { it.time }
    }
    fun index(lines: List<TimedLine>, position: Long): Int {
        var low = 0; var high = lines.lastIndex; var found = -1
        while (low <= high) { val middle = (low + high) ushr 1; if (lines[middle].time <= position) { found = middle; low = middle + 1 } else high = middle - 1 }
        return found
    }
}

class LyricsRepository(private val context: Context) {
    suspend fun get(source: String, id: String): LyricsText = withContext(Dispatchers.IO) {
        val hash = MessageDigest.getInstance("SHA-256").digest("$source:$id".toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(context.filesDir, "lyrics/$hash.json")
        if (file.isFile) runCatching { Gson().fromJson(file.readText(), LyricsText::class.java) }.getOrNull()?.let { return@withContext it }
        val response = SourceStore.call(context, source, "lyric/new", mapOf("id" to id))
        val text = LyricsText(response.obj("lrc")?.str("lyric") ?: "暂无歌词", response.obj("tlyric")?.str("lyric").orEmpty())
        if (text.original != "暂无歌词") {
            file.parentFile!!.mkdirs()
            val temporary = File(file.parentFile, "$hash.tmp")
            temporary.writeText(Gson().toJson(text)); temporary.renameTo(file)
        }
        text
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsSheet(text: LyricsText?, model: EPlayerViewModel, close: () -> Unit) {
    val position by model.position.collectAsState()
    val lines = remember(text) { LyricParser.parse(text?.original.orEmpty()) }
    val translations = remember(text) { LyricParser.parse(text?.translated.orEmpty()).associate { it.time to it.text } }
    val timed = remember(lines) { lines.any { it.time > 0 } }
    val active = remember(lines, position) { LyricParser.index(lines, position) }
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var follow by remember(text) { mutableStateOf(true) }
    var translate by remember { mutableStateOf(true) }
    LaunchedEffect(dragged) { if (dragged) follow = false }
    LaunchedEffect(active, follow) { if (follow && timed && active >= 0) list.animateScrollToItem((active - 2).coerceAtLeast(0)) }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.85f).padding(horizontal = 24.dp)) {
            Row {
                Text("歌词", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (!follow && timed) TextButton(onClick = { follow = true }) { Text("跟随播放") }
                if (translations.isNotEmpty()) TextButton(onClick = { translate = !translate }) { Text(if (translate) "隐藏译文" else "译文") }
            }
            if (text == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(vertical = 24.dp)) {
                itemsIndexed(lines) { index, line ->
                    Column(Modifier.fillMaxWidth().then(if (timed) Modifier.clickable { model.seek(line.time); follow = true } else Modifier).padding(vertical = 15.dp)) {
                        Text(line.text, style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (timed && index == active) FontWeight.Bold else FontWeight.Medium,
                            color = if (!timed || index == active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        if (translate) translations[line.time]?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
            }
        }
    }
}
