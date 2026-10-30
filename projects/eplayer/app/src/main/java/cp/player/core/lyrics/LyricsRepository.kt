package cp.player.core.lyrics

import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Source protocol and Android lifecycle belong to the caller; this owns only lyric data/cache. */
class LyricsRepository(
    private val directory: File,
    private val load: suspend (source: String, id: String) -> LyricsText,
) {
    private val gson = Gson()
    private data class CachedLyrics(val original: String?, val translated: String?)

    suspend fun get(source: String, id: String): LyricsText = withContext(Dispatchers.IO) {
        val hash = MessageDigest.getInstance("SHA-256").digest("$source:$id".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = File(directory, "$hash.json")
        if (file.isFile) {
            val cached = runCatching { gson.fromJson(file.readText(), CachedLyrics::class.java) }.getOrNull()
            val original = cached?.original
            val translated = cached?.translated
            if (!original.isNullOrBlank() && translated != null) {
                return@withContext LyricsText(original, translated)
            }
        }
        val text = load(source, id)
        coroutineContext.ensureActive()
        if (text.original.isNotBlank() && text.original != "暂无歌词") {
            Files.createDirectories(directory.toPath())
            // Different requests must never share a half-written file.
            val temporary = File.createTempFile("$hash-", ".tmp", directory)
            try {
                temporary.writeText(gson.toJson(text))
                coroutineContext.ensureActive()
                Files.move(temporary.toPath(), file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        }
        text
    }
}
