package cp.player

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.engine.StreamProxy
import cp.player.testing.SyntheticAudio
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** A local source isolates cancellation from network timing and keeps all files test-owned. */
@RunWith(AndroidJUnit4::class)
class StreamProxyCancellationTest {
    @Test fun cancelledReadCannotPublishAReadyFile() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File.createTempFile("stream-cancel-", "", app.cacheDir).apply {
            check(delete()); check(mkdir())
        }
        val context = object : ContextWrapper(app) { override fun getCacheDir(): File = directory }
        val body = SyntheticAudio.wav(3)
        val input = File(directory, "input.wav").apply { writeBytes(body) }
        val ready = AtomicBoolean(false)
        val read = AtomicBoolean(false)
        try {
            val download = launch(Dispatchers.IO) {
                StreamProxy.streamToCache(context, android.net.Uri.fromFile(input), body.size.toLong() + 1,
                    onPreBufferReady = { ready.set(true) },
                    onProgress = { read.set(true); cancel("The playback owner replaced this download") })
            }
            download.join()
            assertTrue("The source must have returned real bytes before cancellation", read.get())
            assertTrue("The download belongs to the cancelled job", download.isCancelled)
            assertFalse("Cancelled downloads must not notify a new playback owner", ready.get())
            assertTrue("Cancelled temporary output must be removed", directory.listFiles().orEmpty().none {
                it.name.startsWith("stream_cache_")
            })
        } finally { directory.deleteRecursively() }
    }
}
