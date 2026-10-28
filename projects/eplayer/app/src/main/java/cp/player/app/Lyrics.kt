package cp.player.app

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
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

/** Full-screen lyric reading: one active line, translations and seek by tapping. */
@Composable
internal fun LyricsScreen(text: LyricsText?, model: EPlayerViewModel, close: () -> Unit) {
    val position by model.position.collectAsState()
    val playback by model.playback.collectAsState()
    val quality by model.quality.collectAsState()
    val lines = remember(text) { LyricParser.parse(text?.original.orEmpty()) }
    val translations = remember(text) { LyricParser.parse(text?.translated.orEmpty()).associate { it.time to it.text } }
    val timed = remember(lines) { lines.any { it.time > 0 } }
    val active = remember(lines, position) { LyricParser.index(lines, position) }
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var follow by remember(text) { mutableStateOf(true) }
    var translate by remember { mutableStateOf(true) }
    var qualityOpen by remember { mutableStateOf(false) }
    LaunchedEffect(dragged) { if (dragged) follow = false }
    LaunchedEffect(active, follow) {
        if (follow && timed && active >= 0) {
            val offset = -(list.layoutInfo.viewportEndOffset / 3)
            list.animateScrollToItem(active, offset)
        }
    }
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF173044))) {
            AsyncImage(playback.song?.albumArtUrl ?: "file:///android_asset/travel-sky.png", null,
                Modifier.matchParentSize().blur(48.dp), contentScale = ContentScale.Crop, alpha = .42f)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x77101924), Color(0xCC101923)))))
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = close) { Icon(Icons.Default.KeyboardArrowDown, "关闭歌词", tint = Color.White) }
                    MusicCover(playback.song, Modifier.size(42.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(playback.song?.name.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(playback.song?.artist.orEmpty(), color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (translations.isNotEmpty()) TextButton(onClick = { translate = !translate }) {
                        Text("译", color = Color.White.copy(alpha = if (translate) 1f else .4f))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (text == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White, strokeWidth = 2.dp)
                    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(top = 80.dp, bottom = 160.dp)) {
                        itemsIndexed(lines) { index, line ->
                            val color by animateColorAsState(if (!timed || index == active) Color.White else Color.White.copy(alpha = .33f), tween(240), label = "lyric-emphasis")
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .then(if (timed) Modifier.clickable { model.seek(line.time); follow = true } else Modifier)
                                .padding(horizontal = 6.dp, vertical = 15.dp)) {
                                Text(line.text.ifBlank { "•••" }, color = color, fontSize = 31.sp, lineHeight = 43.sp,
                                    fontWeight = if (!timed || index == active) FontWeight.SemiBold else FontWeight.Medium)
                                if (translate) translations[line.time]?.takeIf(String::isNotBlank)?.let {
                                    Text(it, color = color.copy(alpha = if (!timed || index == active) .8f else .35f), fontSize = 18.sp,
                                        lineHeight = 28.sp, modifier = Modifier.padding(top = 7.dp))
                                }
                            }
                        }
                    }
                    if (!follow && timed) TextButton(onClick = { follow = true }, modifier = Modifier.align(Alignment.BottomEnd)) {
                        Icon(Icons.Default.ArrowDownward, null, tint = Color.White, modifier = Modifier.size(16.dp)); Text("回到当前", color = Color.White)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(playerTime(position), color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = model::previous) { Icon(Icons.Default.SkipPrevious, "上一首", tint = Color.White, modifier = Modifier.size(28.dp)) }
                        IconButton(onClick = model::playPause, modifier = Modifier.size(60.dp)) { Icon(if (playback.playRequested) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (playback.playRequested) "暂停" else "播放", tint = Color.White, modifier = Modifier.size(38.dp)) }
                        IconButton(onClick = model::next) { Icon(Icons.Default.SkipNext, "下一首", tint = Color.White, modifier = Modifier.size(28.dp)) }
                    }
                    TextButton(onClick = { qualityOpen = true }) { Text(qualityName(quality), color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
    if (qualityOpen) QualitySheet(model) { qualityOpen = false }
}
