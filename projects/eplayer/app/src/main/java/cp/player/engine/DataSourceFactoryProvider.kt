package cp.player.engine

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.cache.Cache
import cp.player.service.BackendDataSource
import cp.player.core.playback.PlaybackCache

@OptIn(UnstableApi::class)
object DataSourceFactoryProvider {
    private const val USER_AGENT = "NeteaseMusic/9.1.20 (iPhone; iOS 16.5; Scale/3.00)"
    private val REFERER_HEADERS = mapOf("Referer" to "https://music.163.com")

    fun createHttpDataSourceFactory(context: Context): OkHttpDataSource.Factory {
        return OkHttpDataSource.Factory(cp.player.network.PlaybackNetwork.calls(context.applicationContext))
            .setDefaultRequestProperties(REFERER_HEADERS)
            .setUserAgent(USER_AGENT)
    }

    fun createCpDataSourceFactory(context: Context): DataSource.Factory {
        return BackendDataSource.Factory(context, createHttpDataSourceFactory(context))
    }

    fun createCacheDataSourceFactory(context: Context,
        cache: (() -> Cache)? = null): DataSource.Factory {
        val app = context.applicationContext
        // BackendDataSource resolves cp:// first, then selects a representation-specific cache.
        // Cache initialization remains deferred until an actual remote stream opens.
        return DefaultDataSource.Factory(app, BackendDataSource.Factory(app,
            createHttpDataSourceFactory(app), cache ?: { PlaybackCache.get(app) }))
    }
}
