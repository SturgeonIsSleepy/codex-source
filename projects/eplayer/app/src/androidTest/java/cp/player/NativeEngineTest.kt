package cp.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cp.player.engine.RustEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeEngineTest {
    @Test fun nativeLibraryInitializesWithoutUsbOwnership() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("The packaged native audio engine must initialize on the device ABI", RustEngine.initEngine(context))
        try {
            assertNotEquals("Uninitialized", RustEngine.getState())
            assertFalse("ePlayer must never create an exclusive USB session", RustEngine.isRustDirectUsbSessionActive())
        } finally { RustEngine.stopEngine() }
    }
}
