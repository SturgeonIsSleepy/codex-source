package cp.player.util

import android.content.Context
import android.net.Uri
import cp.player.core.audio.EqualizerProfile
import cp.player.core.audio.EqualizerProfileParser

data class PeqBand(val freq: Float, val gain: Float, val q: Float)

/** URI I/O only; text validation is shared with decoder-independent unit tests. */
object AutoEqParser {
    fun readProfile(context: Context, uri: Uri): EqualizerProfile {
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取均衡器文件")
        val text = input.bufferedReader().use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                require(result.length + count <= EqualizerProfileParser.MAX_CHARACTERS) { "均衡器文件过大" }
                result.append(buffer, 0, count)
            }
            result.toString()
        }
        return EqualizerProfileParser.parse(text)
    }
}
