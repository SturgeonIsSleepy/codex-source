package cp.player.core.playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.Cache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** Process-owned streaming cache. Data sources keep this same live instance across service changes. */
object PlaybackCache {
    const val LIMIT_BYTES = 256L * 1024 * 1024
    private var instance: SimpleCache? = null
    @Synchronized fun get(context: Context): SimpleCache {
        val app = context.applicationContext
        return instance ?: SimpleCache(File(app.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(LIMIT_BYTES), StandaloneDatabaseProvider(app)).also { instance = it }
    }
    suspend fun size(context: Context): Long = withContext(Dispatchers.IO) { get(context).cacheSpace }

    suspend fun clear(context: Context): Long = withContext(Dispatchers.IO) {
        val cache = get(context)
        removeResources(cache)
        cache.cacheSpace
    }

    /** Keep the live cache and database open; readers and new writers retain their owner. */
    internal suspend fun removeResources(cache: Cache) {
        for (key in cache.keys.toList()) {
            currentCoroutineContext().ensureActive()
            cache.removeResource(key)
        }
    }

}
