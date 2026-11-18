package cp.player.app

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.StringReader
import java.math.BigDecimal
import java.math.RoundingMode
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

// Defaults retain the old original/translated cache shape. Missing Gson fields are normalized at the boundary.
data class LyricsText(val original: String, val translated: String = "", val wordTimed: String = "",
    val romanized: String = "", val ttml: String = "", val ttmlSource: String = "", val cacheVersion: Int = 2, val fetchedAt: Long = 0, val communityCheckedAt: Long = 0)
data class TimedWord(val text: String, val start: Long, val end: Long, val timed: Boolean = true)
data class TimedLine(val time: Long, val text: String, val end: Long? = null, val words: List<TimedWord> = emptyList(),
    val speaker: String? = null, val right: Boolean = false, val translation: String = "", val romanized: String = "",
    val background: Boolean = false, val instrumental: Boolean = false, val credit: Boolean = false)

object LyricPayload {
    fun decode(json: JsonObject): LyricsText {
        fun lyric(key: String): String = json.get(key)?.let { value -> when {
            value.isJsonPrimitive -> value.asString
            value.isJsonObject -> value.asJsonObject.get("lyric")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            else -> ""
        } }.orEmpty()
        require(json.get("code")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull() in listOf(null, 200) || json.has("lrc")) { "歌词暂不可用" }
        return LyricsText(lyric("lrc").ifBlank { "暂无歌词" }, lyric("ytlrc").ifBlank { lyric("tlyric") },
            lyric("yrc"), lyric("yromalrc").ifBlank { lyric("romalrc") }, lyric("ttml"))
    }
    fun normalize(value: LyricsText) = value.copy(original = value.original.orEmpty(), translated = value.translated.orEmpty(),
        wordTimed = value.wordTimed.orEmpty(), romanized = value.romanized.orEmpty(), ttml = value.ttml.orEmpty(), ttmlSource = value.ttmlSource.orEmpty())
}

/** Real timing is retained. Plain LRC is never divided into fabricated word intervals. */
object LyricParser {
    private val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    private val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    private val yrcLine = Regex("^\\[(\\d+),(\\d+)](.*)$")
    private val yrcWord = Regex("\\((\\d+),(\\d+),[^)]*\\)")
    private val enhanced = Regex("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>")
    private fun milliseconds(minutes: String, seconds: String, fraction: String) =
        minutes.toLong() * 60000 + seconds.toLong() * 1000 + fraction.padEnd(3, '0').take(3).toLongOrNull().orZero()
    private fun Long?.orZero() = this ?: 0L

    fun parse(text: String): List<TimedLine> {
        if (text.length > 2_000_000) return emptyList()
        if (text.trimStart().startsWith("<")) return parseTtml(text)
        if (text.lineSequence().any { yrcLine.matches(it.trim()) }) return parseYrc(text)
        val shift = offset.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val result = mutableListOf<TimedLine>()
        text.lineSequence().take(10000).forEach { raw ->
            val line = raw.trim()
            val credit = if (line.startsWith("{")) runCatching {
                val value = JsonParser.parseString(line).asJsonObject
                val fragments = value.getAsJsonArray("c") ?: return@runCatching null
                val content = fragments.joinToString("") { it.asJsonObject.get("tx")?.asString.orEmpty() }.trim()
                TimedLine(((value.get("t")?.asLong ?: 0) + shift).coerceAtLeast(0), content, credit = true)
            }.getOrNull() else null
            if (credit != null) { if (credit.text.isNotBlank()) result += credit; return@forEach }
            val stamps = stamp.findAll(line).toList()
            val content = stamp.replace(line, "").trim()
            if (stamps.isEmpty()) {
                if (content.isNotBlank() && !content.startsWith("[")) result += TimedLine(0, content)
            } else stamps.forEach { match ->
                val start = (milliseconds(match.groupValues[1], match.groupValues[2], match.groupValues[3]) + shift).coerceAtLeast(0)
                val markers = enhanced.findAll(content).toList()
                if (markers.isEmpty()) result += TimedLine(start, content, instrumental = content.isBlank())
                else {
                    val words = markers.mapIndexedNotNull { i, marker ->
                        val word = content.substring(marker.range.last + 1, markers.getOrNull(i + 1)?.range?.first ?: content.length)
                        if (word.isEmpty()) null else {
                            val at = (milliseconds(marker.groupValues[1], marker.groupValues[2], marker.groupValues[3]) + shift).coerceAtLeast(0)
                            val endMarker = markers.getOrNull(i + 1)
                            val end = endMarker?.let { (milliseconds(it.groupValues[1], it.groupValues[2], it.groupValues[3]) + shift).coerceAtLeast(at) }
                            // An absent end marker gives no final-word duration: keep its interval untimed.
                            TimedWord(word, at, end ?: at, timed = end != null)
                        }
                    }
                    result += TimedLine(start, words.joinToString("") { it.text }, words.lastOrNull()?.takeIf { it.timed }?.end, words)
                }
            }
        }
        return result.sortedBy { it.time }
    }

    fun parseYrc(text: String): List<TimedLine> {
        val shift = offset.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val credits = text.lineSequence().filter { it.trimStart().startsWith("{") }.flatMap { parse(it).asSequence() }
            .map { it.copy(time = (it.time + shift).coerceAtLeast(0)) }.toList()
        return (credits + text.lineSequence().take(10000).mapNotNull { raw ->
            val line = yrcLine.matchEntire(raw.trim()) ?: return@mapNotNull null
            val start = line.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val duration = line.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            val markers = yrcWord.findAll(line.groupValues[3]).take(2000).toList()
            val timedWords = markers.mapIndexedNotNull { index, match ->
                val at = match.groupValues[1].toLongOrNull() ?: return@mapIndexedNotNull null
                val length = match.groupValues[2].toLongOrNull()?.coerceIn(0, 86_400_000) ?: return@mapIndexedNotNull null
                if (at > 86_400_000) return@mapIndexedNotNull null
                val value = line.groupValues[3].substring(match.range.last + 1, markers.getOrNull(index + 1)?.range?.first ?: line.groupValues[3].length)
                TimedWord(value, (at + shift).coerceAtLeast(0), (at + length + shift).coerceAtLeast(0))
            }
            val prefix = line.groupValues[3].substring(0, markers.firstOrNull()?.range?.first ?: 0)
            val words = (if (prefix.isNotEmpty()) listOf(TimedWord(prefix, (start + shift).coerceAtLeast(0), (start + shift).coerceAtLeast(0), false)) else emptyList()) + timedWords
            val plain = if (words.isEmpty()) line.groupValues[3] else words.joinToString("") { it.text }
            TimedLine((start + shift).coerceAtLeast(0), plain, (start + duration + shift).coerceAtLeast(0), words,
                instrumental = plain.isBlank())
        }.toList()).sortedBy { it.time }
    }

    private fun Element.attribute(name: String): String {
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            if ((attr.localName ?: attr.nodeName.substringAfter(':')) == name) return attr.nodeValue
        }
        return ""
    }
    private fun time(value: String): Long? {
        val raw = value.trim()
        if (raw.isEmpty()) return null
        return runCatching {
            val milliseconds = when {
                raw.endsWith("ms") -> raw.dropLast(2).toBigDecimal()
                raw.endsWith("s") -> raw.dropLast(1).toBigDecimal().multiply(BigDecimal.valueOf(1000))
                else -> raw.split(':').fold(BigDecimal.ZERO) { total, part ->
                    total.multiply(BigDecimal.valueOf(60)).add(part.toBigDecimal())
                }.multiply(BigDecimal.valueOf(1000))
            }
            milliseconds.setScale(0, RoundingMode.HALF_UP).longValueExact().coerceAtLeast(0)
        }.getOrNull()
    }

    private fun descendants(node: Element, name: String): List<Element> {
        val found = node.getElementsByTagNameNS("*", name)
        return (0 until found.length).map { found.item(it) as Element }
    }
    private val auxiliaryRoles = setOf("x-translation", "x-roman", "x-bg")

    private fun ttmlRoot(text: String): Element? {
        if (text.length > 2_000_000 || Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        return runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
                runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            }
            val builder = factory.newDocumentBuilder().apply {
                setEntityResolver { _, _ -> InputSource(StringReader("")) }
                setErrorHandler(org.xml.sax.helpers.DefaultHandler())
            }
            builder.parse(InputSource(StringReader(text))).documentElement.also {
                require((it.localName ?: it.nodeName.substringAfter(':')) == "tt")
            }
        }.getOrNull()
    }
    fun matchesNeteaseId(text: String, id: String): Boolean {
        val root = ttmlRoot(text) ?: return false
        val ids = descendants(root, "meta").filter { it.attribute("key") == "ncmMusicId" }.map { it.attribute("value") }
        return ids.isEmpty() || id in ids
    }
    fun parseTtml(text: String): List<TimedLine> {
        val root = ttmlRoot(text) ?: return emptyList()
        return runCatching {
            val declared = descendants(root, "agent").map { it.attribute("id") }.filter { it.isNotBlank() }
            val speakers = declared.sortedBy { if (it == "v1") 0 else 1 }.toMutableList()
            val result = mutableListOf<TimedLine>()
            fun readWords(container: Element, from: Long, until: Long?): List<TimedWord> {
                val words = mutableListOf<TimedWord>()
                fun read(node: Node, start: Long, end: Long?, precise: Boolean) {
                    if (node is Element) {
                        if (node !== container && node.attribute("role") in auxiliaryRoles) return
                        val ownStart = time(node.attribute("begin"))
                        val at = ownStart ?: start
                        val ownEnd = time(node.attribute("end")) ?: time(node.attribute("dur"))?.let { at + it }
                        val stop = ownEnd ?: end
                        val accurate = precise || ((node !== container || container.localName == "span") && ownStart != null && ownEnd != null)
                        for (i in 0 until node.childNodes.length) read(node.childNodes.item(i), at, stop, accurate)
                    } else if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
                        val value = node.nodeValue.orEmpty()
                        if (value.isNotEmpty() && !(value.isBlank() && ('\n' in value || '\r' in value)))
                            words += TimedWord(value, start, (end ?: start).coerceAtLeast(start), precise && end != null)
                    }
                }
                read(container, from, until, false)
                return words
            }
            descendants(root, "p").take(10000).forEach { p ->
                val start = time(p.attribute("begin")) ?: return@forEach
                val end = time(p.attribute("end")) ?: time(p.attribute("dur"))?.let { start + it }
                var owner: Node? = p
                var speaker = ""
                while (owner is Element && speaker.isBlank()) { speaker = owner.attribute("agent"); owner = owner.parentNode }
                if (speaker.isNotBlank() && speaker !in speakers) speakers += speaker
                val right = speaker.isNotBlank() && speakers.indexOf(speaker) > 0
                val spans = descendants(p, "span")
                fun auxiliary(container: Element, role: String): String = descendants(container, "span").firstOrNull { span ->
                    if (span.attribute("role") != role) false else {
                        var ancestor = span.parentNode
                        var nestedBackground = false
                        while (ancestor is Element && ancestor !== container) {
                            if (ancestor.attribute("role") == "x-bg") nestedBackground = true
                            ancestor = ancestor.parentNode
                        }
                        !nestedBackground
                    }
                }?.textContent?.trim().orEmpty()
                val rawWords = readWords(p, start, end)
                val content = rawWords.joinToString("") { it.text }
                if (content.isNotBlank()) result += TimedLine(start, content, end,
                    if (rawWords.any { it.timed }) rawWords else emptyList(), speaker.ifBlank { null }, right,
                    auxiliary(p, "x-translation"), auxiliary(p, "x-roman"))
                spans.filter { it.attribute("role") == "x-bg" }.forEach { bg ->
                    val at = time(bg.attribute("begin")) ?: start
                    val stop = time(bg.attribute("end")) ?: end
                    val words = readWords(bg, at, stop)
                    val value = words.joinToString("") { it.text }
                    if (value.isNotBlank()) result += TimedLine(at, value, stop, if (words.any { it.timed }) words else emptyList(),
                        speaker.ifBlank { null }, right, auxiliary(bg, "x-translation"), auxiliary(bg, "x-roman"), background = true)
                }
            }
            result.sortedWith(compareBy<TimedLine> { it.time }.thenBy { it.background })
        }.getOrElse { emptyList() }
    }

    fun document(raw: LyricsText, duration: Long = 0): List<TimedLine> {
        val text = LyricPayload.normalize(raw)
        val xml = parseTtml(text.ttml)
        val precise = parseYrc(text.wordTimed)
        val selected = xml.ifEmpty { precise.ifEmpty { parse(text.original) } }
        val translations = parse(text.translated).filter { it.text.isNotBlank() }
        val romanized = parse(text.romanized).filter { it.text.isNotBlank() }
        fun nearest(lines: List<TimedLine>, at: Long): String {
            val before = index(lines, at)
            return listOfNotNull(lines.getOrNull(before), lines.getOrNull(before + 1)).minByOrNull { kotlin.math.abs(it.time - at) }
                ?.takeIf { kotlin.math.abs(it.time - at) <= 800 }?.text.orEmpty()
        }
        val enriched = selected.map { line -> line.copy(
            translation = if (line.instrumental || line.text.isBlank()) "" else line.translation.ifBlank { if (line.background) "" else nearest(translations, line.time) },
            romanized = if (line.instrumental || line.text.isBlank()) "" else line.romanized.ifBlank { if (line.background) "" else nearest(romanized, line.time) }) }
        val result = enriched.toMutableList()
        val vocals = enriched.filter { !it.credit && !it.background && !it.instrumental && it.text.isNotBlank() }
        vocals.firstOrNull()?.takeIf { it.time >= 4000 }?.let { first ->
            if (enriched.none { it.instrumental && it.time < first.time })
                result += TimedLine(0, "", first.time, instrumental = true)
        }
        var occupiedUntil: Long? = null
        enriched.filter { !it.credit && !it.instrumental && it.text.isNotBlank() }.forEach { line ->
            occupiedUntil?.takeIf { line.time - it >= 2500 }?.let { end ->
                if (enriched.none { it.instrumental && it.time in end until line.time })
                    result += TimedLine(end, "", line.time, instrumental = true)
            }
            occupiedUntil = line.end?.let { maxOf(it, occupiedUntil ?: it) }
        }
        occupiedUntil?.takeIf { duration - it >= 2500 }?.let { end ->
            if (enriched.none { it.instrumental && it.time >= end }) result += TimedLine(end, "", duration, instrumental = true)
        }
        return result.sortedWith(compareBy<TimedLine> { it.time }.thenBy { it.background })
    }

    fun index(lines: List<TimedLine>, position: Long): Int {
        var low = 0; var high = lines.lastIndex; var found = -1
        while (low <= high) { val middle = (low + high) ushr 1; if (lines[middle].time <= position) { found = middle; low = middle + 1 } else high = middle - 1 }
        return found
    }
    fun focus(lines: List<TimedLine>, position: Long): Int {
        val last = index(lines, position)
        for (i in last downTo 0) {
            val line = lines[i]
            if (!line.background && !line.credit && (line.end == null || position < line.end)) return i
        }
        return last
    }
    fun progress(word: TimedWord, position: Long): Float = when {
        !word.timed -> if (position >= word.start) 1f else 0f
        word.end <= word.start -> if (position >= word.start) 1f else 0f
        else -> ((position - word.start).toDouble() / (word.end - word.start)).toFloat().coerceIn(0f, 1f)
    }
}
