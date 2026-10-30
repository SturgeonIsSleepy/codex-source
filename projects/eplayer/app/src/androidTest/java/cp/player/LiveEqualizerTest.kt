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
import cp.player.core.library.RecentStore
import cp.player.engine.MusicPulse
import cp.player.engine.RustEngine
import cp.player.model.Song
import cp.player.service.MusicService
import cp.player.testing.SyntheticAudio
import cp.player.util.DspPreferences
import cp.player.util.UserPreferences
import cp.player.app.SoundSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    private fun await(describe: () -> String = { "Audio state did not settle" }, action: () -> Boolean) {
        val limit = SystemClock.elapsedRealtime() + 15000
        while (SystemClock.elapsedRealtime() < limit) {
            if (action()) return
            SystemClock.sleep(100)
        }
        val settled = action()
        val message = describe()
        if (!settled) captureMediaFailure()
        assertTrue(message, settled)
    }
    private fun captureMediaFailure() {
        if (InstrumentationRegistry.getArguments().getString("eplayer.mediaDiagnostics") != "1" ||
            android.os.Build.HARDWARE !in setOf("ranchu", "goldfish")) return
        // Media buffers are small: take the failure-time snapshot before fixture cleanup evicts it.
        runCatching {
            val directory = File(context.getExternalFilesDir(null), "system-media-check").apply { mkdirs() }
            val destination = File(directory, "equalizer-${UUID.randomUUID()}.txt")
            val descriptor = instrumentation.uiAutomation.executeShellCommand(
                "dumpsys activity service com.android.systemui/.SystemUIService " +
                    "MediaCarouselCtlrLog MediaTimeout MediaLog MediaDataManager")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                destination.outputStream().use { input.copyTo(it) }
            }
        }.onFailure { android.util.Log.w("EPlayerTest", "Media failure snapshot unavailable", it) }
    }

    private fun level(): Float {
        return runBlocking { withContext(RustEngine.engineDispatcher) {
            RustEngine.getProgress()
            MusicPulse.sample()
        } }
    }
    @Test fun savedEqualizerChangesApplyWhilePlayingAndDisableCleanly() {
        // The failure is intermittent after replacement, not a steady-state gain error.
        // Recreate complete owners three times rather than retrying a failed assertion.
        repeat(3) { round ->
            android.util.Log.i("EPlayerTest", "Live EQ lifecycle round ${round + 1}/3")
            verifySavedEqualizerLifecycle()
        }
    }

    private fun verifySavedEqualizerLifecycle() {
        val dsp = context.getSharedPreferences("cp_player_dsp_prefs", 0)
        assumeTrue("Use a fresh installation; preserve existing sound settings", dsp.all.isEmpty())
        val queue = context.getSharedPreferences("eplayer_queue", 0).getString("items", null)
        assumeTrue("Preserve the user's playback queue", queue == null || queue == "[]")
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        instrumentation.waitForIdleSync()
        val savedEngine = UserPreferences.getAudioEngine(context)
        val file = File(context.cacheDir, "equalizer-fixture.wav").apply { writeBytes(SyntheticAudio.wav(12)) }
        val id = "equalizer-${UUID.randomUUID()}"
        val changedRateId = "$id-48k"
        val changedRateFile = File(context.cacheDir, "$changedRateId.wav").apply { writeBytes(SyntheticAudio.wav(12, 48000)) }
        val profileFile = File(context.cacheDir, "$id-autoeq.txt").apply {
            writeText("Filter 1: ON PK Fc 440 Hz Gain 6 dB Q 1\nFilter 2: OFF PK Fc 440 Hz Gain -12 dB Q 1")
        }
        val effectsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val settings = SoundSettingsStore(context, effectsScope)
        var player: MediaController? = null
        var volume = 1f
        try {
            player = MediaController.Builder(context, SessionToken(context, ComponentName(context, MusicService::class.java)))
                .setConnectionHints(Bundle().apply { putString("eplayer.qa.owner", "equalizer") })
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
            repeat(3) {
                for (engine in listOf(0, 1)) {
                    lateinit var transition: ListenableFuture<SessionResult>
                    main { transition = current.sendCustomCommand(SessionCommand("SET_ENGINE", Bundle.EMPTY), Bundle().apply { putInt("engine", engine) }) }
                    assertEquals(SessionResult.RESULT_SUCCESS, transition.get(10, TimeUnit.SECONDS).resultCode)
                }
            }
            val samples = ArrayDeque<Float>()
            var latest = Float.NaN
            val diagnostic = {
                var state = ""
                main { state = "playing=${current.isPlaying} state=${current.playbackState} position=${current.currentPosition} duration=${current.duration} error=${current.playerError}" }
                val native = runBlocking { withContext(RustEngine.engineDispatcher) { RustEngine.getState() } }
                "$state native=$native level=$latest samples=$samples eq=${DspPreferences.getEqEnabled(context)} bands=${DspPreferences.getPeqBands(context)}"
            }
            await(describe = diagnostic) {
                var active = false
                main { assertNull(current.playerError); active = current.isPlaying && current.currentPosition >= 1000 && current.duration in 11900..12100 }
                latest = level()
                if (!active || !latest.isFinite() || latest <= .01f) samples.clear()
                else { samples.addLast(latest); if (samples.size > 5) samples.removeFirst() }
                samples.size == 5 && samples.max() - samples.min() < .005f
            }
            val baseline = samples.average().toFloat()
            android.util.Log.i("EPlayerTest", "Stable native EQ baseline=$baseline after three fast engine recreations")
            settings.previewProfile(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", profileFile))
            await { settings.profile.value != null }
            assertFalse("Preview must not enable EQ", DspPreferences.getEqEnabled(context))
            assertTrue("Preview must not overwrite bands", DspPreferences.getPeqBands(context).isEmpty())
            assertEquals("OFF filters must stay off", 1, settings.profile.value!!.bands.size)
            main { settings.applyProfileBands() }
            var boosted = baseline
            await(describe = { "EQ boost baseline=$baseline boosted=$boosted ${diagnostic()}" }) {
                boosted = level(); latest = boosted
                boosted.isFinite() && boosted > baseline + .025f
            }
            DspPreferences.setEqEnabled(context, false)
            await(describe = { "EQ disable baseline=$baseline boosted=$boosted ${diagnostic()}" }) {
                latest = level(); latest.isFinite() && latest < boosted - .02f && latest > .01f
            }
            main { assertTrue("Changing EQ must not restart or pause playback", current.isPlaying) }
            android.util.Log.i("EPlayerTest", "Live native EQ: baseline=$baseline boosted=$boosted disabled=${level()}")
            // Confirm the enabled filter has reached the old output before changing format.
            main { DspPreferences.setEqEnabled(context, true) }
            await(describe = { "Re-enable before rate change ${diagnostic()}" }) {
                latest = level(); latest.isFinite() && latest > baseline + .025f
            }
            main {
                current.setMediaItem(Song(changedRateId, "48 kHz 声音测试", "Synthetic fixture",
                    album = "Test fixtures", durationMs = 12000).toMediaItem(
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", changedRateFile)))
                current.prepare(); current.play()
            }
            await(describe = { "Saved EQ lost after 44.1→48 kHz ${diagnostic()}" }) {
                var active = false
                main { active = current.isPlaying && current.currentPosition >= 1000 }
                latest = level(); active && latest.isFinite() && latest > baseline + .025f
            }
            android.util.Log.i("EPlayerTest", "Saved native EQ survived 44.1→48 kHz; level=$latest baseline=$baseline")
        } finally {
            settings.close(); effectsScope.cancel(); profileFile.delete()
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
            RecentStore.remove(context, changedRateId)
            changedRateFile.delete()
            file.delete()
        }
    }
}
