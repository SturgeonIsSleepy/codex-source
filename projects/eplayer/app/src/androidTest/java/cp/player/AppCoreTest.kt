package cp.player

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.app.SourceStore
import cp.player.app.Track
import cp.player.app.withQuality
import cp.player.model.Song
import cp.player.provider.ModuleManager
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppCoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun module(file: File, manifest: String, extra: Pair<String, ByteArray>? = null) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toByteArray()); zip.closeEntry()
            extra?.let { zip.putNextEntry(ZipEntry(it.first)); zip.write(it.second); zip.closeEntry() }
        }
    }
    @Test fun distributionContainsOnlyItsIntendedSource() {
        assertEquals(BuildConfig.INTERNAL_BUILD, context.assets.list("")!!.contains("bundled-provider.zip"))
        if (BuildConfig.INTERNAL_BUILD) {
            val deadline = System.currentTimeMillis() + 30000
            while (ModuleManager.getProvider("netease-cloudmusic-rs") == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
            val source = ModuleManager.getProvider("netease-cloudmusic-rs")
            assertNotNull("Internal module initializes", source)
            assertEquals("1.1.0", source!!.version)
            assertTrue(source.isReady())
            val response = source.callApi("__eplayer_probe__", emptyMap())
            assertFalse("Publisher JNI namespace must resolve", response.contains("JNI call crashed"))
            assertFalse(response.contains("JNI not loaded"))
            assertTrue(response.contains("code"))
        }
    }
    @Test fun failedModuleReplacementPreservesWorkingModuleAndData() {
        val id = "eplayer-transaction-test"
        val valid = File(context.cacheDir, "valid-module.zip")
        val invalid = File(context.cacheDir, "invalid-module.zip")
        val manifest = """{"id":"$id","name":"Test","version":"1.0","type":"http","entryPoint":"https://example.invalid"}"""
        try {
            module(valid, manifest)
            assertTrue(ModuleManager.importModule(context, valid))
            val directory = File(context.filesDir, "modules/$id")
            File(directory, "user_data").mkdirs()
            val saved = File(directory, "user_data/saved.txt").apply { writeText("keep") }
            module(invalid, """{"id":"$id","name":"Broken","version":"2.0","type":"jni","entryPoint":"broken.so"}""", "broken.so" to byteArrayOf(1))
            assertFalse(ModuleManager.importModule(context, invalid))
            assertEquals(manifest, File(directory, "manifest.json").readText())
            assertEquals("keep", saved.readText())
            assertEquals("1.0", ModuleManager.getProvider(id)!!.version)
        } finally { ModuleManager.deleteModule(id); valid.delete(); invalid.delete() }
    }
    @Test fun invalidManifestIdCannotEscapeModuleDirectory() {
        val file = File(context.cacheDir, "unsafe-module.zip")
        try {
            module(file, """{"id":"../escape-test","name":"Test","version":"1","type":"http","entryPoint":"https://example.invalid"}""")
            assertFalse(ModuleManager.importModule(context, file))
            assertFalse(File(context.filesDir, "escape-test").exists())
        } finally { file.delete() }
    }
    @Test fun queueKeepsOriginalSourceAndReadableMetadata() {
        val track = Track(Song("123", "很长的歌曲名称", "艺术家", album = "专辑"), source = "source-a")
        val item = track.mediaItem()
        assertEquals("source-a", item.localConfiguration!!.uri.getQueryParameter("source"))
        assertEquals("source-a", item.mediaMetadata.extras!!.getString("source"))
        assertEquals("很长的歌曲名称", item.mediaMetadata.title.toString())
    }
    @Test fun qualityUpdatesKeepSourceIdentityAndNeverRewriteLocalFiles() {
        val first = Track(Song("123", "first", "artist", album = "album"), source = "source-a").mediaItem("standard")
        val next = Track(Song("456", "next", "artist", album = "album"), source = "source-b").mediaItem("standard")
        val local = Track(Song("local_7", "local", "artist", album = "album"), uri = "content://media/external/audio/media/7").mediaItem()
        val queue = listOf(first, local, next).map { it.withQuality("lossless") }
        assertEquals("lossless", queue[0].localConfiguration!!.uri.getQueryParameter("quality"))
        assertEquals("lossless", queue[2].localConfiguration!!.uri.getQueryParameter("quality"))
        assertEquals("source-b", queue[2].localConfiguration!!.uri.getQueryParameter("source"))
        assertEquals(next.mediaMetadata, queue[2].mediaMetadata)
        assertSame(local, queue[1])
        assertSame(queue[0], queue[0].withQuality("lossless"))
        assertEquals(first.localConfiguration!!.uri, queue[0].withQuality("standard").localConfiguration!!.uri)
    }
}
