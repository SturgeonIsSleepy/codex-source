package cp.player.core.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * Temporary session view while the previous output releases its focus and resources.
 * The prepared delegate has not started; keep the user's request visible as buffering.
 * Replace this view with the delegate after activation. It owns no queue or output.
 */
internal class PlaybackHandoverPlayer(player: Player, private val requested: Boolean) : ForwardingPlayer(player) {
    override fun getPlayWhenReady(): Boolean = requested
    override fun getPlaybackState(): Int =
        if (requested && mediaItemCount > 0 && playerError == null) Player.STATE_BUFFERING
        else super.getPlaybackState()
    override fun isPlaying(): Boolean = false
}
