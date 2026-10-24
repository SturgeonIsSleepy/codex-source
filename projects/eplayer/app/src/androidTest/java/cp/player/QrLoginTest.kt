package cp.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonParser
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import cp.player.app.QrLogin
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrLoginTest {
    @Test fun readsRustFlatKeyAndWrappedKey() {
        assertEquals("fixture", QrLogin.key(JsonParser.parseString("""{"code":200,"unikey":"fixture"}""").asJsonObject))
        assertEquals("fixture", QrLogin.key(JsonParser.parseString("""{"code":200,"data":{"unikey":"fixture"}}""").asJsonObject))
    }
    @Test fun rustUrlWithoutQrImageProducesScannableCode() {
        val expected = "https://music.163.com/login?codekey=fixture"
        val response = JsonParser.parseString("""{"code":200,"data":{"qrurl":"$expected","qrimg":""}}""").asJsonObject
        val bitmap = QrLogin.bitmap(QrLogin.url(response))
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))))
            assertEquals(expected, decoded.text)
        } finally { bitmap.recycle() }
    }
    @Test fun missingKeyReportsUsefulErrorInsteadOfNullPointer() {
        try { QrLogin.key(JsonParser.parseString("""{"code":500,"msg":"连接失败"}""").asJsonObject); fail("Must reject failed key request") }
        catch (error: IllegalStateException) { assertEquals("连接失败", error.message) }
        try { QrLogin.key(JsonParser.parseString("""{"code":200,"data":null}""").asJsonObject); fail("Must reject absent key") }
        catch (error: IllegalStateException) { assertTrue(error.message!!.contains("key")) }
    }
}
