package cp.player.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import cp.player.util.UserPreferences
import kotlinx.coroutines.delay

@Composable
internal fun SourceLogin(source: String) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val accountRevision by SourceStore.accountRevision.collectAsState()
    var open by rememberSaveable(source) { mutableStateOf(false) }
    var key by rememberSaveable(source) { mutableStateOf<String?>(null) }
    var url by rememberSaveable(source) { mutableStateOf<String?>(null) }
    var image by remember(source) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var status by rememberSaveable(source) { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var logged by remember(source) { mutableStateOf(!UserPreferences.getPrefs(context).getString("cookie_$source", null).isNullOrBlank()) }
    LaunchedEffect(source, accountRevision) { logged = !UserPreferences.getPrefs(context).getString("cookie_$source", null).isNullOrBlank() }
    fun close() { open = false; key = null; url = null; image = null }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(if (logged) "切换账号" else "登录") }
    LaunchedEffect(open, source, refresh) {
        if (!open) return@LaunchedEffect
        status = "加载中"
        try {
            if (key == null || url == null) {
                key = QrLogin.key(SourceStore.call(context, source, "login/qr/key"))
                url = QrLogin.url(SourceStore.call(context, source, "login/qr/create", mapOf("key" to key!!)))
            }
            image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { QrLogin.bitmap(url!!) }
            status = "等待扫码"
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (status == "已登录" || status == "二维码已过期") return@repeatOnLifecycle
                while (open) {
                    delay(2000)
                    val response = SourceStore.call(context, source, "login/qr/check", mapOf("key" to key!!))
                    when (response.get("code")?.asInt) {
                        800 -> { status = "二维码已过期"; image = null; break }
                        801 -> status = "等待扫码"
                        802 -> status = "等待确认"
                        803 -> {
                            val cookie = response.get("cookie")?.takeIf { it.isJsonPrimitive }?.asString
                            check(!cookie.isNullOrBlank()) { "登录未返回会话，请刷新重试" }
                            UserPreferences.getPrefs(context).edit().putString("cookie_$source", cookie).apply()
                            SourceStore.accountChanged(source)
                            logged = true; status = "已登录"; image = null; break
                        }
                        else -> error(response.get("msg")?.takeIf { it.isJsonPrimitive }?.asString ?: "登录检查失败，请刷新重试")
                    }
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { status = error.message ?: "登录失败，请重试" }
    }
    if (open) AlertDialog(onDismissRequest = ::close, title = { Text("扫码登录") }, text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            image?.let { androidx.compose.foundation.Image(it.asImageBitmap(), "登录二维码",
                Modifier.fillMaxWidth().aspectRatio(1f), filterQuality = androidx.compose.ui.graphics.FilterQuality.None) }
            Text(status)
        }
    }, confirmButton = { TextButton(onClick = ::close) { Text(if (status == "已登录") "完成" else "关闭") } },
        dismissButton = { if (status != "已登录") TextButton(onClick = { key = null; url = null; image = null; refresh++ }) { Text("刷新") } })
}
