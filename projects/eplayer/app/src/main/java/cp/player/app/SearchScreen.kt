package cp.player.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

@Composable
internal fun SearchScreen(query: String, change: (String) -> Unit, cloud: CloudViewModel, player: EPlayerViewModel,
    showPlayer: () -> Unit, options: (Track) -> Unit, close: () -> Unit) {
    val home by cloud.home.collectAsState()
    val found by cloud.searchResult.collectAsState()
    val searched by cloud.searched.collectAsState()
    val searching by cloud.searching.collectAsState()
    val searchType by cloud.searchType.collectAsState()
    val error by cloud.searchError.collectAsState()
    val recent by player.recent.collectAsState()
    val context = LocalContext.current.applicationContext
    TravelDialog(close) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var issue by remember { mutableStateOf<String?>(null) }
    fun submit(value: String, type: Int = searchType) {
        if (value.isBlank()) return
        change(value); keyboard?.hide(); focus.clearFocus()
        if (home.source == null) issue = "请先添加音源" else { issue = null; cloud.search(value, type) }
    }
    LaunchedEffect(query, home.source, searched) {
        suggestions = emptyList()
        if (query.isBlank() || searched) return@LaunchedEffect
        delay(200)
        val source = home.source ?: return@LaunchedEffect
        suggestions = try { MusicRepository(context, source).suggestions(query) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { recent.map { it.song.name }.filter { it.contains(query, true) }.distinct().take(8) }
    }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            requester.requestFocus()
            withFrameNanos { }
            keyboard?.show()
        }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { keyboard?.hide(); close() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                BasicTextField(query, { change(it); issue = null; cloud.clearSearch() },
                    Modifier.weight(1f).focusRequester(requester).padding(vertical = 12.dp),
                    singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                    decorationBox = { inner -> Box { if (query.isBlank()) Text("搜索歌曲、歌手、专辑", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
                if (query.isNotEmpty()) IconButton(onClick = { change(""); cloud.clearSearch(); requester.requestFocus(); keyboard?.show() }) { Icon(Icons.Default.Close, "清除") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
            issue?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            if (searched) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { MusicSegments(listOf("歌曲", "歌单", "专辑", "歌手"), listOf(1,1000,10,100).indexOf(searchType).coerceAtLeast(0)) { submit(query, listOf(1,1000,10,100)[it]) } }
                if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
                    items(found.entries, key = { "${it.kind}:${it.id}" }) { entry -> MusicEntryRow(entry) { cloud.open(entry) } }
                    items(found.tracks, key = { "${it.source}:${it.song.id}" }) { track -> SongRow(track, false, { player.play(track, found.tracks); showPlayer() }) {
                        IconButton(onClick = { options(track) }) { Icon(Icons.Default.MoreVert, "歌曲操作") }
                    } }
                    if (!searching && found.tracks.isEmpty() && found.entries.isEmpty()) item { Text(error ?: "没有找到音乐") }
                }
            } else LazyColumn(Modifier.weight(1f)) {
                val hints = if (query.isBlank()) recent.map { it.song.name }.distinct().take(8) else suggestions
                items(hints, key = { it }) { hint ->
                    Row(Modifier.fillMaxWidth().clickable { submit(hint) }.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (query.isBlank()) Icons.Default.History else Icons.Default.Search, null, Modifier.size(19.dp))
                        Text(hint, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { change(hint); cloud.clearSearch(); requester.requestFocus(); keyboard?.show() }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.NorthWest, "填入搜索框", Modifier.size(17.dp)) }
                    }
                }
                if (query.isNotBlank()) item { TextButton(onClick = { submit(query) }, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("搜索「$query」") } }
            }
        }
    }
}
