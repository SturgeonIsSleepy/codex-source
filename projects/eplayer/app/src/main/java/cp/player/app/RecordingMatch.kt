package cp.player.app

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal data class Recording(val id: String, val title: String, val artists: List<String>, val durationMs: Long,
    val album: String = "", val artistAliases: List<String> = emptyList(), val titleAliases: List<String> = emptyList())

/** Rank catalog evidence instead of rejecting translations or guessing the first result. */
internal object RecordingMatch {
    fun text(value: String): String = CjkFold.apply(Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT))
        .filter { it.isLetterOrDigit() }
    private val promotional = Regex("(?i)(?:[\\[(【]\\s*(?:official (?:audio|music video|lyric video|video)|lyrics? video|mv)\\s*[\\])】]|\\s+(?:official (?:audio|music video|lyric video|video)|lyrics? video|mv)$)")
    private val separator = Regex("\\s+/\\s+|\\s*,\\s*|、")
    private val version = Regex("(?i)\\b(live|remix|instrumental|acoustic|cover|remaster(?:ed)?|radio edit|tv size)\\b|现场|現場|伴奏|翻唱|ライブ|カバー")
    private fun title(value: String) = text(value.replace(promotional, " "))
    private fun names(values: List<String>) = values.flatMap { it.split(separator) }.map(::text)
        .filter { it.isNotEmpty() && it !in setOf("unknown", "unknownartist", "未知歌手") }.toSet()
    private fun score(expected: Recording, candidate: Recording): Int? {
        if (candidate.id.isBlank() || expected.durationMs <= 0 || candidate.durationMs <= 0) return null
        val gap = abs(expected.durationMs - candidate.durationMs)
        if (gap > 6000) return null
        val expectedVersions = version.findAll(expected.title).map { text(it.value) }.toSet()
        val candidateVersions = version.findAll(candidate.title).map { text(it.value) }.toSet()
        if (expectedVersions != candidateVersions) return null
        val a = names(expected.artists); val b = names(candidate.artists)
        if (a.isEmpty() || b.isEmpty()) return null
        val title = title(expected.title)
        if (title.isEmpty()) return null
        val exactTitle = title == title(candidate.title)
        val titleAlias = (expected.titleAliases.map(::title) + title).toSet()
            .intersect((candidate.titleAliases.map(::title) + title(candidate.title)).toSet()).any { it.isNotEmpty() }
        if (!exactTitle && !titleAlias) return null
        val same = a == b
        val shared = a.intersect(b).isNotEmpty()
        val alias = names(expected.artists + expected.artistAliases).intersect(names(candidate.artists + candidate.artistAliases)).isNotEmpty()
        if (!shared && !alias) return null
        val artistScore = when { same -> 35; a.containsAll(b) || b.containsAll(a) -> 28; !shared && alias -> 28; else -> 20 }
        val album = text(expected.album)
        val sameAlbum = album.isNotEmpty() && album != "unknown" && album == text(candidate.album)
        // Partial collaborations need close duration or the same album; full/alias identities allow a mastering gap.
        if (artistScore == 20 && gap > 2500 && !sameAlbum) return null
        return (if (exactTitle) 50 else 45) + artistScore + (if (gap <= 2500) 15 else 8) + (if (sameAlbum) 10 else 0)
    }
    fun choose(expected: Recording, candidates: List<Recording>): String? {
        val ranked = candidates.distinctBy { it.id }.mapNotNull { c -> score(expected, c)?.let { c.id to it } }
            .sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return null
        if (best.second < 78 || ranked.getOrNull(1)?.let { best.second - it.second < 6 } == true) return null
        return best.first
    }
}

internal fun Track.recording() = Recording(remoteId ?: song.id, song.name,
    song.artists.orEmpty().map { it.name }.ifEmpty { listOf(song.artist) }, song.durationMs, song.album,
    song.artistAliases.orEmpty(), song.titleAliases.orEmpty())
