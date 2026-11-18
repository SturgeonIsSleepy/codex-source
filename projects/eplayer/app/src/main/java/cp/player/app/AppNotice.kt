package cp.player.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast

/** System toast crosses Dialog windows without overlay permission or a background service. */
internal object AppNotice {
    private val main = Handler(Looper.getMainLooper())
    private var last = ""
    private var at = 0L
    private var toast: Toast? = null
    fun show(context: Context, text: String) {
        val app = context.applicationContext
        val action = Runnable {
            val now = SystemClock.uptimeMillis()
            if (text.isNotBlank() && (text != last || now - at > 1500)) {
                last = text; at = now
                toast?.cancel()
                toast = Toast.makeText(app, text, Toast.LENGTH_SHORT).also { it.show() }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) action.run() else main.post(action)
    }
}
