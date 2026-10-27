package cp.player.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cp.player.core.audio.EqualizerBand
import cp.player.core.audio.isValid

/** A local draft: no preference or audio-engine calls until the whole filter is confirmed. */
@Composable
internal fun EqualizerBandDialog(index: Int, initial: EqualizerBand, dismiss: () -> Unit,
    save: (EqualizerBand) -> Unit) {
    var frequency by rememberSaveable(index) { mutableStateOf(initial.frequency.toString()) }
    var gain by rememberSaveable(index) { mutableStateOf(initial.gain.toString()) }
    var q by rememberSaveable(index) { mutableStateOf(initial.q.toString()) }
    val band = frequency.trim().toFloatOrNull()?.let { f -> gain.trim().toFloatOrNull()?.let { g ->
        q.trim().toFloatOrNull()?.let { EqualizerBand(f, g, it) }
    } }
    val valid = band?.isValid() == true
    AlertDialog(onDismissRequest = dismiss, modifier = Modifier.testTag("eq-band-editor"),
        title = { Text("第 ${index + 1} 段 · 参数均衡") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Q 值越大，调整的频率范围越窄。保存后生效。")
                OutlinedTextField(frequency, { frequency = it }, label = { Text("频率 · Hz") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("eq-edit-frequency"))
                OutlinedTextField(gain, { gain = it }, label = { Text("增益 · dB") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth().testTag("eq-edit-gain"))
                OutlinedTextField(q, { q = it }, label = { Text("带宽 · Q") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("eq-edit-q"))
                if (!valid) Text("频率 10–48000 Hz，增益 −24–24 dB，Q 0.05–30。请填写有效数字。")
            }
        }, confirmButton = { TextButton(onClick = { if (valid) save(band!!) }, enabled = valid) { Text("保存频段") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
