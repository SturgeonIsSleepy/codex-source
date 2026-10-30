package cp.player.core.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricParserTest {
    @Test fun fractionalSecondsAndRepeatedTimestampsRemainSynchronized() {
        val lines = LyricParser.parse("[00:01.5][01:02.034]fixture-a\n[00:02.12]fixture-b")
        assertEquals(listOf(1500L, 2120L, 62034L), lines.map { it.time })
        assertEquals(listOf("fixture-a", "fixture-b", "fixture-a"), lines.map { it.text })
    }
    @Test fun offsetAppliesWithoutDisplayingMetadataTags() {
        val lines = LyricParser.parse("[ti:fixture]\n[offset:-250]\n[00:00.10]first\n[00:02]second")
        assertEquals(listOf(0L, 1750L), lines.map { it.time })
        assertEquals(listOf("first", "second"), lines.map { it.text })
    }
    @Test fun indexHandlesBeforeFirstLineBoundariesAndEnd() {
        val lines = LyricParser.parse("[00:01]a\n[00:02]b")
        assertEquals(-1, LyricParser.index(lines, 999))
        assertEquals(0, LyricParser.index(lines, 1000))
        assertEquals(1, LyricParser.index(lines, 999999))
        assertEquals(-1, LyricParser.index(emptyList(), 0))
    }
    @Test fun untimedContentIsStillReadable() {
        assertEquals(listOf("fixture-a", "fixture-b"), LyricParser.parse("fixture-a\n\nfixture-b").map { it.text })
    }
    @Test fun neteaseJsonCreditsDisplayTextInsteadOfJson() {
        val text = """{"t":0,"c":[{"tx":"作词: "},{"tx":"Ririko"}]}
{"t":1000,"c":[{"tx":"作曲: "},{"tx":"Ririko"}]}
[00:02.50]纯音乐，请欣赏"""
        val lines = LyricParser.parse(text)
        assertEquals(listOf("作词: Ririko", "作曲: Ririko", "纯音乐，请欣赏"), lines.map { it.text })
        assertEquals(listOf(0L, 1000L, 2500L), lines.map { it.time })
    }
    @Test fun emptyJsonCreditsDoNotCreateBlankLyricRows() {
        assertEquals(listOf(TimedLine(1000, "first")),
            LyricParser.parse("""{"t":0,"c":[]}
[00:01]first"""))
    }
}
