package cp.player.service

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import cp.player.app.SourceStore
import cp.player.manager.DownloadRegistry
import cp.player.provider.ProviderManager
import java.io.IOException
import kotlinx.coroutines.runBlocking

/** Resolve only the source recorded in the queue; local files bypass network/cache duplication. */
class BackendDataSource(private val context: Context, private val http: DataSource) : BaseDataSource(true) {
    private var active: DataSource? = null
    private var opened = false
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val uri = dataSpec.uri
        try {
            active?.close()
            var resolved = dataSpec
            if (uri.scheme == "cp") {
                val id = (if (uri.host == "song") uri.pathSegments.firstOrNull() else uri.host) ?: error("歌曲 ID 为空")
                val source = uri.getQueryParameter("source") ?: ProviderManager.currentProvider?.id ?: error("请选择音源")
                val local = (DownloadRegistry.getMetadata(id, source) ?: DownloadRegistry.getMetadata(id))?.filePath
                val target = local?.let { if (it.startsWith("content:")) Uri.parse(it) else Uri.fromFile(java.io.File(it)) }
                    ?: Uri.parse(runBlocking { SourceStore.resolve(context, source, id, uri.getQueryParameter("quality") ?: "standard") })
                resolved = dataSpec.buildUpon().setUri(target).setKey("$source:$id").build()
            }
            active = when (resolved.uri.scheme) {
                "content" -> ContentDataSource(context)
                "http", "https" -> http
                else -> FileDataSource()
            }
            val length = active!!.open(resolved)
            transferStarted(dataSpec)
            opened = true
            return length
        } catch (error: Exception) { throw if (error is IOException) error else IOException(error.message, error) }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val count = active?.read(buffer, offset, length) ?: -1
        if (count > 0) bytesTransferred(count)
        return count
    }
    override fun getUri(): Uri? = active?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()
    override fun close() { try { active?.close() } finally { active = null; if (opened) { opened = false; transferEnded() } } }
    class Factory(private val context: Context, private val http: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = BackendDataSource(context, http.createDataSource())
    }
}
