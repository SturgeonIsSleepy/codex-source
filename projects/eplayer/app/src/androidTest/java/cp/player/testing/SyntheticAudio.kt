package cp.player.testing

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/** Deterministic test signal, never a recording or a main-source-set asset. */
object SyntheticAudio {
    fun wav(seconds: Int, sampleRate: Int = 44100): ByteArray {
        require(seconds in 1..60)
        require(sampleRate == 44100 || sampleRate == 48000)
        val samples = sampleRate * seconds
        val output = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        output.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(samples * 2)
        val periodSamples = sampleRate / 20
        val period = ByteBuffer.allocate(periodSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(periodSamples) { i -> period.putShort((sin(i * 2 * Math.PI * 440 / sampleRate) * 3000).toInt().toShort()) }
        repeat(samples / periodSamples) { output.put(period.array()) }
        return output.array()
    }
}
