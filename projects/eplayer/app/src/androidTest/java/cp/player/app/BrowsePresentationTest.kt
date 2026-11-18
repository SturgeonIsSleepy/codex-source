package cp.player.app

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cp.player.MainActivity
import cp.player.ui.theme.EPlayerTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrowsePresentationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val picks = (0 until 9).map { QuickPick("artist:$it", "测试歌手 $it", entry = MusicEntry("$it", "测试歌手 $it", "fixture", MusicKind.ARTIST)) }
    private fun home(height: Int, fontScale: Float) {
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.width(340.dp).height(height.dp)) { HomePanel(ListeningStats(), picks, {}, {}, Modifier.fillMaxSize()) }
            }
        } } }
        compose.waitForIdle()
        val heading = compose.onNodeWithTag("quick-pick-heading").getUnclippedBoundsInRoot()
        val filters = compose.onNodeWithTag("quick-pick-filters").getUnclippedBoundsInRoot()
        val cards = compose.onNodeWithTag("quick-pick-cards").getUnclippedBoundsInRoot()
        assertTrue("Heading must be above filters", heading.bottom <= filters.top)
        assertTrue("Filters must be above cards", filters.bottom <= cards.top)
        val nodes = compose.onAllNodes(hasTestTagPrefix("quick-pick-artist:")).fetchSemanticsNodes()
        assertTrue("A usable row of cards must remain", nodes.size >= 3)
        for ((i, first) in nodes.withIndex()) for (second in nodes.drop(i + 1)) {
            assertFalse("Cards must never overlap", first.boundsInRoot.overlaps(second.boundsInRoot))
        }
    }
    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("test tag starts with $prefix") {
        it.config.getOrElse(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { "" }.startsWith(prefix)
    }
    @Test fun homeSectionsAndCardsNeverOverlap() = home(620, 1f)
    @Test fun compactHomeWithLargeFontKeepsSeparateSections() = home(390, 1.6f)
    @Test fun artistChoicesHaveSeparateAvatarsAndComfortableTouchRows() {
        var selected: String? = null
        compose.runOnUiThread { compose.activity.setContent { EPlayerTheme {
            Column(Modifier.width(340.dp)) {
                for (i in 0..1) ArtistChoiceRow(MusicEntry("$i", "测试歌手 $i", "fixture", MusicKind.ARTIST)) { selected = "$i" }
            }
        } } }
        val first = compose.onNodeWithTag("artist-choice-0").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("artist-choice-1").getUnclippedBoundsInRoot()
        assertTrue(first.bottom - first.top >= 80.dp)
        assertTrue(first.bottom <= second.top)
        compose.onNodeWithTag("artist-choice-avatar-0", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("artist-choice-avatar-1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("artist-choice-1").performClick()
        assertEquals("1", selected)
    }
    @Test fun artistInformationAppearsBeforeTheWorksTabsOnTheActualDetailPage() {
        lateinit var cloud: CloudViewModel
        lateinit var player: EPlayerViewModel
        compose.runOnUiThread {
            cloud = androidx.lifecycle.ViewModelProvider(compose.activity)[CloudViewModel::class.java]
            player = androidx.lifecycle.ViewModelProvider(compose.activity)[EPlayerViewModel::class.java]
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            val artist = MusicEntry("fixture-artist", "测试歌手", "fixture", MusicKind.ARTIST)
            cloud.pages.value = listOf(DetailPage(artist, MusicDetail(artist, description = "测试歌手资料"), loading = false))
            compose.activity.setContent { EPlayerTheme {
                CloudDetailScreen(cloud, player, {}, songOptions = { _, _ -> })
            } }
        }
        compose.onNodeWithTag("artist-profile-header").assertIsDisplayed()
        compose.onNodeWithTag("artist-header-avatar", useUnmergedTree = true).assertExists()
        val header = compose.onNodeWithTag("artist-profile-header").getUnclippedBoundsInRoot()
        val tabs = compose.onNodeWithTag("music-segment-歌曲").getUnclippedBoundsInRoot()
        assertTrue("Artist information must precede works controls", header.bottom <= tabs.top)
    }

}
