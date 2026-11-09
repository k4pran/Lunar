package com.ryanjames.lunar

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import com.ryanjames.lunar.ui.LibraryTestTags
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.ryanjames.lunar.composition.InMemoryCompositionDraftStore
import com.ryanjames.lunar.library.data.CloudGoogleDriveSource
import com.ryanjames.lunar.library.data.CloudPathStrategy
import com.ryanjames.lunar.library.data.GoogleDriveImportRoot
import com.ryanjames.lunar.library.data.GoogleDriveStorageSettings
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.data.SheetMusicRepository
import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import com.ryanjames.lunar.library.model.ScoreMetadata
import com.ryanjames.lunar.library.model.ScoreMetadataComposer
import com.ryanjames.lunar.library.model.ScoreMetadataSource
import com.ryanjames.lunar.platform.CompositionPdfImportRequest
import com.ryanjames.lunar.platform.CompositionPdfImporter
import com.ryanjames.lunar.platform.ImporterState
import com.ryanjames.lunar.platform.LibraryCacheSnapshot
import com.ryanjames.lunar.platform.LilyPondLiveRenderResult
import com.ryanjames.lunar.platform.LilyPondLiveRenderer
import com.ryanjames.lunar.platform.LilyPondSourceSnapshot
import com.ryanjames.lunar.platform.PdfDocumentInfo
import com.ryanjames.lunar.platform.PdfPageRenderer
import com.ryanjames.lunar.platform.RenderedPdfPage
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.AppSettingsStore
import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.settings.JsonAppSettingsStore
import com.ryanjames.lunar.settings.ViewerKeybindings
import com.ryanjames.lunar.sync.CloudSyncState
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.ComposeScreen
import com.ryanjames.lunar.ui.ImportScreen
import com.ryanjames.lunar.ui.LibraryBrowseMode
import com.ryanjames.lunar.ui.LibraryScreen
import com.ryanjames.lunar.ui.LunarTheme
import com.ryanjames.lunar.ui.LunarTooltip
import com.ryanjames.lunar.ui.SettingsScreen
import com.ryanjames.lunar.ui.ViewerTarget
import com.ryanjames.lunar.ui.design.LunarTestTags
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.ryanjames.lunar.ui.shell.LunarAppShell
import com.ryanjames.lunar.ui.shell.SidebarDrawerTag
import com.ryanjames.lunar.ui.shell.SidebarPrimaryDestinations
import com.ryanjames.lunar.ui.shell.sidebarNavTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Rule
import org.junit.Test

class ScreenSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun appShowsStartupLoadingUntilLocalRuntimeIsReady() {
        runBlocking {
            val settingsGate = CompletableDeferred<Unit>()
            val repositoryGate = CompletableDeferred<Unit>()
            val loadedSettings = AppSettings(
                skin = AppSkin.MOONLIGHT,
                refreshOnLaunch = false,
            )
            val baseRuntime = createTestPlatformRuntime(
                initialItems = listOf(testSheetMusicItem(id = "moon_river", title = "Moon River")),
            )
            val runtime = baseRuntime.copy(
                repository = DelayedSheetMusicRepository(
                    delegate = baseRuntime.repository,
                    initializeGate = repositoryGate,
                ),
                settingsStore = DelayedAppSettingsStore(
                    loadedSettings = loadedSettings,
                    initializeGate = settingsGate,
                ),
            )

            rule.setContent {
                LunarApp(runtime)
            }

            rule.onNodeWithTag(LunarTestTags.STARTUP_SCREEN).assertIsDisplayed()
            rule.onNodeWithContentDescription("Lunar is starting").assertIsDisplayed()
            assertTrue(rule.onAllNodesWithTag(LunarTestTags.libraryRow("moon_river")).fetchSemanticsNodes().isEmpty())

            settingsGate.complete(Unit)
            rule.waitForIdle()
            rule.onNodeWithTag(LunarTestTags.STARTUP_SCREEN).assertIsDisplayed()
            assertTrue(rule.onAllNodesWithTag(LunarTestTags.libraryRow("moon_river")).fetchSemanticsNodes().isEmpty())

            repositoryGate.complete(Unit)
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag(LunarTestTags.libraryRow("moon_river")).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(rule.onAllNodesWithTag(LunarTestTags.STARTUP_SCREEN).fetchSemanticsNodes().isEmpty())
            rule.onNodeWithTag(LunarTestTags.libraryRow("moon_river")).assertIsDisplayed()
            rule.onNodeWithTag(LunarTestTags.NAVIGATION).assertIsDisplayed()
        }
    }

    @Test
    fun lunarTooltipShowsFromLongClickSemantics() {
        rule.setContent {
            LunarTheme(skin = AppSkin.PAPER) {
                LunarTooltip("Open this score") {
                    Button(onClick = {}) {
                        Text("Open")
                    }
                }
            }
        }

        rule.onNodeWithText("Open").performSemanticsAction(SemanticsActions.OnLongClick)
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText("Open this score").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun libraryScreenDoesNotOpenRandomScoreWhileTypingInSearch() {
        runBlocking {
            val item = testSheetMusicItem(
                id = "clair_de_lune",
                title = "Clair de Lune",
            )
            val runtime = createTestPlatformRuntime(initialItems = listOf(item))
            val appState = createTestLunarAppState(
                scope = backgroundScope(),
                runtime = runtime,
            )

            rule.setContent {
                LunarTheme(skin = AppSkin.MOONLIGHT) {
                    LibraryScreen(
                        runtime = runtime,
                        snapshot = runtime.repository.library.value,
                        appState = appState,
                        viewerKeybindings = ViewerKeybindings(openRandomScore = "R"),
                    )
                }
            }

            rule.onNodeWithTag(LunarTestTags.LIBRARY_SEARCH).performClick()
            rule.onNodeWithTag(LunarTestTags.LIBRARY_SEARCH).performKeyInput {
                pressKey(Key.R)
            }

            rule.runOnIdle {
                assertEquals(null, appState.fullscreenTarget)
            }
            rule.onNodeWithTag(LunarTestTags.LIBRARY_SEARCH).performTextReplacement("R")
            rule.runOnIdle {
                assertEquals("R", appState.query.searchText)
                assertEquals(null, appState.fullscreenTarget)
            }
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun libraryToolbarOpensRandomScoreSortsAndSwitchesToGallery() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "moon_river", title = "Moon River", collection = "Standards", pageCount = 2),
                testSheetMusicItem(id = "clair_de_lune", title = "Clair de Lune", collection = "Recital", pageCount = 4),
            )
            val setlist = testLibrarySetlist(
                id = "set_morning",
                name = "Morning rehearsal",
                itemIds = items.map { it.id },
                updatedAtEpochMillis = 10L,
            )
            val runtime = createTestPlatformRuntime(initialItems = items, initialSetlists = listOf(setlist))
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)

            rule.setContent {
                val snapshot by runtime.repository.library.collectAsState()
                LunarTheme(skin = AppSkin.MOONLIGHT) {
                    LibraryScreen(
                        runtime = runtime,
                        snapshot = snapshot,
                        appState = appState,
                        viewerKeybindings = ViewerKeybindings(openRandomScore = "R"),
                    )
                }
            }

            rule.onNodeWithText("Open a random score").assertIsDisplayed()
            rule.onRoot().performKeyInput { pressKey(Key.R) }
            rule.waitUntil(timeoutMillis = 5_000) {
                appState.fullscreenTarget is ViewerTarget.Score
            }
            rule.runOnIdle { appState.closeFullscreen() }

            rule.onNodeWithContentDescription("Sort by Date added").performClick()
            rule.onNodeWithText("Ascending").performClick()
            rule.runOnIdle {
                assertEquals(com.ryanjames.lunar.library.model.SortDirection.ASCENDING, appState.query.sortDirection)
            }
            rule.onNodeWithContentDescription("Sort by Date added").performClick()
            rule.onNodeWithText("Title").performClick()
            rule.runOnIdle {
                assertEquals(com.ryanjames.lunar.library.model.LibrarySortOption.TITLE, appState.query.sortOption)
            }
            assertTrue(rule.onAllNodes(isPopup()).fetchSemanticsNodes().isEmpty())

            rule.onNodeWithContentDescription("Show as gallery").performClick()
            rule.runOnIdle {
                assertEquals(com.ryanjames.lunar.ui.LibraryLayoutMode.GRID, appState.layoutMode)
            }
            rule.onNodeWithContentDescription("Show as list").performClick()

            rule.runOnIdle { appState.selectSection(AppSection.SETLISTS) }
            rule.onNodeWithText("Random setlist").performClick()
            rule.onNodeWithText("Create a random setlist").assertIsDisplayed()
            rule.onNodeWithText("Cancel").performClick()
            rule.onNodeWithText("Morning rehearsal").assertIsDisplayed()
            rule.onNodeWithText("Open").performClick()
            rule.onNodeWithText("Saved automatically. Delete it whenever you no longer need it.").assertIsDisplayed()
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun focusingARowShowsTheDetailPanelAndOpenGoesStraightToTheViewer() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "moon_river", title = "Moon River", collection = "Standards", pageCount = 6)
                    .copy(lastViewedPage = 2, lastOpenedEpochMillis = 1L),
                testSheetMusicItem(id = "clair_de_lune", title = "Clair de Lune", pageCount = 4),
            )
            val runtime = createTestPlatformRuntime(
                initialItems = items,
                pdfExporter = TestPdfDocumentExporter { _, suggestedFileName -> "Downloads/$suggestedFileName" },
                scoreDownloadSupported = true,
            )
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)

            rule.setContent {
                val snapshot by runtime.repository.library.collectAsState()
                LunarTheme(skin = AppSkin.PAPER) {
                    LibraryScreen(runtime = runtime, snapshot = snapshot, appState = appState)
                }
            }

            rule.onNodeWithTag(LibraryTestTags.JumpBackIn).assertIsDisplayed()
            assertTrue(rule.onAllNodesWithTag(LibraryTestTags.DetailPanel).fetchSemanticsNodes().isEmpty())

            rule.onNodeWithTag(LibraryTestTags.row("moon_river")).performClick()
            rule.runOnIdle { assertEquals("moon_river", appState.focusedScoreId) }
            rule.onNodeWithTag(LibraryTestTags.DetailTitle, useUnmergedTree = true).assertTextEquals("Moon River")
            rule.onNodeWithText("Last on page 3 of 6", substring = true).assertExists()
            rule.onNodeWithText("From the top").assertExists()

            rule.onNodeWithContentDescription("Download Moon River").performScrollTo().performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                appState.bannerMessage == "Saved \"Moon River\" to Downloads/Moon River.pdf."
            }
            rule.onNodeWithContentDescription("Show info for Moon River").performScrollTo().performClick()
            rule.onNodeWithText("Filename").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Moon River.pdf").assertExists()

            rule.onNodeWithText("Open at page 3").performScrollTo().performClick()
            rule.runOnIdle {
                assertEquals(ViewerTarget.Score("moon_river"), appState.fullscreenTarget)
                assertEquals(2, appState.viewerStartPage)
                appState.closeFullscreen()
            }

            rule.onNodeWithTag(LibraryTestTags.row("clair_de_lune")).performTouchInput { doubleClick() }
            rule.runOnIdle {
                assertEquals(ViewerTarget.Score("clair_de_lune"), appState.fullscreenTarget)
                appState.closeFullscreen()
            }

            rule.onNodeWithTag(LibraryTestTags.row("moon_river")).performClick()
            rule.onRoot().performKeyInput { pressKey(Key.Enter) }
            rule.runOnIdle {
                assertEquals(ViewerTarget.Score("moon_river"), appState.fullscreenTarget)
            }
        }
    }

    @Test
    fun detailPanelEditsDetailsInlineAndHidesTheScore() {
        runBlocking {
            val item = testSheetMusicItem(id = "moon_river", title = "Moon River", composer = "Mancini")
            val runtime = createTestPlatformRuntime(initialItems = listOf(item))
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)

            rule.setContent {
                val snapshot by runtime.repository.library.collectAsState()
                LunarTheme(skin = AppSkin.PAPER) {
                    LibraryScreen(runtime = runtime, snapshot = snapshot, appState = appState)
                }
            }

            rule.onNodeWithTag(LibraryTestTags.row("moon_river")).performClick()
            rule.onNodeWithContentDescription("Edit Moon River").performScrollTo().performClick()
            rule.onNode(hasSetTextAction() and hasText("Mancini")).performScrollTo().performTextReplacement("Henry Mancini")
            rule.onNodeWithText("Save").performScrollTo().performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.library.value.items.single().composer == "Henry Mancini"
            }
            rule.runOnIdle { assertEquals(null, appState.editingItemId) }

            rule.onNodeWithContentDescription("Hide Moon River").performScrollTo().performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.library.value.items.single().isHidden
            }
            rule.runOnIdle { assertEquals(null, appState.focusedScoreId) }

            rule.onNodeWithText("Hidden").performClick()
            rule.onNodeWithTag(LibraryTestTags.row("moon_river")).performClick()
            rule.onNodeWithContentDescription("Restore Moon River").performScrollTo().performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                !runtime.repository.library.value.items.single().isHidden
            }
        }
    }

    @Test
    fun multiSelectAddsScoresToASetlistFromThePopover() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "moon_river", title = "Moon River"),
                testSheetMusicItem(id = "clair_de_lune", title = "Clair de Lune"),
            )
            val setlist = testLibrarySetlist(id = "set_morning", name = "Morning rehearsal", itemIds = emptyList())
            val runtime = createTestPlatformRuntime(initialItems = items, initialSetlists = listOf(setlist))
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)

            rule.setContent {
                val snapshot by runtime.repository.library.collectAsState()
                LunarTheme(skin = AppSkin.PAPER) {
                    LibraryScreen(runtime = runtime, snapshot = snapshot, appState = appState)
                }
            }

            rule.onNodeWithContentDescription("Select Moon River").performClick()
            rule.onNodeWithContentDescription("Select Clair de Lune").performClick()
            rule.onNodeWithTag(LibraryTestTags.SelectionBar).assertIsDisplayed()
            rule.onNodeWithText("2 scores selected").assertIsDisplayed()
            assertTrue(rule.onAllNodesWithTag(LibraryTestTags.Toolbar).fetchSemanticsNodes().isEmpty())

            rule.onNodeWithText("Add to setlist").performClick()
            rule.onNodeWithTag(LibraryTestTags.SetlistPopover).assertExists()
            rule.onNodeWithText("Morning rehearsal").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.library.value.setlists.single().itemIds.toSet() == items.map { it.id }.toSet()
            }

            rule.onNodeWithContentDescription("New setlist...").performTextInput("Evening")
            rule.onNodeWithText("Create").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.library.value.setlists.any { it.name == "Evening" && it.itemIds.size == 2 }
            }

            rule.runOnIdle { appState.dismissSetlistPicker() }
            rule.onNodeWithText("Clear").performClick()
            rule.runOnIdle { assertTrue(appState.selectedScoreIds.isEmpty()) }
            rule.onNodeWithTag(LibraryTestTags.Toolbar).assertIsDisplayed()
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun searchShortcutClearsTheSelectionAndFocusesTheSearchField() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "moon_river", title = "Moon River"),
                testSheetMusicItem(id = "clair_de_lune", title = "Clair de Lune"),
            )
            val runtime = createTestPlatformRuntime(initialItems = items)
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)

            rule.setContent {
                val snapshot by runtime.repository.library.collectAsState()
                LunarTheme(theme = AppColorTheme.OCEAN) {
                    LibraryScreen(runtime = runtime, snapshot = snapshot, appState = appState)
                }
            }

            rule.onNodeWithContentDescription("Select Moon River").performClick()
            rule.onNodeWithTag(LibraryTestTags.SelectionBar).assertIsDisplayed()

            rule.onRoot().performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.K)
                keyUp(Key.CtrlLeft)
            }

            rule.runOnIdle { assertTrue(appState.selectedScoreIds.isEmpty()) }
            rule.onNodeWithTag(LibraryTestTags.Toolbar).assertIsDisplayed()
            // The only text field on screen is the library search.
            rule.onNode(hasSetTextAction() and isFocused()).assertExists()
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun groupedListNavigatesAndRangeSelectsInDisplayedOrder() {
        runBlocking {
            // Sorted by title the order is Alpha, Beta, Gamma; grouped by composer it reads Beta | Alpha, Gamma.
            val items = listOf(
                testSheetMusicItem(id = "alpha", title = "Alpha", composer = "Zed"),
                testSheetMusicItem(id = "beta", title = "Beta", composer = "Abe"),
                testSheetMusicItem(id = "gamma", title = "Gamma", composer = "Zed"),
            )
            val runtime = createTestPlatformRuntime(initialItems = items)
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.updateBrowseMode(com.ryanjames.lunar.ui.LibraryBrowseMode.BY_COMPOSER)
            appState.updateSortOption(com.ryanjames.lunar.library.model.LibrarySortOption.TITLE)
            appState.updateSortDirection(com.ryanjames.lunar.library.model.SortDirection.ASCENDING)

            rule.setContent {
                val snapshot by runtime.repository.library.collectAsState()
                LunarTheme(theme = AppColorTheme.OCEAN) {
                    LibraryScreen(runtime = runtime, snapshot = snapshot, appState = appState)
                }
            }

            rule.onNodeWithTag(LibraryTestTags.row("beta")).performClick()
            rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            rule.runOnIdle { assertEquals("alpha", appState.focusedScoreId) }
            rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            rule.runOnIdle { assertEquals("gamma", appState.focusedScoreId) }

            rule.onNodeWithTag(LibraryTestTags.row("alpha")).performClick()
            rule.onRoot().performKeyInput { keyDown(Key.ShiftLeft) }
            rule.onNodeWithTag(LibraryTestTags.row("gamma")).performClick()
            rule.onRoot().performKeyInput { keyUp(Key.ShiftLeft) }
            rule.runOnIdle { assertEquals(setOf("alpha", "gamma"), appState.selectedScoreIds) }
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun keyboardNavigationScrollsTheFocusedRowIntoView() {
        runBlocking {
            val composers = listOf("Albeniz", "Bach", "Chopin", "Debussy", "Elgar", "Faure", "Grieg", "Haydn", "Ives", "Liszt")
            val items = composers.flatMap { composer ->
                (1..4).map { index -> testSheetMusicItem(id = "${composer}_$index", title = "$composer piece $index", composer = composer) }
            }
            val runtime = createTestPlatformRuntime(initialItems = items)
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.updateBrowseMode(com.ryanjames.lunar.ui.LibraryBrowseMode.BY_COMPOSER)
            appState.updateSortOption(com.ryanjames.lunar.library.model.LibrarySortOption.TITLE)
            appState.updateSortDirection(com.ryanjames.lunar.library.model.SortDirection.ASCENDING)

            rule.setContent {
                val snapshot by runtime.repository.library.collectAsState()
                LunarTheme(theme = AppColorTheme.OCEAN) {
                    LibraryScreen(runtime = runtime, snapshot = snapshot, appState = appState)
                }
            }

            assertTrue(rule.onAllNodesWithTag(LibraryTestTags.row("Liszt_4")).fetchSemanticsNodes().isEmpty())
            rule.onNodeWithTag(LibraryTestTags.row("Albeniz_1")).performClick()
            repeat(items.size - 1) {
                rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                rule.waitForIdle()
            }
            rule.runOnIdle { assertEquals("Liszt_4", appState.focusedScoreId) }
            rule.onNodeWithTag(LibraryTestTags.row("Liszt_4")).assertIsDisplayed()

            repeat(items.size - 1) {
                rule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
                rule.waitForIdle()
            }
            rule.runOnIdle { assertEquals("Albeniz_1", appState.focusedScoreId) }
            rule.onNodeWithTag(LibraryTestTags.row("Albeniz_1")).assertIsDisplayed()
            rule.onNodeWithText("ALBENIZ").assertIsDisplayed()
        }
    }

    @Test
    fun viewerPickerFromAFilteredSetlistStillShowsItsTarget() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "moon_river", title = "Moon River", originalFileName = "Moon River.pdf"),
                testSheetMusicItem(id = "prelude", title = "Prelude", originalFileName = "Prelude.ly"),
            )
            val morning = testLibrarySetlist(id = "set_morning", name = "Morning rehearsal", itemIds = listOf("moon_river"))
            val evening = testLibrarySetlist(id = "set_evening", name = "Evening recital", itemIds = emptyList())
            val runtime = createTestPlatformRuntime(initialItems = items, initialSetlists = listOf(morning, evening))
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.updateViewerSupportFilter(com.ryanjames.lunar.library.model.ViewerSupportFilter.LILYPOND)
            appState.selectSection(com.ryanjames.lunar.ui.AppSection.SETLISTS)
            appState.openSetlist("set_morning")

            rule.setContent {
                val snapshot by runtime.repository.library.collectAsState()
                LunarTheme(theme = AppColorTheme.OCEAN) {
                    LibraryScreen(runtime = runtime, snapshot = snapshot, appState = appState)
                }
            }

            rule.runOnIdle {
                appState.openScore(runtime.repository.getItem("moon_river")!!)
                appState.showSetlistPickerForScore("moon_river")
            }
            rule.runOnIdle {
                assertEquals(com.ryanjames.lunar.ui.AppSection.SETLISTS, appState.selectedSection)
                assertEquals("moon_river", appState.focusedScoreId)
                assertTrue(appState.setlistPickerVisible)
            }
            rule.onNodeWithTag(LibraryTestTags.DetailTitle, useUnmergedTree = true).assertTextEquals("Moon River")
            rule.onNodeWithText("Evening recital").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.library.value.setlists.single { it.id == "set_evening" }.itemIds == listOf("moon_river")
            }
        }
    }

    @Test
    fun setlistDetailKeepsBulkSelectionActions() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "moon_river", title = "Moon River"),
                testSheetMusicItem(id = "clair_de_lune", title = "Clair de Lune"),
            )
            val morning = testLibrarySetlist(id = "set_morning", name = "Morning rehearsal", itemIds = items.map { it.id })
            val evening = testLibrarySetlist(id = "set_evening", name = "Evening recital", itemIds = emptyList())
            val runtime = createTestPlatformRuntime(initialItems = items, initialSetlists = listOf(morning, evening))
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.selectSection(com.ryanjames.lunar.ui.AppSection.SETLISTS)
            appState.openSetlist("set_morning")

            rule.setContent {
                val snapshot by runtime.repository.library.collectAsState()
                LunarTheme(theme = AppColorTheme.OCEAN) {
                    LibraryScreen(runtime = runtime, snapshot = snapshot, appState = appState)
                }
            }

            rule.onNodeWithContentDescription("Select Moon River").performClick()
            rule.onNodeWithTag(LibraryTestTags.SelectionBar).assertIsDisplayed()
            rule.onNodeWithText("Add to setlist").performClick()
            rule.onNodeWithText("Evening recital").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.library.value.setlists.single { it.id == "set_evening" }.itemIds == listOf("moon_river")
            }
        }
    }

    @Test
    fun filterPopoverNarrowsTheListAndChipsRemoveFilters() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "moon_river", title = "Moon River", pageCount = 2, originalFileName = "Moon River.pdf"),
                testSheetMusicItem(id = "prelude", title = "Prelude", composer = "Bach", pageCount = 1, originalFileName = "Prelude.ly"),
            )
            val runtime = createTestPlatformRuntime(initialItems = items)
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)

            rule.setContent {
                val snapshot by runtime.repository.library.collectAsState()
                LunarTheme(skin = AppSkin.PAPER) {
                    LibraryScreen(runtime = runtime, snapshot = snapshot, appState = appState)
                }
            }

            rule.onNodeWithContentDescription("Filter scores").performClick()
            rule.onNodeWithTag(LibraryTestTags.FilterPopover).assertExists()
            rule.onNodeWithText("LilyPond Viewer").performClick()
            rule.runOnIdle {
                assertEquals(
                    com.ryanjames.lunar.library.model.ViewerSupportFilter.LILYPOND,
                    appState.query.viewerSupport,
                )
            }
            rule.onNodeWithText("Done").performClick()
            assertTrue(rule.onAllNodes(isPopup()).fetchSemanticsNodes().isEmpty())
            assertTrue(rule.onAllNodesWithTag(LibraryTestTags.row("moon_river")).fetchSemanticsNodes().isEmpty())
            rule.onNodeWithTag(LibraryTestTags.row("prelude")).assertIsDisplayed()

            rule.onNodeWithTag(LibraryTestTags.row("prelude")).performClick()
            rule.onNodeWithContentDescription("LilyPond viewer").assertExists()

            rule.onNodeWithContentDescription("Remove filter Viewer: LilyPond Viewer").performClick()
            rule.runOnIdle {
                assertEquals(com.ryanjames.lunar.library.model.ViewerSupportFilter.ALL, appState.query.viewerSupport)
            }
            rule.onNodeWithTag(LibraryTestTags.row("moon_river")).assertIsDisplayed()
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun detailPanelSitsBesideTheCatalogueOnWideWindowsAndBelowItOnNarrowOnes() = runBlocking {
        val item = testSheetMusicItem(id = "moon_river", title = "Moon River")
        listOf(1400 to true, 900 to false).forEach { (width, expectSide) ->
            val runtime = createTestPlatformRuntime(initialItems = listOf(item))
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.focusScore(item.id)
            runDesktopComposeUiTest(width = width, height = 900) {
                setContent {
                    LunarTheme(skin = AppSkin.PAPER) {
                        LibraryScreen(runtime = runtime, snapshot = runtime.repository.library.value, appState = appState)
                    }
                }
                val panel = onNodeWithTag(LibraryTestTags.DetailPanel).fetchSemanticsNode().boundsInRoot
                val catalogue = onNodeWithTag(LibraryTestTags.Catalogue).fetchSemanticsNode().boundsInRoot
                if (expectSide) {
                    assertTrue(panel.left >= catalogue.right, "Expected the panel beside the catalogue at $width px")
                } else {
                    assertTrue(panel.top >= catalogue.bottom, "Expected the panel below the catalogue at $width px")
                }
            }
        }
    }

    @Test
    fun longCatalogueTitleIsEllipsizedButKeepsFullTextInSemantics() {
        runBlocking {
            val longTitle = "Sonata for Piano and Violin in A major, " + "an extremely long movement title ".repeat(12)
            val item = testSheetMusicItem(id = "long", title = longTitle.trim())
            val runtime = createTestPlatformRuntime(initialItems = listOf(item))
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)

            rule.setContent {
                LunarTheme(skin = AppSkin.PAPER) {
                    LibraryScreen(runtime = runtime, snapshot = runtime.repository.library.value, appState = appState)
                }
            }

            val titleNode = rule.onNodeWithTag(LibraryTestTags.rowTitle("long"), useUnmergedTree = true)
            titleNode.assertTextEquals(longTitle.trim())
            val layouts = mutableListOf<TextLayoutResult>()
            titleNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.single().hasVisualOverflow)
            assertEquals(1, layouts.single().lineCount)
        }
    }

    @Test
    fun alphabetIndexJumpsToTheFirstGroupForALetter() {
        runBlocking {
            val composers = listOf("Albeniz", "Bach", "Chopin", "Debussy", "Elgar", "Faure", "Grieg", "Haydn", "Ives", "Liszt")
            val items = composers.flatMap { composer ->
                (1..4).map { index -> testSheetMusicItem(id = "${composer}_$index", title = "$composer piece $index", composer = composer) }
            }
            val runtime = createTestPlatformRuntime(initialItems = items)
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.updateBrowseMode(com.ryanjames.lunar.ui.LibraryBrowseMode.BY_COMPOSER)

            rule.setContent {
                LunarTheme(skin = AppSkin.PAPER) {
                    LibraryScreen(runtime = runtime, snapshot = runtime.repository.library.value, appState = appState)
                }
            }

            assertTrue(rule.onAllNodesWithTag(LibraryTestTags.row("Liszt_1")).fetchSemanticsNodes().isEmpty())
            rule.onNodeWithContentDescription("Jump to L").performClick()
            rule.onNodeWithTag(LibraryTestTags.row("Liszt_1")).assertIsDisplayed()
            rule.onNodeWithText("LISZT").assertIsDisplayed()
        }
    }

    @Test
    fun galleryFocusDoesNotTickTheCardCheckbox() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "alpha", title = "Alpha"),
                testSheetMusicItem(id = "beta", title = "Beta"),
            )
            val runtime = createTestPlatformRuntime(initialItems = items)
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.updateLayoutMode(com.ryanjames.lunar.ui.LibraryLayoutMode.GRID)

            rule.setContent {
                LunarTheme(theme = AppColorTheme.OCEAN) {
                    LibraryScreen(runtime = runtime, snapshot = runtime.repository.library.value, appState = appState)
                }
            }

            rule.onNodeWithTag(LibraryTestTags.row("alpha")).performClick()
            rule.runOnIdle {
                assertEquals("alpha", appState.focusedScoreId)
                assertTrue(appState.selectedScoreIds.isEmpty())
            }
            rule.onNodeWithContentDescription("Select Alpha").assertIsOff()

            rule.onNodeWithContentDescription("Select Beta").performClick()
            rule.runOnIdle { assertEquals(setOf("beta"), appState.selectedScoreIds) }
            rule.onNodeWithContentDescription("Select Alpha").assertIsOff()
            rule.onNodeWithContentDescription("Select Beta").assertIsOn()

            rule.onNodeWithContentDescription("Select Alpha").performClick()
            rule.onNodeWithContentDescription("Select Alpha").performClick()
            rule.runOnIdle { assertEquals(setOf("beta"), appState.selectedScoreIds) }
        }
    }

    @Test
    fun groupsNamedLikeTheUnnamedBucketRenderAlongsideIt() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "named", title = "Named", composer = "No composer", collection = "No collection"),
                testSheetMusicItem(id = "loose", title = "Loose", composer = ""),
            )
            val runtime = createTestPlatformRuntime(initialItems = items)
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            appState.updateBrowseMode(com.ryanjames.lunar.ui.LibraryBrowseMode.BY_COLLECTION)

            rule.setContent {
                LunarTheme(theme = AppColorTheme.OCEAN) {
                    LibraryScreen(runtime = runtime, snapshot = runtime.repository.library.value, appState = appState)
                }
            }

            assertEquals(2, rule.onAllNodesWithText("NO COLLECTION").fetchSemanticsNodes().size)
            rule.onNodeWithTag(LibraryTestTags.row("named")).assertIsDisplayed()
            rule.onNodeWithTag(LibraryTestTags.row("loose")).assertIsDisplayed()

            rule.runOnIdle { appState.updateBrowseMode(com.ryanjames.lunar.ui.LibraryBrowseMode.BY_COMPOSER) }
            rule.waitForIdle()
            assertEquals(2, rule.onAllNodesWithText("NO COMPOSER").fetchSemanticsNodes().size)
            rule.onNodeWithTag(LibraryTestTags.row("named")).assertIsDisplayed()
            rule.onNodeWithTag(LibraryTestTags.row("loose")).assertIsDisplayed()
        }
    }

    @Test
    fun libraryScreenShowsSongbookPopoverAndSongbookOverview() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(id = "autumn_leaves", title = "Autumn Leaves", collection = "Standards"),
                testSheetMusicItem(id = "all_the_things", title = "All The Things You Are", collection = "Standards"),
            )
            val songbook = testLibrarySongbook(
                id = "songbook_standards",
                name = "Standards Book",
                itemIds = listOf("all_the_things"),
                pageCount = 12,
            )
            val runtime = createTestPlatformRuntime(
                initialItems = items,
                initialSongbooks = listOf(songbook),
                songbookCreationSupported = true,
                songbookCoverImageSupported = true,
            )
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)

            rule.setContent {
                val snapshot by runtime.repository.library.collectAsState()
                LunarTheme(skin = AppSkin.PAPER) {
                    LibraryScreen(runtime = runtime, snapshot = snapshot, appState = appState)
                }
            }

            rule.onNodeWithContentDescription("Select Autumn Leaves").performClick()
            rule.onNodeWithText("Add to songbook").performClick()
            rule.onNodeWithTag(LibraryTestTags.SongbookPopover).assertExists()
            rule.onNodeWithText("Standards Book").assertExists()
            rule.onNodeWithText("New songbook...").performClick()
            rule.onNodeWithText("Bind a new songbook").assertIsDisplayed()
            rule.onNodeWithText("Choose cover").assertIsDisplayed()
            rule.onNodeWithText("Cancel").performClick()
            rule.runOnIdle { assertTrue(!appState.newSongbookDialogVisible) }

            rule.runOnIdle { appState.selectSection(AppSection.SONGBOOKS) }
            rule.onNodeWithText("Standards Book").assertIsDisplayed()
            rule.onNodeWithText("Standards Book").performClick()
            rule.runOnIdle {
                assertEquals(ViewerTarget.Songbook(songbook.id), appState.fullscreenTarget)
            }
        }
    }

    @Test
    fun importScreenShowsSourceActionsAndCacheSummary() {
        runBlocking {
            val runtime = createTestPlatformRuntime(
                initialSources = listOf(
                    testLocalFilesSource(
                        id = "source_local_files",
                        label = "Desktop folder",
                        itemCount = 3,
                    )
                ),
                localImageImportSupported = true,
                cacheSnapshot = LibraryCacheSnapshot(
                    storageLabel = "Desktop cache",
                    cacheRootPath = "/tmp/lunar-cache",
                    metadataCached = true,
                    sourceRegistryCached = true,
                    cachedPdfCount = 3,
                    cachedPdfBytes = 2_048L,
                ),
                lilyPondImportSupported = true,
                museScoreImportSupported = true,
            )
            val appState = createTestLunarAppState(
                scope = backgroundScope(),
                runtime = runtime,
            )

            rule.setContent {
                LunarTheme(skin = AppSkin.PAPER) {
                    ImportScreen(
                        runtime = runtime,
                        importerState = ImporterState(statusMessage = "Ready to import PDFs."),
                        syncState = CloudSyncState(),
                        settings = runtime.settingsStore.settings.value,
                        libraryCount = 3,
                        appState = appState,
                    )
                }
            }

            rule.onNodeWithText("Sources").assertIsDisplayed()
            rule.onDisplayedNodeWithText("Add local source").assertIsDisplayed()
            rule.onDisplayedNodeWithText("Add cloud source").assertIsDisplayed()
            rule.onDisplayedNodeWithText("Add local source").performClick()
            rule.onNodeWithText("Choose how to import local PDFs, LilyPond files, MuseScore files, or image files into your library. Matching .json metadata sidecars are imported automatically.").assertIsDisplayed()
            rule.onNodeWithText("Pick PDFs, LY/ILY/LYI LilyPond files, MSCZ/MSCX MuseScore files, or PNG/JPG/JPEG images. Matching .json sidecars are detected automatically.").assertIsDisplayed()
            rule.onNodeWithText("Cancel").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText("On-device cache").fetchSemanticsNodes().isNotEmpty()
            }
            rule.onNodeWithText("Copy cache details").assertIsDisplayed()
        }
    }

    @Test
    fun settingsScreenPicksMoonlightSkinAndPersistsIt() {
        runBlocking {
            val settingsDir = Files.createTempDirectory("lunar-settings-test").toString().toPath()
            val settingsPath = settingsDir / "app_settings.json"
            val runtime = createTestPlatformRuntime().copy(
                settingsStore = JsonAppSettingsStore(
                    fileSystem = FileSystem.SYSTEM,
                    settingsPath = settingsPath,
                ),
            )
            runtime.settingsStore.initialize()
            val appState = createTestLunarAppState(
                scope = backgroundScope(),
                runtime = runtime,
            )
            appState.selectSection(AppSection.SETTINGS)

            rule.setContent {
                val settings by runtime.settingsStore.settings.collectAsState()
                LunarTheme(skin = settings.resolvedSkin) {
                    SettingsScreen(
                        settings = settings,
                        appState = appState,
                    )
                }
            }

            rule.onNodeWithTag(LunarTestTags.SETTINGS_APPEARANCE_PANEL).assertIsDisplayed()
            rule.onNodeWithTag(LunarTestTags.skinOption(AppSkin.PAPER)).assertIsSelected()
            rule.onNodeWithTag(LunarTestTags.skinOption(AppSkin.MOONLIGHT)).assertIsNotSelected()

            rule.onNodeWithTag(LunarTestTags.skinOption(AppSkin.MOONLIGHT)).performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.settingsStore.settings.value.skin == AppSkin.MOONLIGHT
            }
            rule.onNodeWithTag(LunarTestTags.skinOption(AppSkin.MOONLIGHT)).assertIsSelected()
            rule.onNodeWithTag(LunarTestTags.skinOption(AppSkin.PAPER)).assertIsNotSelected()

            val reloaded = JsonAppSettingsStore(
                fileSystem = FileSystem.SYSTEM,
                settingsPath = settingsPath,
            )
            reloaded.initialize()
            assertEquals(AppSkin.MOONLIGHT, reloaded.settings.value.skin)

            rule.onNodeWithText("Keybindings").performClick()
            rule.onNodeWithText("Viewer keybindings").assertIsDisplayed()
            rule.onNodeWithText("Toggle fullscreen").assertIsDisplayed()
            rule.onNodeWithText("F11").assertIsDisplayed()

            rule.runOnIdle {
                appState.resetGlobalSettings()
            }
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.settingsStore.settings.value.skin == AppSkin.PAPER
            }
            rule.runOnIdle {
                appState.selectSection(AppSection.LIBRARY)
            }
            rule.runOnIdle {
                assertEquals(AppSection.LIBRARY, appState.selectedSection)
            }
            FileSystem.SYSTEM.deleteRecursively(settingsDir)
        }
    }

    @Test
    fun importScreenLetsYouEditExistingGoogleDriveSource() {
        runBlocking {
            val runtime = createTestPlatformRuntime(
                initialSources = listOf(
                    CloudGoogleDriveSource(
                        id = "drive_source",
                        label = "Main Drive library",
                        addedAtEpochMillis = 1L,
                        settings = GoogleDriveStorageSettings(
                            clientId = "client-id",
                            refreshToken = "refresh-token",
                            roots = listOf(
                                GoogleDriveImportRoot(
                                    id = "root",
                                    label = "Scores",
                                    folderId = "folder-123",
                                    folderStrategy = CloudPathStrategy.FLAT,
                                )
                            ),
                        ),
                    )
                ),
            )
            val appState = createTestLunarAppState(
                scope = backgroundScope(),
                runtime = runtime,
            )

            rule.setContent {
                LunarTheme(skin = AppSkin.PAPER) {
                    ImportScreen(
                        runtime = runtime,
                        importerState = ImporterState(statusMessage = "Ready to import PDFs."),
                        syncState = CloudSyncState(),
                        settings = runtime.settingsStore.settings.value,
                        libraryCount = 0,
                        appState = appState,
                    )
                }
            }

            rule.onDisplayedNodeWithText("Main Drive library").assertIsDisplayed()
            rule.onDisplayedNodeWithText("Edit").assertIsDisplayed()
            rule.onDisplayedNodeWithText("Edit").performClick()
            rule.onNodeWithText("Edit cloud source").assertIsDisplayed()
            rule.onNodeWithText("Refresh token").assertIsDisplayed()
            rule.onNodeWithText("Save").assertIsDisplayed()
        }
    }

    @Test
    fun composeScreenRendersFreeformLilyPondSource() {
        runBlocking {
            val lilyPondRenderer = RecordingComposeLilyPondRenderer()
            val runtime = createTestPlatformRuntime(
                renderer = ComposeTestPdfPageRenderer(),
                lilyPondLiveRenderer = lilyPondRenderer,
                lilyPondLiveViewingSupported = true,
            )
            val editedSource = "\\version \"2.24.0\"\n{ g'4 a' b' c'' }"

            rule.setContent {
                LunarTheme(skin = AppSkin.PAPER) {
                    ComposeScreen(runtime = runtime)
                }
            }

            rule.onNodeWithText("Compose").assertIsDisplayed()
            rule.onNodeWithText("LilyPond").assertIsDisplayed()
            rule.onNodeWithContentDescription("Clear LilyPond source").assertIsDisplayed()
            rule.onNodeWithContentDescription("Load LilyPond template").assertIsDisplayed()
            rule.onNodeWithContentDescription("Freeform LilyPond editor").assertIsDisplayed()
            rule.waitUntil(timeoutMillis = 5_000) {
                lilyPondRenderer.renderedSources.isNotEmpty()
            }

            rule.onNodeWithContentDescription("Freeform LilyPond editor")
                .performTextReplacement(editedSource)
            rule.waitUntil(timeoutMillis = 5_000) {
                lilyPondRenderer.renderedSources.any { source -> source.sourceText == editedSource }
            }
        }
    }

    @Test
    fun composeScreenPersistsDraftsAndImportsSavedComposition() {
        runBlocking {
            val lilyPondRenderer = RecordingComposeLilyPondRenderer()
            val compositionStore = InMemoryCompositionDraftStore()
            val compositionImporter = RecordingCompositionPdfImporter()
            val runtime = createTestPlatformRuntime(
                renderer = ComposeTestPdfPageRenderer(),
                lilyPondLiveRenderer = lilyPondRenderer,
                lilyPondLiveViewingSupported = true,
                compositionStore = compositionStore,
                compositionPdfImporter = compositionImporter,
            )
            val editedSource = "\\version \"2.24.0\"\n{ e'4 fis' gis' a' }"

            rule.setContent {
                LunarTheme(skin = AppSkin.PAPER) {
                    ComposeScreen(runtime = runtime)
                }
            }

            rule.onNodeWithContentDescription("Collapse composition list").assertIsDisplayed()
            rule.onNodeWithContentDescription("Collapse composition list").performClick()
            rule.onNodeWithContentDescription("Expand composition list").assertIsDisplayed()
            rule.onNodeWithContentDescription("Expand composition list").performClick()
            rule.onNodeWithContentDescription("Composition title")
                .performTextReplacement("Morning Study")
            rule.onNodeWithContentDescription("Composition composer")
                .performTextReplacement("R. Composer")
            rule.onNodeWithContentDescription("Freeform LilyPond editor")
                .performTextReplacement(editedSource)
            rule.onNodeWithContentDescription("Save composition").performClick()

            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.repository.library.value.items.any { item -> item.title == "Morning Study" }
            }
            val importedItem = runtime.repository.library.value.items.first { item ->
                item.title == "Morning Study"
            }
            assertEquals("R. Composer", importedItem.composer)
            assertTrue(compositionImporter.requests.any { request ->
                request.title == "Morning Study" && request.composer == "R. Composer"
            })

            rule.onNodeWithContentDescription("New composition").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                compositionStore.drafts.value.size >= 2
            }
            assertTrue(compositionStore.drafts.value.any { draft ->
                draft.title == "Morning Study" && draft.sourceText == editedSource
            })
            rule.onNodeWithContentDescription("Open composition Morning Study").assertIsDisplayed()
        }
    }

    @Test
    fun expandedSidebarNavigatesEverySection() {
        runBlocking {
            val runtime = createTestPlatformRuntime(
                initialItems = listOf(testSheetMusicItem(id = "moon_river", title = "Moon River", collection = "Standards")),
            )
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            rule.setShellContent(runtime, appState, width = 1024.dp)

            rule.onNodeWithText("Lunar").assertIsDisplayed()
            rule.onNodeWithTag("sidebar-arrange-FAVOURITES").assertIsDisplayed()
            rule.onNodeWithText("Standards").assertIsDisplayed()
            rule.onNodeWithTag("sidebar-arrange-FAVOURITES").performClick()
            rule.runOnIdle { assertTrue(appState.query.favoritesOnly) }

            rule.navigateEverySection(appState)

            // The contextual part is Library-only.
            rule.runOnIdle { appState.selectSection(AppSection.SETLISTS) }
            assertTrue(rule.onAllNodesWithTag("sidebar-arrange-ALL").fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun collapsedRailNavigatesEverySectionAndPersistsToggle() {
        runBlocking {
            val runtime = createTestPlatformRuntime(initialSettings = AppSettings(sidebarCollapsed = true))
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            rule.setShellContent(runtime, appState, width = 1024.dp)

            assertTrue(rule.onAllNodesWithText("Lunar").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithContentDescription("Setlists").assertIsDisplayed()
            rule.navigateEverySection(appState)

            rule.onNodeWithContentDescription("Expand sidebar").performClick()
            rule.waitUntil(timeoutMillis = 5_000) { !runtime.settingsStore.settings.value.sidebarCollapsed }
            rule.onNodeWithText("Lunar").assertIsDisplayed()

            rule.onNodeWithTag("shell-content").performSemanticsAction(SemanticsActions.RequestFocus)
            rule.onNodeWithTag("shell-content").assertIsFocused()
            rule.onRoot().performKeyInput {
                keyDown(Key.CtrlLeft)
                pressKey(Key.B)
                keyUp(Key.CtrlLeft)
            }
            rule.waitUntil(timeoutMillis = 5_000) { runtime.settingsStore.settings.value.sidebarCollapsed }
            rule.onNodeWithContentDescription("Expand sidebar").assertIsDisplayed()
        }
    }

    @Test
    fun mediumWidthShowsRailWithoutExpandButton() {
        runBlocking {
            val runtime = createTestPlatformRuntime()
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            rule.setShellContent(runtime, appState, width = 800.dp)

            rule.onNodeWithContentDescription("Songbooks").assertIsDisplayed()
            assertTrue(rule.onAllNodesWithContentDescription("Expand sidebar").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithText("content:LIBRARY:inline").assertIsDisplayed()
        }
    }

    @Test
    fun compactDrawerNavigatesEverySection() {
        runBlocking {
            val runtime = createTestPlatformRuntime()
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            rule.setShellContent(runtime, appState, width = 420.dp)

            assertTrue(rule.onAllNodesWithTag(SidebarDrawerTag).fetchSemanticsNodes().isEmpty())
            AppSection.entries.forEach { section ->
                rule.onNodeWithContentDescription("Open navigation").performClick()
                rule.onNodeWithTag(SidebarDrawerTag).assertIsDisplayed()
                rule.onNodeWithTag(sidebarNavTag(section)).performClick()
                rule.runOnIdle { assertEquals(section, appState.selectedSection) }
                assertTrue(rule.onAllNodesWithTag(SidebarDrawerTag).fetchSemanticsNodes().isEmpty())
                rule.onNodeWithText("content:${section.name}:", substring = true).assertIsDisplayed()
            }
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun compactDrawerClosesOnEscape() {
        runBlocking {
            val runtime = createTestPlatformRuntime()
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            rule.setShellContent(runtime, appState, width = 420.dp)

            rule.onNodeWithContentDescription("Open navigation").performClick()
            rule.onNodeWithTag(SidebarDrawerTag).assertIsDisplayed()
            rule.onRoot().performKeyInput { pressKey(Key.Escape) }
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithTag(SidebarDrawerTag).fetchSemanticsNodes().isEmpty()
            }
            rule.runOnIdle { assertEquals(AppSection.LIBRARY, appState.selectedSection) }
        }
    }

    @Test
    fun shortRailKeepsSettingsReachable() {
        runBlocking {
            val runtime = createTestPlatformRuntime()
            val appState = createTestLunarAppState(scope = backgroundScope(), runtime = runtime)
            // A landscape phone: medium width selects the rail, but six items cannot fit.
            rule.setShellContent(runtime, appState, width = 800.dp, height = 240.dp)

            val lastPrimary = SidebarPrimaryDestinations.last().section
            rule.onNodeWithTag(sidebarNavTag(lastPrimary)).performScrollTo().performClick()
            rule.runOnIdle { assertEquals(lastPrimary, appState.selectedSection) }

            rule.onNodeWithTag(sidebarNavTag(AppSection.SETTINGS)).assertIsDisplayed().performClick()
            rule.runOnIdle { assertEquals(AppSection.SETTINGS, appState.selectedSection) }
        }
    }
}

private fun ComposeContentTestRule.setShellContent(
    runtime: com.ryanjames.lunar.platform.PlatformRuntime,
    appState: com.ryanjames.lunar.ui.LunarAppState,
    width: Dp,
    height: Dp = 700.dp,
) {
    setContent {
        val settings by runtime.settingsStore.settings.collectAsState()
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        LunarTheme(skin = AppSkin.PAPER) {
            Box(modifier = Modifier.requiredSize(width = width, height = height)) {
                LunarAppShell(
                    appState = appState,
                    snapshot = runtime.repository.library.collectAsState().value,
                    syncState = CloudSyncState(),
                    settings = settings,
                ) { sidebarContextVisible ->
                    Box(modifier = Modifier.testTag("shell-content").focusRequester(focusRequester).focusable()) {
                        val mode = if (sidebarContextVisible) "sidebar" else "inline"
                        Text("content:${appState.selectedSection.name}:$mode")
                    }
                }
            }
        }
    }
}

private fun ComposeContentTestRule.navigateEverySection(appState: com.ryanjames.lunar.ui.LunarAppState) {
    AppSection.entries.forEach { section ->
        onNodeWithTag(sidebarNavTag(section)).performClick()
        runOnIdle { assertEquals(section, appState.selectedSection) }
    }
}

private fun backgroundScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

private fun ComposeContentTestRule.onDisplayedNodeWithText(text: String) =
    onNode(hasText(text) and !hasAnyAncestor(isPopup()))

private class RecordingComposeLilyPondRenderer : LilyPondLiveRenderer {
    val renderedSources = mutableListOf<LilyPondSourceSnapshot>()

    override suspend fun loadSource(document: com.ryanjames.lunar.library.model.PdfDocumentReference): LilyPondSourceSnapshot? =
        null

    override suspend fun renderSource(source: LilyPondSourceSnapshot): LilyPondLiveRenderResult {
        renderedSources += source
        return LilyPondLiveRenderResult(
            documentPath = "/compose/${source.revision.hashCode()}.pdf",
            pageCount = 1,
        )
    }
}

private class ComposeTestPdfPageRenderer : PdfPageRenderer {
    override suspend fun inspect(documentPath: String): PdfDocumentInfo = PdfDocumentInfo(pageCount = 1)

    override suspend fun renderPage(
        documentPath: String,
        pageIndex: Int,
        targetWidth: Int,
    ): RenderedPdfPage? = RenderedPdfPage(
        pageIndex = pageIndex,
        pageCount = 1,
        aspectRatio = 0.72f,
        image = ImageBitmap(width = 120, height = 160),
    )
}

private class RecordingCompositionPdfImporter : CompositionPdfImporter {
    val requests = mutableListOf<CompositionPdfImportRequest>()

    override suspend fun importRenderedComposition(
        request: CompositionPdfImportRequest,
    ): ImportedPdfDescriptor {
        requests += request
        val originalFileName = "${request.title}.pdf"
        return ImportedPdfDescriptor(
            storedPath = "/compose-library/${request.draftId}-${requests.size}.pdf",
            originalFileName = originalFileName,
            sourceUri = "lunar-compose:${request.draftId}",
            contentFingerprint = "composition-${request.draftId}-${requests.size}",
            pageCount = request.pageCount ?: 1,
            suggestedTitle = request.title,
            scoreMetadata = ScoreMetadata(
                title = request.title,
                composer = ScoreMetadataComposer(name = request.composer),
                pageCount = request.pageCount ?: 1,
                source = ScoreMetadataSource(
                    filename = originalFileName,
                    fileType = "pdf",
                    url = "lunar-compose:${request.draftId}",
                ),
            ),
        )
    }
}

private class DelayedSheetMusicRepository(
    private val delegate: SheetMusicRepository,
    private val initializeGate: CompletableDeferred<Unit>,
) : SheetMusicRepository by delegate {
    private val state = MutableStateFlow(LibrarySnapshot())

    override val library: StateFlow<LibrarySnapshot> = state.asStateFlow()

    override suspend fun initialize() {
        initializeGate.await()
        delegate.initialize()
        state.value = delegate.library.value
    }
}

private class DelayedAppSettingsStore(
    private val loadedSettings: AppSettings,
    private val initializeGate: CompletableDeferred<Unit>,
) : AppSettingsStore {
    private val state = MutableStateFlow(AppSettings())

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override suspend fun initialize() {
        initializeGate.await()
        state.value = loadedSettings
    }

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }

    override suspend fun reset() {
        state.value = AppSettings()
    }
}
