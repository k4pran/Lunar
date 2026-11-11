package com.ryanjames.lunar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.LibraryArrangement
import com.ryanjames.lunar.ui.design.LunarTestTags
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class KeyboardNavigationTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun keyboardOnlyPassThroughLibrarySetlistsSettingsAndViewer() {
        val runtime = createKeyboardTestRuntime()
        rule.setContent {
            Box(modifier = Modifier.requiredSize(width = 1280.dp, height = 900.dp)) {
                LunarApp(runtime)
            }
        }
        awaitLibrary()

        // Library: Tab reaches a score's quick action and Enter activates it.
        tabUntilFocused(hasContentDescription("Show info for Moon River"))
        pressKey(Key.Enter)
        rule.waitUntil(timeoutMillis = 5_000) { hasNode(hasText("Score info")) }
        tabUntilFocused(hasText("Close"))
        pressKey(Key.Enter)
        rule.waitUntil(timeoutMillis = 5_000) { !hasNode(hasText("Score info")) }

        // Setlists: the sidebar destinations are reachable and selectable from the keyboard.
        tabUntilFocused(hasTestTag(LunarTestTags.navigationItem(AppSection.SETLISTS)))
        pressKey(Key.Enter)
        rule.onNodeWithTag(LunarTestTags.navigationItem(AppSection.SETLISTS)).assertIsSelected()
        rule.waitUntil(timeoutMillis = 5_000) { hasNode(hasTestTag(LunarTestTags.setlistRow("set_morning"))) }
        tabUntilFocused(hasTestTag(LunarTestTags.setlistRow("set_morning")))

        // Settings and back to the library through the section navigation.
        tabUntilFocused(hasTestTag(LunarTestTags.navigationItem(AppSection.SETTINGS)))
        pressKey(Key.Enter)
        rule.waitUntil(timeoutMillis = 5_000) { hasNode(hasTestTag(LunarTestTags.SETTINGS_PANEL)) }
        rule.onNodeWithTag(LunarTestTags.navigationItem(AppSection.SETTINGS)).assertIsSelected()

        tabUntilFocused(hasTestTag(LunarTestTags.navigationItem(AppSection.LIBRARY)))
        pressKey(Key.Enter)
        rule.waitUntil(timeoutMillis = 5_000) { hasNode(hasTestTag(LunarTestTags.LIBRARY_PANEL)) }

        // Viewer: open a score from its catalogue card and reach the viewer toolbar.
        tabUntilFocused(hasTestTag(LunarTestTags.libraryRow("moon_river")))
        pressKey(Key.Enter)
        rule.waitUntil(timeoutMillis = 5_000) { hasNode(hasText("View Fullscreen")) }
        tabUntilFocused(hasText("View Fullscreen"))
    }

    @Test
    fun railWidthArrangementTabsAreKeyboardSelectableWithFullSizeTargets() {
        val runtime = createKeyboardTestRuntime()
        rule.setContent {
            Box(modifier = Modifier.requiredSize(width = 800.dp, height = 900.dp)) {
                LunarApp(runtime)
            }
        }
        awaitLibrary()

        LibraryArrangement.entries.forEach { arrangement ->
            val bounds = rule.onNodeWithTag(LunarTestTags.libraryArrangementTab(arrangement)).getBoundsInRoot()
            assertTrue(bounds.bottom - bounds.top >= 44.dp, "$arrangement is shorter than the touch target")
        }
        tabUntilFocused(hasTestTag(LunarTestTags.libraryArrangementTab(LibraryArrangement.FAVOURITES)))
        pressKey(Key.Enter)
        rule.onNodeWithTag(LunarTestTags.libraryArrangementTab(LibraryArrangement.FAVOURITES)).assertIsSelected()
    }

    @Test
    fun compactWidthKeepsNavigationAndCatalogueOnScreenWithFullSizeTargets() {
        val runtime = createKeyboardTestRuntime()
        rule.setContent {
            Box(modifier = Modifier.requiredSize(width = 390.dp, height = 800.dp)) {
                LunarApp(runtime)
            }
        }
        awaitLibrary()

        rule.onNodeWithTag(LunarTestTags.NAVIGATION).assertIsDisplayed()
        val row = rule.onNodeWithTag(LunarTestTags.libraryRow("moon_river")).getBoundsInRoot()
        assertTrue(row.right <= 390.dp, "Library card overflows the phone width")

        tabUntilFocused(hasContentDescription("Open navigation"))
        pressKey(Key.Enter)
        rule.waitUntil(timeoutMillis = 5_000) {
            hasNode(hasTestTag(LunarTestTags.navigationItem(AppSection.SETTINGS)))
        }
        AppSection.entries.forEach { section ->
            val bounds = rule.onNodeWithTag(LunarTestTags.navigationItem(section)).getBoundsInRoot()
            assertTrue(bounds.bottom - bounds.top >= 44.dp, "$section is shorter than the touch target")
            assertTrue(bounds.right <= 390.dp, "$section overflows the phone width")
        }

        tabUntilFocused(hasTestTag(LunarTestTags.navigationItem(AppSection.SETTINGS)))
        pressKey(Key.Enter)
        rule.waitUntil(timeoutMillis = 5_000) { hasNode(hasTestTag(LunarTestTags.SETTINGS_PANEL)) }
    }

    @Test
    fun narrowPhoneListFooterKeepsOpenButtonFullyVisible() {
        val runtime = createKeyboardTestRuntime()
        rule.setContent {
            Box(modifier = Modifier.requiredSize(width = 360.dp, height = 800.dp)) {
                LunarApp(runtime)
            }
        }
        awaitLibrary()

        val row = rule.onNodeWithTag(LunarTestTags.libraryRow("moon_river")).getBoundsInRoot()
        val open = rule.onNodeWithTag(LunarTestTags.libraryOpen("moon_river"))
        open.assertIsDisplayed()
        val bounds = open.getBoundsInRoot()
        assertTrue(bounds.right - bounds.left >= 58.dp, "Open button is squeezed below its minimum width")
        assertTrue(bounds.bottom - bounds.top >= 40.dp, "Open button is shorter than its minimum height")
        assertTrue(bounds.right <= row.right, "Open button overflows its library card")

        val favourite = rule.onNode(hasContentDescription("Mark Moon River as favorite")).getBoundsInRoot()
        assertTrue(favourite.bottom - favourite.top >= 44.dp, "Quick action is shorter than the touch target")
    }

    private fun awaitLibrary() {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(LunarTestTags.libraryRow("moon_river")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun hasNode(matcher: SemanticsMatcher): Boolean =
        rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /**
     * Sends a key to whatever holds focus, or to the root before anything is focused. Each window
     * keeps its own focused node, so prefer the last one: dialogs and popups sit above the app.
     */
    private fun pressKey(key: Key) {
        val target = if (hasNode(isFocused())) rule.onAllNodes(isFocused()).onLast() else rule.onRoot()
        target.performKeyInput { pressKey(key) }
        rule.waitForIdle()
    }

    private fun tabUntilFocused(matcher: SemanticsMatcher, maxPresses: Int = 120) {
        repeat(maxPresses) {
            if (hasNode(matcher and isFocused())) return
            pressKey(Key.Tab)
        }
        if (!hasNode(matcher and isFocused())) {
            fail("Tab never reached ${matcher.description} within $maxPresses presses")
        }
    }
}

private fun createKeyboardTestRuntime(): PlatformRuntime = runBlocking {
    val items = listOf(
        testSheetMusicItem(id = "moon_river", title = "Moon River", pageCount = 2),
        testSheetMusicItem(id = "clair_de_lune", title = "Clair de Lune", pageCount = 4),
    )
    createTestPlatformRuntime(
        initialItems = items,
        initialSetlists = listOf(
            testLibrarySetlist(id = "set_morning", name = "Morning rehearsal", itemIds = items.map { it.id }),
        ),
        initialSettings = AppSettings(refreshOnLaunch = false),
    )
}
