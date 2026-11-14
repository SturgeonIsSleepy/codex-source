package cp.player.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import cp.player.MainActivity
import cp.player.ui.theme.EPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RecommendationControlsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun singlePickUsesOneTapAndThereIsNoRedundantDefaultRoamButton() {
        var picker = 0; var chosen = 0
        val track = Track(cp.player.model.Song("fixture", "单曲夹具", "歌手", album = "专辑"), source = "fixture")
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            Column { QuickPickGrid(listOf(QuickPick("song:fixture", "单曲夹具", track = track)), { chosen++ }, { picker++ }) }
        } } }
        compose.onNodeWithText("单曲夹具").performClick()
        compose.onNodeWithText("漫游").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, picker); assertEquals(1, chosen) }
    }
    @Test fun recommendationSettingChangesOnlyTheEngineAndCanBeRestored() {
        val model = ViewModelProvider(compose.activity)[EPlayerViewModel::class.java]
        val original = model.recommendationEngine.value
        try {
            compose.runOnUiThread { model.setRecommendationEngine("netease"); compose.activity.setContent { EPlayerTheme { Settings(model) {} } } }
            compose.onNodeWithText("推荐").performScrollTo().performClick()
            compose.onNodeWithText("YTM").performClick()
            compose.runOnIdle {
                assertEquals("ytm", model.recommendationEngine.value)
                assertEquals("ytm", compose.activity.getSharedPreferences("eplayer_ui", 0).getString("recommendation_engine", null))
            }
        } finally { compose.runOnUiThread { model.setRecommendationEngine(original) } }
    }
}
