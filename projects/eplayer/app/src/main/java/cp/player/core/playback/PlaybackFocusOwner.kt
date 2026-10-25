package cp.player.core.playback

import androidx.media3.common.Player

/** A focus callback belongs to one player, including after an engine replacement. */
class PlaybackFocusOwner(private val player: Player) {
    private var active = true
    var resumeOnGain = false
        private set

    fun lost(transient: Boolean) {
        if (!active) return
        resumeOnGain = transient && (resumeOnGain || player.playWhenReady)
        player.pause()
    }

    fun gained() {
        if (!active || !resumeOnGain) return
        resumeOnGain = false
        player.play()
    }

    fun cancelResume() { resumeOnGain = false }
    fun close() { active = false; cancelResume() }
}
