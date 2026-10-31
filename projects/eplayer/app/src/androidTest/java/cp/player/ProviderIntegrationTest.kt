package cp.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.core.source.SourceStore
import cp.player.provider.ModuleManager
import cp.player.provider.ProviderManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread

/** A loopback-only provider verifies ZIP loading and API mapping without third-party credentials. */
@RunWith(AndroidJUnit4::class)
class ProviderIntegrationTest {
    @Test fun sourceModuleImportSearchAndUrlResolution() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = ProviderManager.currentProvider
        val id = "eplayer-integration-fixture"
        assertNull("Test module ID must be unused", ModuleManager.getModuleDir(id))
        val requests = CopyOnWriteArrayList<String>()
        val queries = CopyOnWriteArrayList<String>()
        val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val worker = thread(name = "ePlayer-Test-Provider", isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val input = socket.getInputStream().buffered()
                        fun readHeaderLine(): String? {
                            val bytes = java.io.ByteArrayOutputStream()
                            while (true) {
                                val value = input.read()
                                if (value < 0) return if (bytes.size() == 0) null else bytes.toString("US-ASCII")
                                if (value == 10) return bytes.toString("US-ASCII")
                                if (value != 13) bytes.write(value)
                            }
                        }
                        val path = readHeaderLine()?.split(' ')?.getOrNull(1) ?: "/"
                        var length = 0
                        while (true) {
                            val line = readHeaderLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                        }
                        val body = ByteArray(length)
                        var offset = 0
                        while (offset < length) { val read = input.read(body, offset, length - offset); if (read < 0) break; offset += read }
                        requests.add(path)
                        if (path == "/search") queries.add(JSONObject(String(body, Charsets.UTF_8)).optString("keywords"))
                        val payload = when (path) {
                            "/search" -> """{"code":200,"result":{"songs":[{"id":987654,"name":"旅途测试音","ar":[{"id":1,"name":"ePlayer"}],"al":{"id":1,"name":"旅行手札"},"dt":45000}],"songCount":1}}"""
                            "/song/url/v1" -> """{"code":200,"data":[{"id":987654,"url":"http://127.0.0.1:${server.localPort}/fixture.wav","br":128000,"type":"wav","code":200}]}"""
                            else -> """{"code":501,"msg":"Fixture endpoint not implemented"}"""
                        }.toByteArray(Charsets.UTF_8)
                        val output = socket.getOutputStream()
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(payload); output.flush()
                    }
                } catch (_: Exception) { if (server.isClosed) break }
            }
        }
        val archive = File(context.cacheDir, "$id.zip")
        try {
            val manifest = JSONObject().put("id", id).put("name", "ePlayer integration fixture")
                .put("version", "1.0.0").put("type", "http")
                .put("entryPoint", "http://127.0.0.1:${server.localPort}")
                .put("apiMap", JSONObject().put("cloudsearch", "search"))
            ZipOutputStream(archive.outputStream()).use {
                it.putNextEntry(ZipEntry("manifest.json")); it.write(manifest.toString().toByteArray()); it.closeEntry()
            }
            assertTrue(ModuleManager.importModule(context, archive))
            val provider = ModuleManager.getProvider(id)
            assertNotNull(provider)
            assertTrue(ProviderManager.switchProvider(provider, context, save = false))
            val search = SourceStore.call(context, id, "cloudsearch", mapOf("keywords" to "旅途", "type" to "1"))
            assertEquals("旅途测试音", search.getAsJsonObject("result").getAsJsonArray("songs")[0].asJsonObject.get("name").asString)
            val urls = SourceStore.call(context, id, "song/url/v1", mapOf("id" to "987654"))
            assertTrue(urls.getAsJsonArray("data")[0].asJsonObject.get("url").asString.endsWith("/fixture.wav"))
            assertTrue("Provider API map must be applied", requests.contains("/search"))
            assertTrue("Unicode search text must survive the provider transport", queries.contains("旅途"))
        } finally {
            ProviderManager.switchProvider(previous, context, save = false)
            ModuleManager.deleteModule(id)
            server.close(); worker.join(2000); archive.delete()
        }
    }
}
