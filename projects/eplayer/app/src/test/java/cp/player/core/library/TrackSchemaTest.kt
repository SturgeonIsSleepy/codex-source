package cp.player.core.library

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** The stored JSON predates the data-package move; package names must not change user state. */
class TrackSchemaTest {
    @Test fun existingLibraryAndQueueJsonKeepsSourceIdentityAndAudioUri() {
        val legacy = """{"song":{"id":"import_fixture","name":"紙の旅","artist":"Fixture","album":"Test","durationMs":30000},"uri":"content://app.eplayer.music.fileprovider/audio/import_fixture.audio","source":"fixture-source","remoteId":"remote-123"}"""
        val gson = Gson()
        val track = gson.fromJson(legacy, Track::class.java)
        assertEquals("import_fixture", track.song.id)
        assertEquals("紙の旅", track.song.name)
        assertEquals(30000L, track.song.durationMs)
        assertEquals("content://app.eplayer.music.fileprovider/audio/import_fixture.audio", track.uri)
        assertEquals("fixture-source", track.source)
        assertEquals("remote-123", track.remoteId)
        val encoded = JsonParser.parseString(gson.toJson(track)).asJsonObject
        assertEquals(JsonParser.parseString(legacy), encoded)
    }
}
