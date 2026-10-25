package cp.player

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import cp.player.app.RecentStore
import cp.player.engine.MusicPulse
import cp.player.engine.RustEngine
import cp.player.model.Song
import cp.player.service.MusicService
import cp.player.testing.SyntheticAudio
import cp.player.util.DspPreferences
import cp.player.util.PeqBand
import cp.player.util.UserPreferences
import cp.player.util.toMediaItem
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Measures the rendered synthetic PCM through the packaged native DSP, without microphone capture. */
@RunWith(AndroidJUnit4::class)
class LiveEqualizerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun main(action: () -> Unit) {
        var error: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (failure: Throwable) { error = failure } }
        error?.let { throw it }
    }
    private fun await(action: () -> Boolean) {
        val limit = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < limit) {
            if (action()) return
            SystemClock.sleep(100)
        }
        assertTrue("Audio effect did not reach the expected rendered level", action())
    }
    private fun level(): Float {
        RustEngine.getProgress()
        return MusicPulse.sample()
    }
    @Test fun savedEqualizerChangesApplyWhilePlayingAndDisableCleanly() {
        val dsp = context.getSharedPreferences("cp_player_dsp_prefs", 0)
        assumeTrue("Use a fresh installation; preserve existing sound settings", dsp.all.isEmpty())
        val queue = context.getSharedPreferences("eplayer_queue", 0).getString("items", null)
        assumeTrue("Preserve the user's playback queue", queue == null || queue == "[]")
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        val savedEngine = UserPreferences.getAudioEngine(context)
        val file = File(context.cacheDir, "equalizer-fixture.wav").apply { writeBytes(SyntheticAudio.wav(12)) }
        val id = "equalizer-${UUID.randomUUID()}"
        var player: MediaController? = null
        var volume = 1f
        try {
            player = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java)))
                .buildAsync().get(30, TimeUnit.SECONDS)
            val current = player!!
            lateinit var switch: ListenableFuture<SessionResult>
            main {
                volume = current.volume
                switch = current.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", 1) })
            }
            assertEquals(SessionResult.RESULT_SUCCESS, switch.get(10, TimeUnit.SECONDS).resultCode)
            main {
                current.volume = .35f
                current.repeatMode = Player.REPEAT_MODE_ONE
                current.setMediaItem(Song(id, "声音测试", "Synthetic fixture", album = "Test fixtures", durationMs = 12000).toMediaItem(
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)))
                current.prepare(); current.play()
            }
            await { var active = false; main { assertNull(current.playerError); active = current.isPlaying }; active && level().isFinite() && level() > .01f }
            var baseline = 0f
            repeat(5) { baseline += level(); SystemClock.sleep(100) }
            baseline /= 5
            DspPreferences.setPeqBands(context, listOf(PeqBand(440f, 6f, 1f)))
            DspPreferences.setEqEnabled(context, true)
            var boosted = baseline
            await { boosted = level(); boosted.isFinite() && boosted > baseline + .025f }
            DspPreferences.setEqEnabled(context, false)
            await { level().let { it.isFinite() && it < boosted - .02f && it > .01f } }
            main { assertTrue("Changing EQ must not restart or pause playback", current.isPlaying) }
            android.util.Log.i("EPlayerTest", "Live native EQ: baseline=$baseline boosted=$boosted disabled=${level()}")
        } finally {
            dsp.edit().clear().commit() // Guard above requires an originally empty store.
            player?.let { current ->
                lateinit var restore: ListenableFuture<SessionResult>
                main {
                    current.stop(); current.clearMediaItems(); current.repeatMode = Player.REPEAT_MODE_OFF
                    current.volume = volume
                    restore = current.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", savedEngine) })
                }
                runCatching { restore.get(10, TimeUnit.SECONDS) }
                main { current.release() }
            }
            RecentStore.remove(context, id)
            file.delete()
        }
    }
}
