package cp.player.core.playback

/** Service-owned timer. Call on the player thread; UI lifetime never owns its task. */
class PlaybackSleepTimer(
    private val now: () -> Long,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val pause: () -> Unit,
    private val changed: (Long) -> Unit,
) {
    var deadline: Long = 0
        private set
    private var generation = 0
    private var cancelTask: (() -> Unit)? = null

    fun set(durationMs: Long) {
        require(durationMs in 0..MAX_DURATION_MS)
        cancelTask?.invoke()
        cancelTask = null
        val version = ++generation
        deadline = if (durationMs == 0L) 0 else now() + durationMs
        changed(deadline)
        if (durationMs > 0) arm(version)
    }

    private fun arm(version: Int) {
        cancelTask = schedule((deadline - now()).coerceAtLeast(0)) {
            if (version == generation && deadline != 0L) {
                // A scheduler may fire early. The monotonic deadline is authoritative.
                if (now() < deadline) arm(version)
                else { set(0); pause() }
            }
        }
    }

    companion object {
        const val COMMAND = "SET_SLEEP_TIMER"
        const val DURATION_MS = "duration_ms"
        const val DEADLINE = "sleep_deadline_elapsed_ms"
        const val MAX_DURATION_MS = 180 * 60 * 1000L
    }
}
