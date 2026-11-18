package cp.player.network

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import okhttp3.FormBody
import okhttp3.Request

/** Same song and level, using the account's existing session directly with NetEase. */
internal object NeteaseAudio {
    private const val path = "/api/song/enhance/player/url/v1"
    private const val endpoint = "https://interface.music.163.com/eapi/song/enhance/player/url/v1"

    fun resolve(context: Context, id: String, level: String, cookie: String?): JsonObject {
        require(id.matches(Regex("[0-9]+")))
        val payload = JsonObject().apply {
            addProperty("ids", "[$id]")
            addProperty("level", level)
            addProperty("encodeType", "flac")
            if (level == "sky") addProperty("immerseType", "c51")
        }.toString()
        val checksum = MessageDigest.getInstance("MD5")
            .digest("nobody${path}use${payload}md5forencrypt".toByteArray()).toHex()
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES"))
        val encoded = cipher.doFinal("$path-36cd479b6b5-$payload-36cd479b6b5-$checksum".toByteArray()).toHex()
        val request = Request.Builder().url(endpoint).header("Referer", "https://music.163.com/")
            .post(FormBody.Builder().add("params", encoded).build())
            .apply { if (!cookie.isNullOrBlank()) header("Cookie", cookie) }.build()
        val call = PlaybackNetwork.calls(context).newCall(request)
        call.timeout().timeout(8, java.util.concurrent.TimeUnit.SECONDS)
        call.execute().use { response ->
            if (!response.isSuccessful) return JsonObject().apply { addProperty("code", response.code) }
            val input = response.body.source()
            if (input.request(65537)) throw IOException("音源响应过大")
            return try { JsonParser.parseString(input.readUtf8()).asJsonObject }
                catch (_: RuntimeException) { throw IOException("网易云返回了无效的音源响应") }
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
}
