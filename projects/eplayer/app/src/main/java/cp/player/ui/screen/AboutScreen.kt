package cp.player.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cp.player.BuildConfig

/** Fork identity and upstream attribution; upstream APK updates do not belong to ePlayer. */
@Composable
fun AboutScreenInline() {
    val context = LocalContext.current
    SettingsSection("ePlayer") {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("旅途有声", style = MaterialTheme.typography.headlineMedium)
            Text("ePlayer ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text("白纸上的魔女音乐播放器。以 CPPlayer 为基础，保留曲库、搜索、歌词、下载与音频引擎。",
                style = MaterialTheme.typography.bodyLarge)
            Text("本版本为独立构建；CPPlayer 的安装包不能作为 ePlayer 更新。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    SettingsSection("开源与致谢") {
        ExpressiveClickItem(
            title = "CPPlayer · Aurora-Nasa-1", subtitle = "基于 MIT 协议的原始项目",
            shapes = ListItemDefaults.segmentedShapes(0, 1),
            icon = { Icon(Icons.Default.Code, null) },
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Aurora-Nasa-1/CPPlayer"))) }
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("霞鹜文楷 Medium · LXGW · SIL Open Font License 1.1")
            Text("Yuji Boku · Kinuta Font Factory · SIL Open Font License 1.1")
            Text("界面插画参考用户提供的《魔女之旅》同人图；新增天空装饰与图标由图像生成制作。",
                style = MaterialTheme.typography.bodySmall)
            Text("Copyright (c) 2026 Aurora-Nasa-1\nCPPlayer is licensed under the MIT License.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
