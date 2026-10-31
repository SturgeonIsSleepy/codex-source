package cp.player.core.listening

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class ListeningDay(val date: LocalDate, val milliseconds: Long, val songs: Int)
data class ListeningStats(val days: List<ListeningDay> = emptyList()) {
    val minutes: Long get() = days.sumOf { it.milliseconds } / 60000
    val songs: Int get() = days.sumOf { it.songs }
    val activeDays: Int get() = days.count { it.milliseconds >= 1000 }
}

/** Counts elapsed playing time; buffering, seeking and a paused UI add no time. */
internal class ListeningClock {
    private var since: Long? = null
    private var track: String? = null
    fun update(now: Long, id: String?, playing: Boolean): Pair<String, Long>? {
        val elapsed = since?.let { (now - it).coerceAtLeast(0) } ?: 0
        val result = track?.takeIf { elapsed > 0 }?.let { it to elapsed }
        since = if (playing && id != null) now else null
        track = id
        return result
    }
}

/** Local aggregates only. The service checkpoints while playing and on state changes. */
object ListeningStatsStore {
    private val _stats = MutableStateFlow(ListeningStats())
    val stats = _stats.asStateFlow()
    @Synchronized fun refresh(context: Context) {
        val prefs = context.getSharedPreferences("eplayer_listening", 0)
        val today = LocalDate.now()
        _stats.value = ListeningStats((6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            ListeningDay(day, prefs.getLong("ms_$day", 0), prefs.getStringSet("songs_$day", emptySet()).orEmpty().size)
        })
    }
    @Synchronized fun record(context: Context, track: String, milliseconds: Long) {
        if (milliseconds <= 0) return
        val prefs = context.getSharedPreferences("eplayer_listening", 0)
        val day = LocalDate.now()
        val songs = prefs.getStringSet("songs_$day", emptySet()).orEmpty().toMutableSet()
        if (milliseconds >= 1000) songs.add(track)
        prefs.edit().putLong("ms_$day", prefs.getLong("ms_$day", 0) + milliseconds)
            .putStringSet("songs_$day", songs).apply()
        refresh(context)
    }
}
