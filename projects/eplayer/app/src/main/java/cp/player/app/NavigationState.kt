package cp.player.app

internal fun openSettingsPath(path: List<String>, next: String): List<String> =
    if (path.lastOrNull() == next) path else path + next
internal fun previousSettingsPath(path: List<String>): List<String> = path.dropLast(1)
