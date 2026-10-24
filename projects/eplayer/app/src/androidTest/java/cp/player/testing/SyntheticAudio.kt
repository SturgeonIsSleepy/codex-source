package cp.player.testing

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/** Deterministic test signal, never a recording or a main-source-set asset. */
object SyntheticAudio {
    fun wav(seconds: Int): ByteArray {
        require(seconds in 1..60)
        val samples = 44100 * seconds
        val output = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        output.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(44100).putInt(88200).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples * 2)
        val period = ByteBuffer.allocate(2205 * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(2205) { i -> period.putShort((sin(i * 2 * Math.PI * 440 / 44100) * 3000).toInt().toShort()) }
        repeat(samples / 2205) { output.put(period.array()) }
        return output.array()
    }
}
