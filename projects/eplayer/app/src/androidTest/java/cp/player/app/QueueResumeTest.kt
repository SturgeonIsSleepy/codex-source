package cp.player.app

import cp.player.core.library.Track
import cp.player.core.queue.QueueStore

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import cp.player.model.Song
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Restore into a real unprepared player; no output, user queue, import or source request. */
@RunWith(AndroidJUnit4::class)
class QueueResumeTest {
    @Test fun removingUnavailableEarlierEntryKeepsCurrentSongAndPosition() = withQueue { context, prefs ->
        val tracks = listOf(Track(Song("unavailable", "Missing", "", album = "")),
            Track(Song("saved-current", "Current", "", album = ""), "file:///fixture-current.wav"),
            Track(Song("next", "Next", "", album = ""), "file:///fixture-next.wav"))
        prefs.edit().putString("items", Gson().toJson(tracks)).putInt("index", 1).putLong("position", 12000).commit()
        withPlayer(context) { player ->
            QueueStore.restore(context, player)
            assertEquals("saved-current", player.currentMediaItem?.mediaId)
            assertEquals(12000L, player.currentPosition)
            assertFalse(player.playWhenReady)
        }
    }

    @Test fun unavailableCurrentMovesToNextWithoutItsOldPosition() = withQueue { context, prefs ->
        val tracks = listOf(Track(Song("previous", "Previous", "", album = ""), "file:///fixture-previous.wav"),
            Track(Song("unavailable-current", "Missing", "", album = "")),
            Track(Song("next", "Next", "", album = ""), "file:///fixture-next.wav"))
        prefs.edit().putString("items", Gson().toJson(tracks)).putInt("index", 1).putLong("position", 12000).commit()
        withPlayer(context) { player ->
            QueueStore.restore(context, player)
            assertEquals("next", player.currentMediaItem?.mediaId)
            assertEquals("Another song must not inherit the missing song's position", 0L, player.currentPosition)
            assertFalse(player.playWhenReady)
        }
    }

    @Test fun unavailableTailFallsBackToLastSurvivingSongAtBeginning() = withQueue { context, prefs ->
        val tracks = listOf(Track(Song("previous", "Previous", "", album = ""), "file:///fixture-previous.wav"),
            Track(Song("missing-tail", "Missing", "", album = "")))
        prefs.edit().putString("items", Gson().toJson(tracks)).putInt("index", 1).putLong("position", 12000).commit()
        withPlayer(context) { player ->
            QueueStore.restore(context, player)
            assertEquals("previous", player.currentMediaItem?.mediaId)
            assertEquals(0L, player.currentPosition)
            assertFalse(player.playWhenReady)
        }
    }

    private fun withQueue(block: (Context, SharedPreferences) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "queue-resume-${UUID.randomUUID()}"
        val context = object : ContextWrapper(target) {
            override fun getSharedPreferences(requested: String, mode: Int): SharedPreferences =
                super.getSharedPreferences(if (requested == "eplayer_queue") name else requested, mode)
        }
        try { block(context, context.getSharedPreferences("eplayer_queue", 0)) }
        finally { target.deleteSharedPreferences(name) }
    }

    private fun withPlayer(context: Context, block: (ExoPlayer) -> Unit) {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val player = ExoPlayer.Builder(context).build()
            try { block(player) } catch (error: Throwable) { failure = error }
            finally { player.release() }
        }
        failure?.let { throw it }
    }
}
