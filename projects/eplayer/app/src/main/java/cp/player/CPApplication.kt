package cp.player

import android.app.Application
import kotlinx.coroutines.launch
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

class CPApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()

        android.util.Log.i("CPApplication", "Application onCreate")
        cp.player.manager.DownloadRegistry.init(this)
        cp.player.manager.LocalMusicManager.init(this)
        cp.player.api.MusicApiServiceFactory.init(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            cp.player.app.SourceStore.initialize(this@CPApplication)
        }

    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) cp.player.ui.artwork.WitchPlayerView.clearAssetCache()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.12)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
