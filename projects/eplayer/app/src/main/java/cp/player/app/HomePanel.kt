package cp.player.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** The home canvas has no vertical scrolling parent. Card height follows the measured free area. */
@Composable
internal fun HomePanel(stats: ListeningStats, picks: List<QuickPick>, choose: (QuickPick) -> Unit,
    openPicker: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.testTag("home-static")) {
        val short = maxHeight < 430.dp
        val count = if (short) 6 else 9
        Column(Modifier.fillMaxSize().clipToBounds()) {
            ListeningSummary(stats, compact = true, tiny = short)
            Spacer(Modifier.height(8.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val rows = count / 3
                val cell = minOf((maxWidth - 16.dp) / 3, ((maxHeight - 110.dp - 8.dp * rows) / rows).coerceAtLeast(32.dp))
                QuickPickGrid(picks, choose, openPicker, cellHeight = cell, maxItems = count)
            }
        }
    }
}
