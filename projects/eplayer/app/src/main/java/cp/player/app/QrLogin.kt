package cp.player.app

import android.graphics.Bitmap
import android.graphics.Color
import com.google.gson.JsonObject
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Rust returns a URL instead of qrimg. Render it locally without exposing the login key. */
object QrLogin {
    private fun JsonObject.text(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank)
    private fun payload(response: JsonObject): JsonObject {
        val code = response.get("code")?.takeIf { it.isJsonPrimitive }?.asInt
        check(code == null || code == 200) {
            response.text("msg") ?: response.text("message") ?: "登录接口返回错误${code?.let { "（$it）" }.orEmpty()}"
        }
        return response.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: response
    }
    fun key(response: JsonObject): String = payload(response).text("unikey") ?: response.text("unikey")
        ?: error("音源未返回二维码 key，请重试")
    fun url(response: JsonObject): String {
        val url = payload(response).text("qrurl") ?: response.text("qrurl") ?: error("音源未返回登录链接")
        check(url.startsWith("https://") || url.startsWith("http://")) { "登录链接无效" }
        return url
    }
    fun bitmap(url: String): Bitmap {
        val size = 512
        val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"))
        val pixels = IntArray(size * size) { index -> if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply { setPixels(pixels, 0, size, 0, 0, size, size) }
    }
}
