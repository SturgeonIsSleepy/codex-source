package cp.player.engine

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import cp.player.service.BackendDataSource
import cp.player.core.playback.PlaybackCache
import java.io.IOException

@OptIn(UnstableApi::class)
object DataSourceFactoryProvider {
    private const val USER_AGENT = "NeteaseMusic/9.1.20 (iPhone; iOS 16.5; Scale/3.00)"
    private val REFERER_HEADERS = mapOf("Referer" to "https://music.163.com")

    fun createHttpDataSourceFactory(): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(REFERER_HEADERS)
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(USER_AGENT)
    }

    fun createCpDataSourceFactory(context: Context): DataSource.Factory {
        return BackendDataSource.Factory(context, createHttpDataSourceFactory())
    }

    fun createCacheDataSourceFactory(context: Context,
        cache: (() -> Cache)? = null): DataSource.Factory {
        val app = context.applicationContext
        val cachedStreams = DataSource.Factory { DeferredStreamSource {
            CacheDataSource.Factory().setCache(cache?.invoke() ?: PlaybackCache.get(app))
                .setUpstreamDataSourceFactory(createCpDataSourceFactory(app))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR).createDataSource()
        } }
        // DefaultDataSource reads file/content URIs directly; remote and cp:// streams
        // still use the cache. Imported/downloaded files need no second disk copy.
        return DefaultDataSource.Factory(app, cachedStreams)
    }

    /** Cache directory/database scanning happens only when a remote stream is opened. */
    private class DeferredStreamSource(private val create: () -> DataSource) : DataSource {
        private var delegate: DataSource? = null
        private val listeners = mutableListOf<TransferListener>()
        override fun addTransferListener(listener: TransferListener) {
            if (!listeners.contains(listener)) {
                listeners += listener
                delegate?.addTransferListener(listener)
            }
        }
        override fun open(dataSpec: DataSpec): Long {
            val source = delegate ?: create().also { next ->
                listeners.forEach(next::addTransferListener)
                delegate = next
            }
            return source.open(dataSpec)
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            (delegate ?: throw IOException("Stream is not open")).read(buffer, offset, length)
        override fun getUri() = delegate?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()
        override fun close() { delegate?.close() }
    }
}
