package cp.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cp.player.app.EPlayerApp
import cp.player.app.EPlayerViewModel
import cp.player.ui.theme.EPlayerTheme

class MainActivity : ComponentActivity() {
    private val player: EPlayerViewModel by viewModels()
    private val cloud: cp.player.app.CloudViewModel by viewModels()
    private var showPlayer by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showPlayer = intent.action == "ACTION_SHOW_PLAYER"
        enableEdgeToEdge()
        setContent {
            val appearance by player.appearance.collectAsState()
            val dark = when (appearance) { "light" -> false; "system" -> androidx.compose.foundation.isSystemInDarkTheme(); else -> true }
            EPlayerTheme(darkTheme = dark) { EPlayerApp(player, cloud, showPlayer) { showPlayer = it } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == "ACTION_SHOW_PLAYER") showPlayer = true
    }
}
