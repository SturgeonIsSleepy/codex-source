package cp.player.app

import android.content.Context
import cp.player.core.lyrics.LyricsRepository
import cp.player.core.lyrics.LyricsText
import cp.player.core.lyrics.LyricPayload
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android source adapter; the shared rich parser/cache do not depend on presentation. */
internal suspend fun loadLyrics(context: Context, source: String, id: String, update: (LyricsText) -> Unit = {}): LyricsText =
    LyricsRepository(File(context.applicationContext.filesDir, "lyrics")) { sourceId, trackId ->
        LyricPayload.decode(SourceStore.call(context.applicationContext, sourceId, "lyric/new", mapOf("id" to trackId)))
    }.get(source, id) { text -> withContext(Dispatchers.Main.immediate) { update(text) } }
