package cp.player.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import cp.player.app.SourceStore
import cp.player.core.playback.AudioResource
import cp.player.core.playback.AudioRepresentationChanged
import cp.player.manager.DownloadRegistry
import cp.player.provider.ProviderManager
import java.io.IOException
import kotlinx.coroutines.runBlocking

/** Resolve the actual audio representation before a cache can satisfy any byte range. */
class BackendDataSource(private val context: Context, private val http: DataSource.Factory,
    private val cache: (() -> Cache)? = null,
    private val resolveAudio: (String, String, String, Boolean) -> AudioResource = { source, id, quality, refresh ->
        runBlocking { SourceStore.resolveAudio(context, source, id, quality, refresh) }
    }
) : BaseDataSource(true) {
    private var active: DataSource? = null
    private var opened = false
    // An extractor may reopen this source at a byte offset. Never switch formats under it.
    private var pinnedIdentity: String? = null
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        try {
            active?.close()
            val uri = dataSpec.uri
            var resolved = dataSpec
            var streamingCache: Cache? = null
            var resource: AudioResource? = null
            var source: String? = null
            var id: String? = null
            val quality = if (uri.scheme == "cp") uri.getQueryParameter("quality") ?: "standard" else "standard"
            if (uri.scheme == "cp") {
                id = (if (uri.host == "song") uri.pathSegments.firstOrNull() else uri.host) ?: error("歌曲 ID 为空")
                source = uri.getQueryParameter("source") ?: ProviderManager.currentProvider?.id ?: error("请选择音源")
                val local = (DownloadRegistry.getMetadata(id, source) ?: DownloadRegistry.getMetadata(id)?.takeIf {
                    it.providerId.isBlank() || it.providerId == source
                })?.filePath
                if (local != null) {
                    resolved = dataSpec.buildUpon().setUri(if (local.startsWith("content:")) Uri.parse(local)
                        else Uri.fromFile(java.io.File(local))).build()
                } else {
                    streamingCache = cache?.invoke()
                    val previous = SourceStore.peekAudio(source, id, quality)
                    val remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong())
                        (previous?.size ?: 0) - dataSpec.position else dataSpec.length
                    val fullyCached = previous != null && remaining > 0 && streamingCache?.isCached(
                        previous.cacheKey(source, id, quality), dataSpec.position, remaining) == true
                    resource = if (fullyCached) previous else resolveAudio(source, id, quality, false)
                    val key = resource.cacheKey(source, id, quality)
                    if (pinnedIdentity != null && pinnedIdentity != key) throw AudioRepresentationChanged()
                    pinnedIdentity = key
                    resolved = dataSpec.buildUpon().setUri(resource.url).setKey(key).build()
                }
            } else if (uri.scheme in setOf("http", "https")) streamingCache = cache?.invoke()

            fun newSource(): DataSource = when (resolved.uri.scheme) {
                "content" -> ContentDataSource(context)
                "http", "https" -> streamingCache?.let {
                    CacheDataSource.Factory().setCache(it).setUpstreamDataSourceFactory(http)
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR).createDataSource()
                } ?: http.createDataSource()
                else -> FileDataSource()
            }
            active = newSource()
            val length = try { active!!.open(resolved) }
            catch (failure: HttpDataSource.InvalidResponseCodeException) {
                // Refresh an expired CDN URL once, without splicing a different representation.
                if (resource == null || source == null || id == null || failure.responseCode !in setOf(401, 403, 410)) throw failure
                active?.close()
                val next = resolveAudio(source, id, quality, true)
                if (next.cacheKey(source, id, quality) != pinnedIdentity) throw AudioRepresentationChanged()
                resolved = resolved.buildUpon().setUri(next.url).build()
                active = newSource()
                active!!.open(resolved)
            }
            transferStarted(dataSpec)
            opened = true
            return length
        } catch (error: Exception) {
            runCatching { active?.close() }; active = null
            throw if (error is IOException) error else IOException(error.message, error)
        }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val count = active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
        if (count > 0) bytesTransferred(count)
        return count
    }
    override fun getUri(): Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()
    override fun close() { try { active?.close() } finally { active = null; if (opened) { opened = false; transferEnded() } } }
    class Factory(private val context: Context, private val http: DataSource.Factory,
        private val cache: (() -> Cache)? = null) : DataSource.Factory {
        override fun createDataSource(): DataSource = BackendDataSource(context, http, cache)
    }
}
