package cp.player.core.lyrics

import com.google.gson.JsonParser

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
