package cp.player.core.audio

/** A decoder-independent imported profile; unsupported processing stays explicit. */
data class EqualizerBand(val frequency: Float, val gain: Float, val q: Float)
/** Shared bounds for imported and manually edited native peaking filters. */
fun EqualizerBand.isValid(): Boolean = frequency.isFinite() && frequency in 10f..48000f &&
    gain.isFinite() && gain in -24f..24f && q.isFinite() && q in .05f..30f
data class EqualizerProfile(val bands: List<EqualizerBand>, val preamp: Float?, val warnings: List<String>)

object EqualizerProfileParser {
    const val MAX_CHARACTERS = 1024 * 1024
    const val MAX_BANDS = 30 // Packaged native EqParams::MAX_BANDS; reject instead of silently truncating.
    private val parametric = Regex("Filter\\s+\\d+:\\s+(ON|OFF)\\s+(\\S+)\\s+Fc\\s+(\\S+)\\s+Hz\\s+Gain\\s+(\\S+)\\s+dB\\s+Q\\s+(\\S+)", RegexOption.IGNORE_CASE)
    fun parse(text: String): EqualizerProfile {
        require(text.length <= MAX_CHARACTERS) { "均衡器文件过大" }
        val bands = mutableListOf<EqualizerBand>()
        val warnings = linkedSetOf<String>()
        var preamp: Float? = null
        fun add(frequency: String, gain: String, q: String) {
            val f = frequency.toFloatOrNull()
            val g = gain.toFloatOrNull()
            val width = q.toFloatOrNull()
            require(f != null && g != null && width != null && EqualizerBand(f, g, width).isValid()) { "频率、增益或 Q 值无效" }
            require(bands.size < MAX_BANDS) { "均衡器频段过多" }
            bands += EqualizerBand(f, g, width)
        }
        for (raw in text.lineSequence()) {
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty() || line.startsWith('#')) continue
            when {
                line.startsWith("Preamp:", true) -> {
                    val value = line.substringAfter(':').trim().substringBefore(' ').toFloatOrNull()
                    require(value != null && value.isFinite() && value in -60f..24f) { "前置增益无效" }
                    preamp = value
                }
                line.startsWith("GraphicEQ:", true) -> {
                    for (pair in line.substringAfter(':').split(';')) {
                        if (pair.isBlank()) continue
                        val parts = pair.trim().split(Regex("\\s+"))
                        require(parts.size == 2) { "GraphicEQ 频段格式无效" }
                        add(parts[0], parts[1], "1.41")
                    }
                    warnings += "GraphicEQ 使用近似 Q=1.41，不能保证精确复现原曲线"
                }
                line.startsWith("Filter", true) -> {
                    if (Regex("\\bOFF\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)) continue
                    val match = parametric.matchEntire(line)
                    if (match == null || !match.groupValues[2].equals("PK", true)) {
                        warnings += "文件含尚未支持的滤波器，未导入这些频段"
                        continue
                    }
                    add(match.groupValues[3], match.groupValues[4], match.groupValues[5])
                }
            }
        }
        require(bands.isNotEmpty()) { "文件没有可用的均衡器频段" }
        return EqualizerProfile(bands.toList(), preamp, warnings.toList())
    }
}
