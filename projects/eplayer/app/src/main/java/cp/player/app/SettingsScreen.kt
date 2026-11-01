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
    TravelDialog(onDismissRequest = back,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        destination = {
            if (showSound) Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    Text("播放设置", Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall)
                    PlaybackSettings(model, sound.engine) {}
                }
            }
        }) {
        Surface(Modifier.fillMaxSize().testTag("settings-panel")) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (showSound) "均衡器与音效" else "播放设置", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
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
        TravelAlertDialog(onDismissRequest = store::dismissImport, modifier = Modifier.testTag("eq-import-preview"), title = { Text("导入均衡器") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("将替换当前 ${sound.bands.size} 段，应用文件中的 ${imported.bands.size} 段滤波器并启用均衡器。")
                imported.preamp?.takeIf { it != 0f }?.let { Text("文件的前置增益 $it dB 尚未支持，不会应用。") }
                imported.warnings.forEach { Text(it) }
            }
        }, confirmButton = { TextButton(store::applyProfileBands) { Text("仅应用频段") } },
            dismissButton = { TextButton(store::dismissImport) { Text("取消") } })
    }
    importIssue?.let { issue ->
        TravelAlertDialog(onDismissRequest = store::dismissImport, title = { Text("导入未完成") }, text = { Text(issue) },
            confirmButton = { TextButton(store::dismissImport) { Text("知道了") } })
    }

}

@Composable
private fun PlaybackSettings(model: EPlayerViewModel, engine: Int, openSound: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { openSound() }, modifier = Modifier.fillMaxWidth().testTag("open-sound-settings")) { Text("均衡器与音效") }
        SleepTimerSetting(model)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
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
        Text("定时停止", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Box {
            TextButton(onClick = { menu = true }) { Text(if (remainingMinutes > 0) "$remainingMinutes 分钟" else "关闭") }
            TravelMenu(menu, { menu = false }) {
                for (minutes in listOf(15, 30, 45, 60, 90, 120)) {
                    DropdownMenuItem(text = { Text("$minutes 分钟") }, onClick = { setMinutes(minutes); menu = false })
                }
                if (remainingMinutes > 0) DropdownMenuItem(text = { Text("关闭定时") }, onClick = { setMinutes(0); menu = false })
            }
        }
    }
}

@Composable
internal fun VisualSettings(model: EPlayerViewModel, close: () -> Unit) {
    val animate by model.animate.collectAsState()
    var themeOpen by rememberSaveable { mutableStateOf(false) }
    val appearance by model.appearance.collectAsState()
    val background by model.playerBackground.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::importPlayerBackground) }
    val stored by model.coverBlur.collectAsState()
    var radius by remember(stored) { mutableFloatStateOf(stored) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var lastTick by remember { mutableIntStateOf(-1) }
    var lastTickAt by remember { mutableLongStateOf(0L) }
    val playback by model.playback.collectAsState()
    val preview = if (background != null) (playback.song ?: cp.player.model.Song("background", "", "", album = "")).copy(albumArtUrl = background) else playback.song
    TravelDialog(close) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("画面设置", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = close) { Text("完成") }
                }
                SettingsCard {
                    SettingsRow("显示模式", when (appearance) { "light" -> "浅色"; "system" -> "跟随系统"; else -> "深色" }) { themeOpen = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                    SettingsRow("播放器背景", if (background == null) "未设置" else "已设置") { picker.launch(arrayOf("image/*")) }
                    if (background != null) TextButton(onClick = model::clearPlayerBackground) { Text("移除背景") }
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("角色动画", Modifier.weight(1f)); Switch(animate, model::setAnimate)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SoftStageCover(preview, radius, Modifier.size(68.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("封面模糊", Modifier.weight(1f))
                            Text(if (radius < .01f) "关闭" else "${(radius / 24f * 100).toInt()}%")
                        }
                        Slider(radius, {
                            radius = it
                            val tick = (it / 24f * 25).toInt()
                            val now = SystemClock.uptimeMillis()
                            if (tick != lastTick && now - lastTickAt >= 55) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                lastTick = tick; lastTickAt = now
                            }
                        }, valueRange = 0f..24f, steps = 0,
                            onValueChangeFinished = { model.setCoverBlur(radius) }, modifier = Modifier.testTag("cover-blur"))
                    }
                }
            }
        }
    }
    if (themeOpen) ThemeSettings(model) { themeOpen = false }
}
