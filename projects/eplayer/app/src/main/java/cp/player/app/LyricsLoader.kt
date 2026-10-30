package cp.player.app

import android.content.Context
import cp.player.core.lyrics.LyricsRepository
import cp.player.core.lyrics.LyricsText
import java.io.File

/** Android/source adapter; the parser and cache do not depend on Compose or SourceStore. */
internal suspend fun loadLyrics(context: Context, source: String, id: String): LyricsText =
    LyricsRepository(File(context.applicationContext.filesDir, "lyrics")) { sourceId, trackId ->
        val response = SourceStore.call(context.applicationContext, sourceId, "lyric/new", mapOf("id" to trackId))
        LyricsText(response.obj("lrc")?.str("lyric") ?: "暂无歌词",
            response.obj("tlyric")?.str("lyric").orEmpty())
    }.get(source, id)
