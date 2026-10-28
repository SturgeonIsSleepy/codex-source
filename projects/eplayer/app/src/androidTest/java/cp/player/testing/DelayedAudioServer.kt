package cp.player.testing

import android.net.Uri
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** A single real HTTP response, held below the native prebuffer until the test releases it. */
class DelayedAudioServer(id: String, body: ByteArray, allowClientCancellation: Boolean = false) : Closeable {
    private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    private val client = AtomicReference<Socket?>()
    private val worker = Executors.newSingleThreadExecutor()
    private val release = CountDownLatch(1)
    val firstChunk = CountDownLatch(1)
    val uri: Uri = Uri.parse("http://127.0.0.1:${server.localPort}/$id.wav")
    private val response = worker.submit {
        server.accept().use { socket ->
            client.set(socket)
            socket.soTimeout = 10000
            val reader = socket.getInputStream().bufferedReader()
            while (true) { val line = reader.readLine() ?: break; if (line.isEmpty()) break }
            val output = socket.getOutputStream()
            output.write("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            output.write(body, 0, 96 * 1024); output.flush()
            firstChunk.countDown()
            check(release.await(30, TimeUnit.SECONDS)) { "Fixture release timed out" }
            try {
                output.write(body, 96 * 1024, body.size - 96 * 1024); output.flush()
                socket.shutdownOutput()
                // Join the client's response consumption/close, not just the server's write.
                check(socket.getInputStream().read() == -1) { "Unexpected second HTTP request" }
            } catch (closed: SocketException) {
                if (!allowClientCancellation) throw closed
            }
        }
    }

    fun finish() { release.countDown(); response.get(15, TimeUnit.SECONDS) }

    override fun close() {
        release.countDown()
        server.close()
        client.get()?.close()
        worker.shutdownNow()
    }
}
