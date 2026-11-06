package cp.player.app

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal data class Recording(val id: String, val title: String, val artists: List<String>, val durationMs: Long,
    val album: String = "")

/** No fuzzy title-only fallback: unknown artists, versions and ambiguous releases stay unmatched. */
internal object RecordingMatch {
    fun text(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }
    private fun title(value: String): String = text(value.replace(Regex("(?i)\\s*[\\[(]?(?:official (?:audio|music video|lyric video)|lyrics? video|official video)[\\])]?\\s*"), ""))
    private fun artists(values: List<String>) = values.flatMap { it.split(Regex("\\s+(?:&|feat\\.?|ft\\.?|and|x)\\s+|[、;/]", RegexOption.IGNORE_CASE)) }
        .map(::text).filter { it.isNotEmpty() }.toSet()
    fun choose(expected: Recording, candidates: List<Recording>): String? {
        val name = title(expected.title)
        val performers = artists(expected.artists)
        if (name.isEmpty() || performers.isEmpty() || expected.durationMs <= 0) return null
        val valid = candidates.distinctBy { it.id }.filter { candidate ->
            title(candidate.title) == name && artists(candidate.artists) == performers && candidate.durationMs > 0 &&
                abs(candidate.durationMs - expected.durationMs) <= 2500
        }
        val album = text(expected.album)
        val exactAlbum = valid.filter { album.isNotEmpty() && text(it.album) == album }
        return (if (album.isNotEmpty()) exactAlbum else valid).singleOrNull()?.id
    }
}

internal fun Track.recording() = Recording(remoteId ?: song.id, song.name, listOf(song.artist), song.durationMs, song.album)
