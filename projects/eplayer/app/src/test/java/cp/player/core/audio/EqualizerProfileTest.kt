package cp.player.core.audio

import org.junit.Assert.*
import org.junit.Test

class EqualizerProfileTest {
    @Test fun manualFilterBoundsRejectNonFiniteValuesInEveryField() {
        assertTrue(EqualizerBand(10f, -24f, .05f).isValid())
        assertTrue(EqualizerBand(48000f, 24f, 30f).isValid())
        val good = EqualizerBand(440f, 0f, 1f)
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertFalse(good.copy(frequency = value).isValid())
            assertFalse(good.copy(gain = value).isValid())
            assertFalse(good.copy(q = value).isValid())
        }
        for (band in listOf(good.copy(frequency = 9f), good.copy(frequency = 48001f),
            good.copy(gain = -24.1f), good.copy(gain = 24.1f), good.copy(q = .04f), good.copy(q = 30.1f)))
            assertFalse(band.isValid())
    }
    @Test fun preservesActiveCustomBandsAndPreampButNeverEnablesOffFilters() {
        val profile = EqualizerProfileParser.parse("""
            Preamp: -4.2 dB
            Filter 1: ON PK Fc 440 Hz Gain 3.5 dB Q 0.8
            Filter 2: OFF PK Fc 2000 Hz Gain 9 dB Q 2
        """.trimIndent())
        assertEquals(listOf(EqualizerBand(440f, 3.5f, .8f)), profile.bands)
        assertEquals(-4.2f, profile.preamp!!, .001f)
    }
    @Test fun warnsAboutUnsupportedFiltersRatherThanPretendingTheyApplied() {
        val profile = EqualizerProfileParser.parse("Filter 1: ON LS Fc 100 Hz Gain 3 dB Q 1\nFilter 2: ON PK Fc 500 Hz Gain -2 dB Q 1")
        assertEquals(1, profile.bands.size)
        assertEquals(1, profile.warnings.size)
    }
    @Test fun graphicApproximationIsExplicit() {
        val profile = EqualizerProfileParser.parse("GraphicEQ: 31 -2; 1000 3;")
        assertEquals(2, profile.bands.size)
        assertEquals(1.41f, profile.bands[0].q, .001f)
        assertEquals(1, profile.warnings.size)
    }
    @Test fun rejectsNonFiniteAndUnsafeDspInputsWithoutPartiallyApplying() {
        for (value in listOf("NaN", "Infinity", "-1", "0", "999999")) {
            assertThrows(IllegalArgumentException::class.java) { EqualizerProfileParser.parse("GraphicEQ: $value 2") }
        }
        assertThrows(IllegalArgumentException::class.java) { EqualizerProfileParser.parse("GraphicEQ: 440 NaN") }
        assertThrows(IllegalArgumentException::class.java) { EqualizerProfileParser.parse("Filter 1: ON PK Fc 440 Hz Gain 2 dB Q 0") }
    }
    @Test fun rejectsOversizedAndUnboundedProfiles() {
        assertThrows(IllegalArgumentException::class.java) { EqualizerProfileParser.parse("#".repeat(EqualizerProfileParser.MAX_CHARACTERS + 1)) }
        assertThrows(IllegalArgumentException::class.java) { EqualizerProfileParser.parse("GraphicEQ: " + List(EqualizerProfileParser.MAX_BANDS + 1) { "100 1" }.joinToString(";")) }
    }
    @Test fun rejectsEmptyUnsupportedOrDisabledOnlyFiles() {
        for (text in listOf("", "Preamp: -1 dB", "Filter 1: OFF PK Fc 440 Hz Gain 3 dB Q 1", "Filter 1: ON LS Fc 440 Hz Gain 3 dB Q 1")) {
            assertThrows(IllegalArgumentException::class.java) { EqualizerProfileParser.parse(text) }
        }
    }
}
