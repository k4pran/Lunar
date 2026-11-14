package com.ryanjames.lunar

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.design.LunarTestTags
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class QuickFindPaletteTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun ctrlKOpensPaletteAndTypingFiltersResults() {
        startApp()
        openPalette()

        rule.onNodeWithTag(LunarTestTags.QUICK_FIND_PANEL).assertIsDisplayed()
        rule.onNodeWithTag(LunarTestTags.quickFindResult("score_moon_river")).assertIsDisplayed()
        rule.onNodeWithTag(LunarTestTags.quickFindResult("score_blue_bossa")).assertIsDisplayed()
        rule.onNodeWithTag(LunarTestTags.quickFindResult("action_switch_skin")).assertIsDisplayed()

        rule.onNodeWithTag(LunarTestTags.QUICK_FIND_INPUT).performTextInput("bossa")

        rule.onNodeWithTag(LunarTestTags.quickFindResult("score_blue_bossa")).assertIsDisplayed()
        assertGone(LunarTestTags.quickFindResult("score_moon_river"))
        assertGone(LunarTestTags.quickFindResult("action_switch_skin"))
    }

    @Test
    fun enterOpensTheHighlightedScoreInTheViewer() {
        startApp()
        openPalette()

        rule.onNodeWithTag(LunarTestTags.QUICK_FIND_INPUT).performTextInput("moon")
        rule.onNodeWithTag(LunarTestTags.quickFindResult("score_moon_river")).assertIsSelected()
        rule.onNodeWithTag(LunarTestTags.QUICK_FIND_INPUT).performKeyInput { pressKey(Key.Enter) }

        assertGone(LunarTestTags.QUICK_FIND_PANEL)
        rule.waitUntil(timeoutMillis = 5_000) {
            // The viewer replaces the library screen.
            rule.onAllNodesWithTag(LunarTestTags.LIBRARY_PANEL).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun arrowKeysMoveTheHighlight() {
        startApp()
        openPalette()

        rule.onNodeWithTag(LunarTestTags.QUICK_FIND_INPUT).performTextInput("o")
        rule.onNodeWithTag(LunarTestTags.quickFindResult("score_blue_bossa")).assertIsSelected()
        rule.onNodeWithTag(LunarTestTags.QUICK_FIND_INPUT).performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(LunarTestTags.quickFindResult("score_moon_river")).assertIsSelected()
        rule.onNodeWithTag(LunarTestTags.QUICK_FIND_INPUT).performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithTag(LunarTestTags.quickFindResult("score_blue_bossa")).assertIsSelected()
    }

    @Test
    fun escapeClosesThePaletteWithoutSideEffects() {
        startApp()
        openPalette()

        rule.onNodeWithTag(LunarTestTags.QUICK_FIND_INPUT).performTextInput("moon")
        rule.onNodeWithTag(LunarTestTags.QUICK_FIND_INPUT).performKeyInput { pressKey(Key.Escape) }

        assertGone(LunarTestTags.QUICK_FIND_PANEL)
        rule.onNodeWithTag(LunarTestTags.LIBRARY_PANEL).assertIsDisplayed()
        rule.onNodeWithTag(LunarTestTags.libraryRow("moon_river")).assertIsDisplayed()
    }

    @Test
    fun ctrlKWorksFromOtherSections() {
        startApp()
        rule.onNodeWithTag(LunarTestTags.navigationItem(AppSection.SETTINGS)).performClick()
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(LunarTestTags.SETTINGS_PANEL).fetchSemanticsNodes().isNotEmpty()
        }

        openPalette()

        rule.onNodeWithTag(LunarTestTags.QUICK_FIND_PANEL).assertIsDisplayed()
    }

    private fun startApp() {
        val runtime = runBlocking {
            createTestPlatformRuntime(
                initialItems = listOf(
                    testSheetMusicItem(id = "moon_river", title = "Moon River"),
                    testSheetMusicItem(id = "blue_bossa", title = "Blue Bossa"),
                ),
                initialSettings = AppSettings(refreshOnLaunch = false),
            )
        }
        rule.setContent {
            LunarApp(runtime)
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(LunarTestTags.libraryRow("moon_river")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openPalette() {
        rule.waitForIdle()
        rule.onRoot().performKeyInput {
            withKeyDown(Key.CtrlLeft) { pressKey(Key.K) }
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(LunarTestTags.QUICK_FIND_PANEL).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertGone(tag: String) {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }
}
