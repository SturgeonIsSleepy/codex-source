package cp.player.app

import android.content.Context
import android.net.Uri
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cp.player.BuildConfig
import cp.player.provider.BackendProvider
import cp.player.provider.ModuleManager
import cp.player.provider.ProviderManager
import cp.player.util.JsonUtils
import cp.player.util.UserPreferences
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** All app-facing source operations use one serialized module store and explicit source IDs. */
object SourceStore {
    private val mutex = Mutex()
    private data class Resolved(val audio: cp.player.core.playback.AudioResource, val expiresAt: Long)
    private val resolved = android.util.LruCache<String, Resolved>(64)
    private val initialized = CompletableDeferred<Unit>()
    private val _issue = MutableStateFlow<String?>(null)
    val issue = _issue.asStateFlow()
    private val _accountChanges = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    val accountChanges = _accountChanges.asSharedFlow()
    val accountRevision = MutableStateFlow(0L)
    fun accountChanged(source: String) { clearResolved(source); accountRevision.value++; _accountChanges.tryEmit(source) }
    suspend fun logout(context: Context, source: String) {
        try { call(context, source, "logout") }
        finally { context.getSharedPreferences("cp_player_prefs", 0).edit().remove("cookie_$source").apply(); accountChanged(source) }
    }
    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        try {
            mutex.withLock {
                ModuleManager.init(context)
                if (BuildConfig.INTERNAL_BUILD) {
                    val prefs = context.getSharedPreferences("bundled_source", Context.MODE_PRIVATE)
                    if (!prefs.getBoolean("installed_1.1.0", false)) {
                        val temporary = File(context.cacheDir, "bundled-provider.zip")
                        try {
                            context.assets.open("bundled-provider.zip").use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
                            check(ModuleManager.importModule(context, temporary)) { ModuleManager.lastLoadError ?: "内置音源加载失败" }
                            prefs.edit().putBoolean("installed_1.1.0", true).apply()
                        } finally { temporary.delete() }
                    }
                    if (ProviderManager.currentProvider == null) ModuleManager.getAvailableProviders().firstOrNull()?.let {
                        ProviderManager.switchProvider(it, context)
                    }
                }
            }
        } catch (error: Exception) { _issue.value = error.message ?: "音源初始化失败" }
        finally { initialized.complete(Unit) }
    }
    suspend fun import(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        initialized.await()
        mutex.withLock {
            val temporary = File.createTempFile("source-", ".zip", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法读取模块" }
                    temporary.outputStream().use { input.copyTo(it) }
                }
                check(ModuleManager.importModule(context, temporary)) { ModuleManager.lastLoadError ?: "导入失败" }
                if (ProviderManager.currentProvider == null) ModuleManager.getAvailableProviders().firstOrNull()?.let {
                    check(ProviderManager.switchProvider(it, context)) { "音源未就绪" }
                }
                _issue.value = null
            } finally { temporary.delete() }
        }
    }
    suspend fun select(context: Context, provider: BackendProvider) = withContext(Dispatchers.IO) {
        mutex.withLock { check(ProviderManager.switchProvider(provider, context)) { "音源切换失败" } }
    }
    suspend fun remove(context: Context, provider: BackendProvider) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (ProviderManager.currentProvider?.id == provider.id) ProviderManager.switchProvider(null, context)
            check(ModuleManager.deleteModule(provider.id)) { "删除失败" }
        }
    }
    suspend fun call(context: Context, sourceId: String, method: String, parameters: Map<String, String> = emptyMap()): JsonObject = withContext(Dispatchers.IO) {
        initialized.await()
        val provider = ModuleManager.getProvider(sourceId) ?: error("音源已移除")
        val mapped = provider.apiMap?.get(method) ?: method
        check(mapped.isNotBlank() && !mapped.equals("unsupported", true)) { "此音源不支持该操作" }
        val cookie = UserPreferences.getPrefs(context).getString("cookie_$sourceId", null)
        val params = if (cookie.isNullOrBlank() || method in setOf("login/qr/key", "login/qr/create", "login/qr/check", "login", "login/cellphone", "captcha/sent")) parameters else parameters + ("cookie" to cookie)
        val raw = provider.callApi(mapped, params)
        check(raw.isNotBlank()) { "音源返回空响应" }
        JsonParser.parseString(raw).asJsonObject
    }
    suspend fun search(context: Context, sourceId: String, query: String): List<Track> {
        val response = call(context, sourceId, "cloudsearch", mapOf("keywords" to query, "type" to "1", "limit" to "50"))
        check(response.get("code")?.asInt == 200) { response.get("msg")?.asString ?: "搜索失败" }
        return response.getAsJsonObject("result")?.getAsJsonArray("songs")?.mapNotNull(JsonUtils::parseSong)
            ?.map { Track(it, source = sourceId) }.orEmpty()
    }
    suspend fun resolve(context: Context, sourceId: String, songId: String, quality: String = "standard"): String =
        resolveAudio(context, sourceId, songId, quality).url

    fun peekAudio(source: String, song: String, quality: String): cp.player.core.playback.AudioResource? =
        synchronized(resolved) { resolved.get("$source:$song:$quality")?.audio }

    suspend fun resolveAudio(context: Context, sourceId: String, songId: String, quality: String = "standard",
        refresh: Boolean = false): cp.player.core.playback.AudioResource {
        val key = "$sourceId:$songId:$quality"
        if (!refresh) synchronized(resolved) {
            resolved.get(key)?.takeIf { it.expiresAt > android.os.SystemClock.elapsedRealtime() }?.let { return it.audio }
        }
        var audio: cp.player.core.playback.AudioResource? = null
        repeat(2) { attempt ->
            if (audio == null) {
                try {
                    val response = call(context, sourceId, "song/url/v1", mapOf("id" to songId, "level" to quality,
                        "timestamp" to System.currentTimeMillis().toString()))
                    audio = cp.player.core.playback.AudioResource.parse(response)
                } catch (failure: cp.player.core.playback.AudioResolutionException) {
                    if (!failure.retryable || attempt != 0) throw failure
                    kotlinx.coroutines.delay(250)
                }
            }
        }
        val result = checkNotNull(audio)
        val lifetime = (result.lifetimeSeconds * 1000 - 15000).coerceAtLeast(1000)
        synchronized(resolved) { resolved.put(key, Resolved(result, android.os.SystemClock.elapsedRealtime() + lifetime)) }
        return result
    }
    fun clearResolved(source: String) {
        synchronized(resolved) { resolved.snapshot().keys.filter { it.startsWith("$source:") }.forEach(resolved::remove) }
    }
}
