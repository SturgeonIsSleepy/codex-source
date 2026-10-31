package cp.player.core.lyrics

import com.google.gson.Gson
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** One versioned private cache holds both source lyrics and optional role-aware TTML. */
class LyricsRepository(private val directory: File, private val now: () -> Long = System::currentTimeMillis,
    private val load: suspend (String, String) -> LyricsText) {
    companion object {
        private const val MAX_TEXT = 2_000_000
        private const val CACHE_AGE = 7L * 24 * 60 * 60 * 1000
        private val client = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false).build()
    }
    // Nullable DTO: Gson may read older or damaged files without calling Kotlin constructors.
    private data class CachedLyrics(val original: String?, val translated: String?, val wordTimed: String?,
        val romanized: String?, val ttml: String?, val ttmlSource: String?, val cacheVersion: Int,
        val fetchedAt: Long, val communityCheckedAt: Long)

    private fun hasContent(text: LyricsText) =
        text.original.isNotBlank() && text.original != "暂无歌词" || text.wordTimed.isNotBlank() || text.ttml.isNotBlank()

    private fun readCache(file: File): LyricsText? = runCatching {
        val value = Gson().fromJson(file.readText(), CachedLyrics::class.java) ?: return@runCatching null
        LyricsText(value.original ?: return@runCatching null, value.translated ?: return@runCatching null,
            value.wordTimed.orEmpty(), value.romanized.orEmpty(), value.ttml.orEmpty(), value.ttmlSource.orEmpty(),
            value.cacheVersion, value.fetchedAt, value.communityCheckedAt).takeIf(::hasContent)
    }.getOrNull()

    suspend fun get(source: String, id: String, update: suspend (LyricsText) -> Unit = {}): LyricsText = withContext(Dispatchers.IO) {
        val hash = MessageDigest.getInstance("SHA-256").digest("$source:$id".toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(directory, "$hash.json")
        val cached = if (file.isFile && file.length() <= MAX_TEXT * 3L) readCache(file) else null
        if (cached != null) update(cached)
        val now = now()
        val sourceFresh = cached != null && cached.cacheVersion >= 2 && now - cached.fetchedAt.takeIf { it > 0 }.orFileTime(file) < CACHE_AGE
        val communityEligible = source == "netease-cloudmusic-rs" && id.matches(Regex("[0-9]{1,20}"))
        val communityFresh = !communityEligible || (cached != null && cached.communityCheckedAt > 0 &&
            now - cached.communityCheckedAt < if (cached.ttml.isNotBlank()) CACHE_AGE else 15 * 60 * 1000)
        // Only community TTML has an independent freshness window; source TTML follows source expiry.
        if (cached != null && cached.ttml.isNotBlank() && communityEligible && communityFresh) return@withContext cached
        if (cached != null && (sourceFresh && communityFresh || cached.cacheVersion < 2 && cached.communityCheckedAt > 0 && now - cached.communityCheckedAt < 60_000))
            return@withContext cached
        val refreshed = coroutineScope {
            val sourceLyrics = async {
                if (sourceFresh) cached else try {
                    load(source, id).copy(fetchedAt = now).also {
                        if (cached?.ttml.isNullOrBlank()) update(it)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
            val community = async { if (!communityFresh && communityEligible) community(id) else null }
            val base = sourceLyrics.await()?.let { fresh ->
                if (fresh.ttml.isBlank() && cached?.ttml?.isNotBlank() == true)
                    fresh.copy(ttml = cached.ttml, ttmlSource = cached.ttmlSource, communityCheckedAt = cached.communityCheckedAt)
                else fresh
            } ?: cached
            val extra = community.await()
            when {
                extra != null -> (base ?: LyricsText("")).copy(ttml = extra.first, ttmlSource = extra.second, cacheVersion = 2,
                    communityCheckedAt = now, fetchedAt = base?.fetchedAt?.takeIf { it > 0 } ?: now)
                base != null -> base.copy(communityCheckedAt = if (!communityFresh) now else base.communityCheckedAt)
                else -> LyricsText("暂无歌词")
            }
        }
        // A network failure preserves the old cache and its opportunity to upgrade later.
        currentCoroutineContext().ensureActive()
        if (hasContent(refreshed)) {
            Files.createDirectories(directory.toPath())
            val temporary = File.createTempFile("$hash-", ".tmp", file.parentFile)
            try {
                temporary.writeText(Gson().toJson(refreshed))
                currentCoroutineContext().ensureActive()
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { temporary.delete() }
        }
        refreshed
    }

    private fun Long?.orFileTime(file: File) = this ?: file.lastModified()

    private suspend fun community(id: String): Pair<String, String>? {
        val urls = listOf(
            "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main/ncm-lyrics/$id.ttml",
            "https://cdn.jsdelivr.net/gh/amll-dev/amll-ttml-db@main/ncm-lyrics/$id.ttml")
        for (url in urls) {
            currentCoroutineContext().ensureActive()
            try {
                val text = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.code == 404) return null
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    if (body.contentLength() > MAX_TEXT) return@use null
                    body.charStream().use { reader ->
                        val result = StringBuilder()
                        val buffer = CharArray(4096)
                        while (result.length <= MAX_TEXT) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            result.append(buffer, 0, count)
                        }
                        result.toString().takeIf { it.length <= MAX_TEXT }
                    }
                }
                if (text != null && LyricParser.matchesNeteaseId(text, id) && LyricParser.parseTtml(text).any { it.words.any(TimedWord::timed) })
                    return text to "https://github.com/amll-dev/amll-ttml-db/blob/main/ncm-lyrics/$id.ttml"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Existing source or cached lyrics remain usable. */ }
        }
        return null
    }
}
