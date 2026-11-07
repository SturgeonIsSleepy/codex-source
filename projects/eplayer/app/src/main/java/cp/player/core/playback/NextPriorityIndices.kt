package cp.player.core.playback

internal fun nextPriorityIndices(tokens: List<String?>, current: Int, consumed: Set<String>): List<Int> {
    if (tokens.isEmpty()) return emptyList()
    return (1..tokens.size).map { (current.coerceAtLeast(0) + it) % tokens.size }
        .filter { it != current && tokens[it]?.let { token -> token !in consumed } == true }
}
