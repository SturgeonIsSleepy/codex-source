package cp.player.core.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** Process-owned streaming cache. Data sources keep this same live instance across service changes. */
object PlaybackCache {
    private var instance: SimpleCache? = null
    @Synchronized fun get(context: Context): SimpleCache {
        val app = context.applicationContext
        return instance ?: SimpleCache(File(app.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024), StandaloneDatabaseProvider(app)).also { instance = it }
    }
}
