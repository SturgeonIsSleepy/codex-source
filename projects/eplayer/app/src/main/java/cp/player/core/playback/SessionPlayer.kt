package cp.player.core.playback

import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** One public player for the session lifetime; only the output delegate changes. */
internal class SessionPlayer(initial: Player, private val releaseOutput: (Player) -> Unit) :
    ForwardingSimpleBasePlayer(initial) {
    private var handover: SimpleBasePlayer.State? = null

    override fun getState(): SimpleBasePlayer.State = handover ?: super.getState()

    fun replaceWith(target: Player, requested: Boolean) {
        val previous = player
        handover = super.getState().buildUpon()
            .setContentPositionMs(previous.currentPosition)
            .setPlayWhenReady(requested, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (requested && previous.mediaItemCount > 0 && previous.playerError == null) Player.STATE_BUFFERING
                else previous.playbackState)
            .build()
        try {
            // Detach the old delegate before pausing/releasing its output and focus.
            setPlayer(target)
            previous.pause()
            releaseOutput(previous)
            target.playWhenReady = requested
        } finally {
            handover = null
            invalidateState()
        }
    }

    override fun handleRelease(): ListenableFuture<*> {
        releaseOutput(player)
        return Futures.immediateVoidFuture()
    }
}
