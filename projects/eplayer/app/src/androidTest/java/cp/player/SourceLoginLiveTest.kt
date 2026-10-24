package cp.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.app.QrLogin
import cp.player.app.SourceStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in handshake only. Never scans, confirms login, saves cookies or prints login keys. */
@RunWith(AndroidJUnit4::class)
class SourceLoginLiveTest {
    @Test fun officialModuleCanCreateLocallyRenderedLoginCode() = runBlocking {
        assumeTrue(BuildConfig.INTERNAL_BUILD && InstrumentationRegistry.getArguments().getString("liveNetwork") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = "netease-cloudmusic-rs"
        val key = QrLogin.key(SourceStore.call(context, source, "login/qr/key"))
        assertTrue(key.isNotBlank())
        val url = QrLogin.url(SourceStore.call(context, source, "login/qr/create", mapOf("key" to key)))
        val bitmap = QrLogin.bitmap(url)
        assertEquals(512, bitmap.width)
        bitmap.recycle()
        val code = SourceStore.call(context, source, "login/qr/check", mapOf("key" to key)).get("code")?.asInt
        assertTrue("QR status must be a supported scan state", code in setOf(800, 801, 802, 803))
        android.util.Log.i("ePlayerLoginCheck", "Official key, URL, local QR and scan-status handshake passed")
    }
}
