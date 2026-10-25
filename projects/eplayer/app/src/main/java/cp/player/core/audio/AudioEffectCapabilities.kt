package cp.player.core.audio

/** Values reported by the active output session; no Android effect objects cross this boundary. */
data class AudioEffectCapabilities(
    val frequencies: List<Int> = emptyList(),
    val minGain: Float = 0f,
    val maxGain: Float = 0f,
    val virtualizer: Boolean = false,
    val bass: Boolean = false,
    val loudness: Boolean = false
)
