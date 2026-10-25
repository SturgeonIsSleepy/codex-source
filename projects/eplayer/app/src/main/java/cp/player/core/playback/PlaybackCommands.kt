package cp.player.core.playback

import androidx.media3.common.Player

/** Toggle the user's request, including buffering and focus-suppressed playback. */
fun Player.togglePlayback() {
    when {
        playbackState == Player.STATE_ENDED -> { seekToDefaultPosition(); play() }
        playbackState == Player.STATE_IDLE -> { prepare(); play() }
        playWhenReady -> pause()
        else -> play()
    }
}
