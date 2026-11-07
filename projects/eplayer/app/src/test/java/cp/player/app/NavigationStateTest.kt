package cp.player.app

import org.junit.Assert.*
import org.junit.Test

class NavigationStateTest {
    private val target = MusicEntry("new", "新发行", "source", MusicKind.BROWSE)
    @Test fun openingTheSameListAgainCreatesAnIndependentScene() {
        assertNotEquals(DetailPage(target).instanceId, DetailPage(target).instanceId)
    }
    @Test fun networkUpdatesKeepTheScenesScrollIdentity() {
        val page = DetailPage(target)
        assertEquals(page.instanceId, page.copy(loading = false, error = "offline").instanceId)
    }
    @Test fun settingsBackRestoresTheParentInsteadOfClosingEverything() {
        val path = openSettingsPath(openSettingsPath(openSettingsPath(emptyList(), "profile"), "settings"), "source")
        assertEquals(listOf("profile", "settings"), previousSettingsPath(path))
        assertEquals(listOf("profile"), previousSettingsPath(previousSettingsPath(path)))
    }
    @Test fun doubleTapsDoNotCreateAnExtraBackStep() {
        assertEquals(listOf("profile", "settings"), openSettingsPath(listOf("profile", "settings"), "settings"))
        assertEquals(emptyList<String>(), previousSettingsPath(emptyList()))
    }
}
