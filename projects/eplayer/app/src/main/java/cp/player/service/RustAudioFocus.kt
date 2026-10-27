package cp.player.service

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import androidx.media3.common.Player
import cp.player.core.playback.PlaybackFocusOwner

/** ExoPlayer owns its own focus. Rust gets a bounded request tied to its player. */
internal class RustAudioFocus(private val audio: AudioManager, private val player: Player, handler: Handler) {
    private val owner = PlaybackFocusOwner(player)
    private var active = true
    private var requested = false
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setOnAudioFocusChangeListener({ change ->
            if (active) when (change) {
                AudioManager.AUDIOFOCUS_GAIN -> owner.gained()
                AudioManager.AUDIOFOCUS_LOSS -> owner.lost(transient = false)
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> owner.lost(transient = true)
            }
        }, handler).build()
    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
            if (ready) acquire() else if (!owner.resumeOnGain) abandon()
        }
    }

    init { player.addListener(listener) }

    private fun acquire() {
        if (!active || requested) return
        requested = true
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            owner.cancelResume()
            player.pause()
            abandon()
        }
    }

    private fun abandon() {
        if (!requested) return
        requested = false
        audio.abandonAudioFocusRequest(request)
    }

    fun pause() {
        owner.cancelResume()
        player.pause()
        abandon()
    }

    fun close() {
        active = false
        owner.close()
        player.removeListener(listener)
        abandon()
    }
}
