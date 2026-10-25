package cp.player.app

import android.os.SystemClock
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cp.player.util.UserPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun Settings(model: EPlayerViewModel, close: () -> Unit) {
    val context = LocalContext.current
    val animate by model.animate.collectAsState()
    var engine by remember { mutableIntStateOf(UserPreferences.getAudioEngine(context)) }
    AlertDialog(onDismissRequest = close, title = { Text("设置") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SleepTimerSetting(model)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("角色动画", Modifier.weight(1f)); Switch(animate, model::setAnimate) }
            val quality by model.quality.collectAsState()
            var qualityMenu by remember { mutableStateOf(false) }
            val qualities = listOf("标准" to "standard", "极高" to "exhigh", "无损" to "lossless", "Hi-Res" to "hires")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("音质", Modifier.weight(1f))
                Box {
                    TextButton(onClick = { qualityMenu = true }) { Text(qualities.firstOrNull { it.second == quality }?.first ?: "标准") }
                    DropdownMenu(qualityMenu, { qualityMenu = false }) { qualities.forEach { choice ->
                        DropdownMenuItem(text = { Text(choice.first) }, onClick = { model.setQuality(choice.second); qualityMenu = false })
                    } }
                }
            }
            listOf("系统音频", "Rust 音频").forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().clickable { engine = index; model.setEngine(index) }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(engine == index, { engine = index; model.setEngine(index) }); Text(label)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("完成") } })
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
