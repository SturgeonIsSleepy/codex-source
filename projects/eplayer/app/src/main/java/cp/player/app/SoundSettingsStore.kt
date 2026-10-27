package cp.player.app

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import cp.player.core.audio.EqualizerProfile
import cp.player.core.audio.EqualizerBand
import cp.player.core.audio.isValid
import cp.player.core.audio.AudioEffectCapabilities
import cp.player.util.AutoEqParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import cp.player.engine.ExoAudioFxManager
import cp.player.util.DspPreferences as Prefs
import cp.player.util.PeqBand
import cp.player.util.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class SoundBand(val frequency: Float, val gain: Float, val q: Float = 1f)
internal data class SoundSettingsState(
    val engine: Int, val equalizer: Boolean, val bands: List<SoundBand>, val minGain: Float, val maxGain: Float,
    val space: Boolean, val size: Float, val mix: Float, val width: Float,
    val virtualizer: Boolean, val virtualizerStrength: Float, val bass: Boolean, val bassStrength: Float,
    val loudness: Boolean, val loudnessGain: Float, val capabilities: AudioEffectCapabilities
)

/** Application-layer preference adapter. UI sends edits; audio engines own effect application. */
internal class SoundSettingsStore(context: Context, private val scope: CoroutineScope) : AutoCloseable {
    private val app = context.applicationContext
    private val _state = MutableStateFlow(read())
    val state = _state.asStateFlow()
    private val _profile = MutableStateFlow<EqualizerProfile?>(null)
    val profile = _profile.asStateFlow()
    private val _importIssue = MutableStateFlow<String?>(null)
    val importIssue = _importIssue.asStateFlow()
    private var importJob: Job? = null
    fun dismissImport() { importJob?.cancel(); _profile.value = null; _importIssue.value = null }
    fun previewProfile(uri: Uri) {
        dismissImport()
        importJob = scope.launch {
            try { _profile.value = withContext(Dispatchers.IO) { AutoEqParser.readProfile(app, uri) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { _importIssue.value = error.message ?: "无法导入均衡器文件" }
        }
    }
    fun applyProfileBands() {
        val imported = _profile.value ?: return
        if (state.value.engine != 1) { _importIssue.value = "请切换到 Rust 音频后导入"; _profile.value = null; return }
        Prefs.setPeqBands(app, imported.bands.map { PeqBand(it.frequency, it.gain, it.q) })
        Prefs.setEqEnabled(app, true)
        dismissImport()
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    init {
        Prefs.addListener(app, listener)
        UserPreferences.getPrefs(app).registerOnSharedPreferenceChangeListener(listener)
        scope.launch { ExoAudioFxManager.capabilities.collect { refresh() } }
    }
    private fun refresh() { _state.value = read() }
    private fun read(): SoundSettingsState {
        val native = UserPreferences.getAudioEngine(app) == 1
        val caps = ExoAudioFxManager.capabilities.value
        val nativeBands = Prefs.getPeqBands(app).ifEmpty {
            listOf(31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f).map { PeqBand(it, 0f, 1f) }
        }
        val gains = Prefs.getExoEqGains(app)
        return SoundSettingsState(if (native) 1 else 0,
            if (native) Prefs.getEqEnabled(app) else Prefs.getExoEqEnabled(app),
            if (native) nativeBands.map { SoundBand(it.freq, it.gain, it.q) }
            else caps.frequencies.mapIndexed { index, freq -> SoundBand(freq.toFloat(),
                if (Prefs.getExoPreset(app) >= 0) ExoAudioFxManager.getBandLevel(index.toShort()) / 100f
                else (gains[index.toShort()] ?: 0) / 100f) },
            if (native) -24f else caps.minGain, if (native) 24f else caps.maxGain,
            Prefs.getFxEnabled(app), Prefs.getFxSize(app), Prefs.getFxMix(app), Prefs.getFxWidth(app),
            Prefs.getExoVirtualizerEnabled(app), Prefs.getExoVirtualizerStrength(app) / 1000f,
            Prefs.getExoBassBoostEnabled(app), Prefs.getExoBassBoostStrength(app) / 1000f,
            Prefs.getExoLoudnessEnabled(app), Prefs.getExoLoudnessGain(app) / 100f, caps)
    }
    fun equalizer(enabled: Boolean) {
        if (state.value.engine == 1) {
            if (Prefs.getPeqBands(app).isEmpty()) saveBands(state.value.bands)
            Prefs.setEqEnabled(app, enabled)
        } else Prefs.setExoEqEnabled(app, enabled)
    }
    private fun saveBands(bands: List<SoundBand>) {
        if (state.value.engine == 1) Prefs.setPeqBands(app, bands.map { PeqBand(it.frequency, it.gain, it.q) })
        else {
            Prefs.setExoEqGains(app, bands.mapIndexed { index, band -> index.toShort() to (band.gain * 100).toInt().toShort() }.toMap())
            Prefs.setExoPreset(app, -1)
        }
    }
    fun band(index: Int, gain: Float) {
        val current = state.value
        if (index !in current.bands.indices) return
        saveBands(current.bands.mapIndexed { i, band -> if (i == index) band.copy(gain = gain.coerceIn(current.minGain, current.maxGain)) else band })
    }
    fun flat() = saveBands(state.value.bands.map { it.copy(gain = 0f) })
    fun editBand(index: Int, band: EqualizerBand) {
        val current = state.value
        if (current.engine != 1 || index !in current.bands.indices || !band.isValid()) return
        saveBands(current.bands.mapIndexed { i, old ->
            if (i == index) SoundBand(band.frequency, band.gain, band.q) else old
        })
    }
    fun toggle(effect: String, enabled: Boolean) {
        when (effect) {
            "space" -> Prefs.setFxEnabled(app, enabled)
            "virtualizer" -> Prefs.setExoVirtualizerEnabled(app, enabled)
            "bass" -> Prefs.setExoBassBoostEnabled(app, enabled)
            "loudness" -> Prefs.setExoLoudnessEnabled(app, enabled)
        }
    }
    fun amount(effect: String, amount: Float) {
        when (effect) {
            "size" -> Prefs.setFxSize(app, amount.coerceIn(0f, 1f))
            "mix" -> Prefs.setFxMix(app, amount.coerceIn(0f, 1f))
            "width" -> Prefs.setFxWidth(app, amount.coerceIn(0f, 2f))
            "virtualizer" -> Prefs.setExoVirtualizerStrength(app, (amount.coerceIn(0f, 1f) * 1000).toInt().toShort())
            "bass" -> Prefs.setExoBassBoostStrength(app, (amount.coerceIn(0f, 1f) * 1000).toInt().toShort())
            "loudness" -> Prefs.setExoLoudnessGain(app, (amount.coerceIn(0f, 12f) * 100).toInt())
        }
    }
    override fun close() {
        importJob?.cancel()
        Prefs.removeListener(app, listener)
        UserPreferences.getPrefs(app).unregisterOnSharedPreferenceChangeListener(listener)
    }
}
