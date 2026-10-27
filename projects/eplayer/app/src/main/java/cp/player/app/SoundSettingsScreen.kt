package cp.player.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Locale
import cp.player.core.audio.EqualizerBand

/** Paper controls only. Preference persistence and decoder application live outside Compose. */
@Composable
internal fun SoundSettingsScreen(state: SoundSettingsState, equalizer: (Boolean) -> Unit,
    gain: (Int, Float) -> Unit, flat: () -> Unit, toggle: (String, Boolean) -> Unit,
    amount: (String, Float) -> Unit, editBand: ((Int, EqualizerBand) -> Unit)? = null,
    importProfile: (() -> Unit)? = null) {
    var editing by remember(state.engine, state.bands.size) { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().testTag("sound-settings").verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (state.engine == 1) "Rust 音频 · 声音手记" else "系统音频 · 声音手记", style = MaterialTheme.typography.headlineSmall)
        Text("从平直开始，慢慢找到喜欢的声音。", style = MaterialTheme.typography.bodyMedium)
        if (state.engine == 1 && importProfile != null) OutlinedButton(importProfile, Modifier.fillMaxWidth()) { Text("导入 AutoEQ 文本") }
        HorizontalDivider()
        EffectToggle("均衡器", state.equalizer, state.engine == 1 || state.bands.isNotEmpty(), equalizer)
        if (state.engine == 0 && state.bands.isEmpty()) {
            Text("播放一首歌后显示设备支持的频段。", style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("频段增益", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(flat) { Text("恢复平直") }
            }
            state.bands.forEachIndexed { index, band ->
                val frequency = if (band.frequency >= 1000) "${number(band.frequency / 1000)} kHz" else "${number(band.frequency)} Hz"
                SoundAmount(frequency, band.gain, state.minGain..state.maxGain,
                    state.equalizer && state.minGain < state.maxGain, "${number(band.gain)} dB", "eq-band-$index") { gain(index, it) }
                if (state.engine == 1 && editBand != null) {
                    TextButton(onClick = { editing = index }, modifier = Modifier.testTag("edit-eq-band-$index")) {
                        Text("编辑第 ${index + 1} 段 · Q ${number(band.q)}")
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (state.engine == 1) {
            EffectToggle("空间感", state.space, true) { toggle("space", it) }
            SoundAmount("空间大小", state.size, 0f..1f, state.space, percent(state.size), "fx-size") { amount("size", it) }
            SoundAmount("混响混合", state.mix, 0f..1f, state.space, percent(state.mix), "fx-mix") { amount("mix", it) }
            SoundAmount("声场宽度", state.width, 0f..2f, state.space, percent(state.width), "fx-width") { amount("width", it) }
        } else {
            val caps = state.capabilities
            EffectToggle("环绕感", state.virtualizer, caps.virtualizer) { toggle("virtualizer", it) }
            if (caps.virtualizer) SoundAmount("环绕强度", state.virtualizerStrength, 0f..1f, state.virtualizer, percent(state.virtualizerStrength), "exo-space") { amount("virtualizer", it) }
            EffectToggle("低音增强", state.bass, caps.bass) { toggle("bass", it) }
            if (caps.bass) SoundAmount("低音强度", state.bassStrength, 0f..1f, state.bass, percent(state.bassStrength), "exo-bass") { amount("bass", it) }
            EffectToggle("响度增强", state.loudness, caps.loudness) { toggle("loudness", it) }
            if (caps.loudness) SoundAmount("响度增益", state.loudnessGain, 0f..12f, state.loudness, "${number(state.loudnessGain)} dB", "exo-loudness") { amount("loudness", it) }
            Text("灰色选项表示当前音频会话未提供该能力。", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
    }
    editing?.let { index -> state.bands.getOrNull(index)?.let { band ->
        EqualizerBandDialog(index, EqualizerBand(band.frequency, band.gain, band.q),
            dismiss = { editing = null }, save = { editBand?.invoke(index, it); editing = null })
    } }
}

private fun number(value: Float) = String.format(Locale.ROOT, "%.1f", value)
private fun percent(value: Float) = "${(value * 100).toInt()}%"

@Composable
private fun EffectToggle(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Switch(checked, change, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundAmount(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean,
    formatted: String, tag: String, change: (Float) -> Unit) {
    // Keep drag local; persist one edit on release rather than dispatching JNI on every pointer move.
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(value, enabled) { if (!enabled) dragging = null }
    val shown = dragging ?: value
    val ink = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(if (dragging == null) formatted else if (range.endInclusive > 2f) "${number(shown)} dB" else percent(shown), style = MaterialTheme.typography.labelLarge)
        }
        Slider(shown.coerceIn(range.start, range.endInclusive), { dragging = it },
            onValueChangeFinished = { dragging?.let(change); dragging = null }, enabled = enabled,
            valueRange = if (range.start < range.endInclusive) range else 0f..1f,
            modifier = Modifier.testTag(tag).semantics { contentDescription = label },
            thumb = { Surface(Modifier.size(12.dp), shape = CircleShape, color = ink) {} },
            track = { Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                drawLine(ink, Offset(0f, center.y), Offset(size.width, center.y), 2.dp.toPx(), StrokeCap.Round)
            } })
    }
}
