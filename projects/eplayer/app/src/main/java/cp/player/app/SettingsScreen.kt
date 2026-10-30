package cp.player.app

import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun Settings(model: EPlayerViewModel, close: () -> Unit) {
    val sound by model.soundSettings.state.collectAsState()
    val store = model.soundSettings
    val profile by store.profile.collectAsState()
    val importIssue by store.importIssue.collectAsState()
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(store::previewProfile) }
    var showSound by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(store) { onDispose { store.dismissImport() } }
    val back: () -> Unit = {
        if (showSound) { store.dismissImport(); showSound = false } else close()
    }
    Dialog(onDismissRequest = back,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().testTag("settings-panel")) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (showSound) "声音" else "设置", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = back) { Text(if (showSound) "返回设置" else "完成") }
                }
                HorizontalDivider()
                if (showSound) {
                    SoundSettingsScreen(sound, store::equalizer, store::band, store::flat, store::toggle,
                        store::amount, editBand = store::editBand) { importFile.launch(arrayOf("text/*")) }
                } else PlaybackSettings(model, sound.engine) { showSound = true }
            }
        }
    }
    profile?.let { imported ->
        AlertDialog(onDismissRequest = store::dismissImport, modifier = Modifier.testTag("eq-import-preview"), title = { Text("导入均衡器") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("将替换当前 ${sound.bands.size} 段，应用文件中的 ${imported.bands.size} 段滤波器并启用均衡器。")
                imported.preamp?.takeIf { it != 0f }?.let { Text("文件的前置增益 $it dB 尚未支持，不会应用。") }
                imported.warnings.forEach { Text(it) }
            }
        }, confirmButton = { TextButton(store::applyProfileBands) { Text("仅应用频段") } },
            dismissButton = { TextButton(store::dismissImport) { Text("取消") } })
    }
    importIssue?.let { issue ->
        AlertDialog(onDismissRequest = store::dismissImport, title = { Text("导入未完成") }, text = { Text(issue) },
            confirmButton = { TextButton(store::dismissImport) { Text("知道了") } })
    }

}

@Composable
private fun PlaybackSettings(model: EPlayerViewModel, engine: Int, openSound: () -> Unit) {
    val animate by model.animate.collectAsState()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("播放与休息", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { openSound() }, modifier = Modifier.fillMaxWidth().testTag("open-sound-settings")) { Text("声音手记 · 均衡器与音效") }
        SleepTimerSetting(model)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Text("角色动画", Modifier.weight(1f)); Switch(animate, model::setAnimate) }
        QualitySetting(model)
        listOf("系统音频", "Rust 音频").forEachIndexed { index, label ->
            Row(Modifier.fillMaxWidth().testTag("audio-engine-$index").clickable { model.setEngine(index) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(engine == index, { model.setEngine(index) }); Text(label)
            }
        }
    }
}

@Composable
private fun SleepTimerSetting(model: EPlayerViewModel) {
    val deadline by model.sleepDeadline.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(deadline, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive && deadline > SystemClock.elapsedRealtime()) {
                now = SystemClock.elapsedRealtime()
                delay(1000)
            }
            now = SystemClock.elapsedRealtime()
        }
    }
    SleepTimerControl(((deadline - now).coerceAtLeast(0) + 59999) / 60000, model::setSleepTimer)
}

@Composable
internal fun SleepTimerControl(remainingMinutes: Long, setMinutes: (Int) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("定时停止", style = MaterialTheme.typography.titleMedium)
            Text(if (remainingMinutes > 0) "约 $remainingMinutes 分钟后暂停" else "让旅途在合适的时候歇一歇",
                style = MaterialTheme.typography.bodySmall)
        }
        Box {
            TextButton(onClick = { menu = true }) { Text(if (remainingMinutes > 0) "调整" else "设置") }
            DropdownMenu(menu, { menu = false }) {
                for (minutes in listOf(15, 30, 45, 60, 90, 120)) {
                    DropdownMenuItem(text = { Text("$minutes 分钟") }, onClick = { setMinutes(minutes); menu = false })
                }
                if (remainingMinutes > 0) DropdownMenuItem(text = { Text("关闭定时") }, onClick = { setMinutes(0); menu = false })
            }
        }
    }
}
