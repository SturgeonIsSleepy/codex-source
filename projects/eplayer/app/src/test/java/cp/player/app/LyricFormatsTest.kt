package cp.player.app

import cp.player.core.lyrics.LyricParser

import cp.player.core.lyrics.LyricPayload

import cp.player.core.lyrics.LyricsText

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class LyricFormatsTest {
    @Test fun yrcKeepsAbsoluteWordTimesDurationsPunctuationAndSpaces() {
        val line = LyricParser.parse("[120000,2000](120000,700,0)Hello (120700,300,0)(yes)(121000,1000,0) world").single()
        assertEquals(120000L, line.time)
        assertEquals(122000L, line.end)
        assertEquals("Hello (yes) world", line.text)
        assertEquals(listOf(120000L, 120700L, 121000L), line.words.map { it.start })
        assertEquals(listOf(120700L, 121000L, 122000L), line.words.map { it.end })
        assertEquals(.5f, LyricParser.progress(line.words.last(), 121500), .001f)
        assertEquals(0f, LyricParser.progress(line.words.last(), 120999), .001f)
        assertEquals(1f, LyricParser.progress(line.words.last(), 122000), .001f)
    }
    @Test fun yrcOffsetAndZeroLengthWordsDoNotDivideByZero() {
        val line = LyricParser.parse("[offset:-100]\n[1000,500](1000,0,0)A(1000,500,0)B").single()
        assertEquals(900L, line.time)
        assertEquals(1f, LyricParser.progress(line.words.first(), 900), 0f)
        assertEquals(0f, LyricParser.progress(line.words.first(), 899), 0f)
    }
    @Test fun enhancedLrcUsesRealMarkersAndLeavesUnknownFinalWordUntimed() {
        val marked = LyricParser.parse("[00:01]<00:01>A<00:01.500>B<00:02>").single()
        assertEquals("AB", marked.text)
        assertEquals(listOf(1000L, 1500L), marked.words.map { it.start })
        assertEquals(listOf(1500L, 2000L), marked.words.map { it.end })
        assertTrue(marked.words.all { it.timed })
        val unknown = LyricParser.parse("[00:01]<00:01>A").single()
        assertFalse(unknown.words.single().timed)
        assertTrue(LyricParser.parse("[00:01]ordinary words").single().words.isEmpty())
    }
    @Test fun ttmlPreservesOtherSpeakerTranslationRomanizationAndBackground() {
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
          <head><metadata><ttm:agent xml:id="v1" type="person"/><ttm:agent xml:id="v2" type="other"/></metadata></head>
          <body><div><p begin="1.030" end="2.850" ttm:agent="v1"><span begin="1.030" end="1.360">Hi </span><span begin="1.360" end="2.850">there</span><span ttm:role="x-bg" begin="2.0" end="3.0"><span begin="2.0" end="3.0">echo</span><span ttm:role="x-translation">echo translated</span></span><span ttm:role="x-translation">译文</span><span ttm:role="x-roman">roman</span></p>
          <p begin="0:01.500" end="0:02.500" ttm:agent="v2"><span begin="1.5" end="2.5">reply</span></p></div></body></tt>"""
        val lines = LyricParser.parseTtml(xml)
        assertEquals(3, lines.size)
        val first = lines.first()
        assertEquals("Hi there", first.text)
        assertEquals(1030L, first.words.first().start)
        assertEquals("译文", first.translation)
        assertEquals("roman", first.romanized)
        assertFalse(first.right)
        val response = lines.single { it.speaker == "v2" }
        assertTrue(response.right)
        assertFalse(response.background)
        val background = lines.single { it.background }
        assertFalse(background.right)
        assertEquals("echo", background.text)
        assertEquals("echo translated", background.translation)
        assertTrue(background.words.single().timed)
    }
    @Test fun sourceIdMetadataAndDecimalMillisecondsRemainExact() {
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:a="urn:amll"><head><metadata><a:meta key="ncmMusicId" value="123"/></metadata></head><body><div><p begin="1.001" end="2.001"><span begin="1.001" end="2.001">fixture</span></p></div></body></tt>"""
        assertTrue(LyricParser.matchesNeteaseId(xml, "123"))
        assertFalse(LyricParser.matchesNeteaseId(xml, "456"))
        assertEquals(1001L, LyricParser.parseTtml(xml).single().words.single().start)
    }
    @Test fun yrcRetainsItsJsonCreditsWithoutLeakingTransportText() {
        val lines = LyricParser.parse("""{"t":100,"c":[{"tx":"Composer: fixture"}]}
[1000,1000](1000,1000,0)line""")
        assertEquals("Composer: fixture", lines.first().text)
        assertTrue(lines.first().credit)
        assertEquals("line", lines.last().text)
    }
    @Test fun parserRejectsExternalEntitiesAndMalformedXml() {
        assertTrue(LyricParser.parseTtml("<!DOCTYPE tt [<!ENTITY leak SYSTEM 'file:///private'>]><tt><p begin='1' end='2'>&leak;</p></tt>").isEmpty())
        assertTrue(LyricParser.parseTtml("<tt><p").isEmpty())
        assertTrue(LyricParser.parseTtml("<html><p begin='1' end='2'>wrong</p></html>").isEmpty())
    }
    @Test fun gapMarkersUseRealVocalEndsAndDoNotInterruptOverlappingVoices() {
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div>
          <p begin="0" end="8"><span begin="0" end="8">long</span></p>
          <p begin="2" end="3"><span begin="2" end="3">overlap</span></p>
          <p begin="12" end="13"><span begin="12" end="13">next</span></p>
        </div></body></tt>"""
        val lines = LyricParser.document(LyricsText("", ttml = xml))
        val gap = lines.single { it.instrumental }
        assertEquals(8000L, gap.time)
        assertEquals(12000L, gap.end)
        assertEquals(lines.indexOfFirst { it.text == "long" }, LyricParser.focus(lines, 4000))
        assertEquals(lines.indexOf(gap), LyricParser.focus(lines, 9000))
        val plain = LyricParser.document(LyricsText("[00:01]one\n[00:10]two"))
        assertFalse(plain.any { it.instrumental })
    }
    @Test fun finalInstrumentalUsesKnownAudioDurationRatherThanAnInventedWordLength() {
        val lines = LyricParser.document(LyricsText("", wordTimed = "[1000,1000](1000,1000,0)last"), 6000)
        val outro = lines.single { it.instrumental }
        assertEquals(2000L, outro.time)
        assertEquals(6000L, outro.end)
    }
    @Test fun oldCachesNormalizeMissingFieldsWithoutPretendingWordTiming() {
        val raw = Gson().fromJson("""{"original":"[00:01]old","translated":"[00:01]译"}""", LyricsText::class.java)
        val data = LyricPayload.normalize(raw)
        assertEquals("", data.wordTimed)
        assertEquals("", data.ttml)
        assertEquals(0, data.cacheVersion)
        assertTrue(LyricParser.document(data).single().words.isEmpty())
    }
    @Test fun payloadKeepsYrcAndMatchingTranslationInsteadOfOnlyOldLrc() {
        val json = JsonParser.parseString("""{"code":200,"lrc":{"lyric":"[00:01]old"},"yrc":{"lyric":"[1000,1000](1000,1000,0)precise"},"ytlrc":{"lyric":"[00:01]译"},"yromalrc":{"lyric":"[00:01]roman"}}""").asJsonObject
        val data = LyricPayload.decode(json)
        val line = LyricParser.document(data).single()
        assertEquals("precise", line.text)
        assertEquals("译", line.translation)
        assertEquals("roman", line.romanized)
        assertEquals(1000L, line.words.single().start)
    }
}
