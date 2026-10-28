package cp.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.app.*
import cp.player.provider.ModuleManager
import cp.player.util.UserPreferences
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Account and write operations hit only this loopback fixture, never a user's NCM account. */
@RunWith(AndroidJUnit4::class)
class MusicRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private class Fixture : AutoCloseable {
        val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        val requests = CopyOnWriteArrayList<Pair<String, JSONObject>>()
        private val song = """{"id":123,"name":"fixture-song","ar":[],"al":{"id":7,"name":"fixture-album"},"dt":30000}"""
        private val playlist = """{"id":88,"name":"fixture-playlist","creator":{"userId":42},"trackCount":1}"""
        private val worker = thread(isDaemon = true) {
            while (!socket.isClosed) try {
                socket.accept().use { client ->
                    val input = client.getInputStream().bufferedReader(Charsets.UTF_8)
                    val path = input.readLine().split(' ')[1].substringBefore('?')
                    var length = 0
                    while (true) {
                        val header = input.readLine() ?: break
                        if (header.isEmpty()) break
                        if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                    }
                    // Test parameters are ASCII; HTTP bytes and decoded characters have equal length.
                    val body = CharArray(length)
                    var read = 0
                    while (read < length) { val count = input.read(body, read, length - read); if (count < 0) break; read += count }
                    requests += path to JSONObject(String(body, 0, read).ifBlank { "{}" })
                    val response = when (path) {
                        "/login/status" -> """{"code":200,"data":{"profile":{"userId":42,"nickname":"fixture-user"}}}"""
                        "/user/playlist" -> """{"code":200,"playlist":[$playlist]}"""
                        "/likelist" -> """{"code":200,"ids":[123]}"""
                        "/recommend/resource" -> """{"code":200,"recommend":[$playlist]}"""
                        "/recommend/songs" -> """{"code":200,"data":{"dailySongs":[$song]}}"""
                        "/cloudsearch" -> """{"code":200,"result":{"songs":[$song],"playlists":[$playlist],"albums":[{"id":7,"name":"fixture-album"}],"artists":[{"id":9,"name":"fixture-artist"}]}}"""
                        "/playlist/detail" -> """{"code":200,"playlist":$playlist}"""
                        "/playlist/track/all", "/song/detail" -> """{"code":200,"songs":[$song]}"""
                        "/playlist/create" -> """{"code":200,"playlist":$playlist}"""
                        "/comment/music" -> """{"code":200,"comments":[{"commentId":6,"content":"fixture-comment","user":{"nickname":"fixture-user"},"likedCount":2}]}"""
                        else -> """{"code":200}"""
                    }.toByteArray()
                    client.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(response); flush()
                    }
                }
            } catch (_: Exception) { if (socket.isClosed) break }
        }
        override fun close() { socket.close(); worker.join(2000) }
    }
    private fun exercise(action: suspend (MusicRepository, Fixture) -> Unit) = runBlocking {
        val id = "eplayer-cloud-fixture"
        val archive = File(context.cacheDir, "$id.zip")
        Fixture().use { fixture ->
            try {
                val manifest = JSONObject().put("id", id).put("name", "fixture").put("version", "1")
                    .put("type", "http").put("entryPoint", "http://127.0.0.1:${fixture.socket.localPort}")
                ZipOutputStream(archive.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest.toString().toByteArray()); zip.closeEntry()
                }
                assertTrue(ModuleManager.importModule(context, archive))
                UserPreferences.getPrefs(context).edit().putString("cookie_$id", "fixture-session").commit()
                action(MusicRepository(context, id), fixture)
            } finally {
                ModuleManager.deleteModule(id)
                UserPreferences.getPrefs(context).edit().remove("cookie_$id").commit()
                archive.delete()
            }
        }
    }
    @Test fun accountStatusCarriesSessionAndLoadsLibrary() = exercise { repo, fixture ->
        assertEquals("42", repo.account()!!.id)
        assertEquals("88", repo.playlists("42").single().id)
        assertEquals(setOf("123"), repo.liked("42"))
        assertEquals("fixture-session", fixture.requests.first { it.first == "/login/status" }.second.getString("cookie"))
    }
    @Test fun searchCategoriesAndPlaylistDetailRemainPlayable() = exercise { repo, _ ->
        val track = repo.search("fixture", 1).tracks.single()
        assertEquals("7", track.song.albumId)
        assertEquals("eplayer-cloud-fixture", track.source)
        assertEquals(MusicKind.PLAYLIST, repo.search("fixture", 1000).entries.single().kind)
        assertEquals(MusicKind.ALBUM, repo.search("fixture", 10).entries.single().kind)
        assertEquals(MusicKind.ARTIST, repo.search("fixture", 100).entries.single().kind)
        val target = repo.playlists("42").single()
        assertEquals("123", repo.detail(target).tracks.single().song.id)
        assertEquals(1, repo.daily().size)
    }
    @Test fun playlistAndCommentWritesUseCorrectProtocolOnlyOnFixture() = exercise { repo, fixture ->
        repo.like("123", true)
        repo.createPlaylist("fixture", true)
        repo.renamePlaylist("88", "renamed")
        repo.changeTracks("88", listOf("123"), true)
        repo.changeTracks("88", listOf("123"), false)
        repo.sendComment("123", "fixture", "6")
        assertEquals("10", fixture.requests.first { it.first == "/playlist/create" }.second.getString("privacy"))
        assertEquals(listOf("add", "del"), fixture.requests.filter { it.first == "/playlist/tracks" }.map { it.second.getString("op") })
        val reply = fixture.requests.first { it.first == "/comment" }.second
        assertEquals("2", reply.getString("t")); assertEquals("6", reply.getString("commentId"))
        assertEquals("fixture-comment", repo.comments("123").single().text)
    }
}
