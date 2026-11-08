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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
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
                assertEquals(null, appState.previewTarget)
            }
            rule.onNodeWithTag(LunarTestTags.LIBRARY_SEARCH).performTextReplacement("R")
            rule.runOnIdle {
                assertEquals("R", appState.query.searchText)
                assertEquals(null, appState.previewTarget)
            }
        }
    }

    @Test
    @OptIn(ExperimentalTestApi::class)
    fun libraryScreenOpensRandomSetlistDialogAndSavedSetlistDetail() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(
                    id = "moon_river",
                    title = "Moon River",
                    collection = "Standards",
                    tags = listOf("jazz"),
                    pageCount = 2,
                ),
                testSheetMusicItem(
                    id = "clair_de_lune",
                    title = "Clair de Lune",
                    collection = "Recital",
                    tags = listOf("piano"),
                    pageCount = 4,
                ),
            )
            val setlist = testLibrarySetlist(
                id = "set_morning",
                name = "Morning rehearsal",
                itemIds = items.map { it.id },
                updatedAtEpochMillis = 10L,
            )
            val runtime = createTestPlatformRuntime(
                initialItems = items,
                initialSetlists = listOf(setlist),
                pdfExporter = TestPdfDocumentExporter { _, suggestedFileName ->
                    "Downloads/$suggestedFileName"
                },
                scoreDownloadSupported = true,
            )
            val appState = createTestLunarAppState(
                scope = backgroundScope(),
                runtime = runtime,
            )
            val snapshot = runtime.repository.library.value

            rule.setContent {
                LunarTheme(skin = AppSkin.MOONLIGHT) {
                    LibraryScreen(
                        runtime = runtime,
                        snapshot = snapshot,
                        appState = appState,
                        viewerKeybindings = ViewerKeybindings(openRandomScore = "R"),
                    )
                }
            }

            rule.onNodeWithText("Open random sheet").assertIsDisplayed()
            rule.onNodeWithText("Random setlist").assertIsDisplayed()
            rule.onRoot().performKeyInput { pressKey(Key.R) }
            rule.waitUntil(timeoutMillis = 5_000) {
                appState.previewTarget is ViewerTarget.Score
            }
            rule.onNodeWithContentDescription("Download Moon River").assertIsDisplayed()
            rule.onNodeWithContentDescription("Download Moon River").performClick()
            rule.waitUntil(timeoutMillis = 5_000) {
                appState.bannerMessage == "Saved \"Moon River\" to Downloads/Moon River.pdf."
            }
            rule.onNodeWithContentDescription("Mark Moon River as favorite").assertIsDisplayed()
            rule.onNodeWithContentDescription("Show info for Moon River").assertIsDisplayed()
            rule.onNodeWithContentDescription("Edit Moon River").assertIsDisplayed()
            rule.onNodeWithContentDescription("Delete Moon River").assertIsDisplayed()
            rule.onNodeWithContentDescription("Show info for Moon River").performClick()
            rule.onNodeWithText("Score info").assertIsDisplayed()
            rule.onNodeWithText("Filename").assertIsDisplayed()
            rule.onNodeWithText("Moon River.pdf").assertIsDisplayed()
            rule.onNodeWithText("Close").performClick()
            rule.onNodeWithTag(LunarTestTags.LIBRARY_SEARCH).assertIsDisplayed()
            rule.runOnIdle {
                assertTrue(
                    rule.onAllNodesWithText("Browsing 2 scores. Filter by collection or tag when you need it.")
                        .fetchSemanticsNodes()
                        .isEmpty()
                )
            }
            rule.onNodeWithContentDescription("Sort descending").assertIsDisplayed()
            rule.onNodeWithContentDescription("Sort descending").performClick()
            rule.runOnIdle {
                assertEquals(com.ryanjames.lunar.library.model.SortDirection.ASCENDING, appState.query.sortDirection)
            }
            rule.onNodeWithContentDescription("Layout options (List)").assertIsDisplayed()
            rule.onNodeWithContentDescription("Layout options (List)").performClick()
            rule.onNodeWithText("Grid").performClick()
            rule.runOnIdle {
                assertEquals(com.ryanjames.lunar.ui.LibraryLayoutMode.GRID, appState.layoutMode)
            }
            rule.onNodeWithText("Show filters").assertIsDisplayed()
            rule.onNodeWithText("Show filters").performClick()
            rule.onNodeWithText("All collections").assertIsDisplayed()

            rule.onDisplayedNodeWithText("Random setlist").performClick()
            rule.onNodeWithText("Create a random setlist").assertIsDisplayed()
            rule.onNodeWithText("Cancel").performClick()

            rule.runOnIdle { appState.selectSection(AppSection.SETLISTS) }
            rule.onNodeWithText("Morning rehearsal").assertIsDisplayed()
            rule.onNodeWithText("Open").performClick()
            rule.onNodeWithText("Saved automatically. Delete it whenever you no longer need it.").assertIsDisplayed()
        }
    }

    @Test
    fun libraryScreenShowsLilyPondBadgeAndViewerSupportFilter() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(
                    id = "moon_river",
                    title = "Moon River",
                    pageCount = 2,
                    originalFileName = "Moon River.pdf",
                ),
                testSheetMusicItem(
                    id = "prelude",
                    title = "Prelude",
                    composer = "Bach",
                    pageCount = 1,
                    originalFileName = "Prelude.ly",
                ),
            )
            val runtime = createTestPlatformRuntime(initialItems = items)
            val appState = createTestLunarAppState(
                scope = backgroundScope(),
                runtime = runtime,
            )
            val snapshot = runtime.repository.library.value

            rule.setContent {
                LunarTheme(skin = AppSkin.PAPER) {
                    LibraryScreen(
                        runtime = runtime,
                        snapshot = snapshot,
                        appState = appState,
                    )
                }
            }

            rule.onNodeWithContentDescription("LilyPond viewer").assertIsDisplayed()
            rule.onNodeWithContentDescription("PDF viewer").assertIsDisplayed()
            rule.onNodeWithText("Show filters").assertIsDisplayed()
            rule.onNodeWithText("Show filters").performClick()
            rule.onNodeWithText("Viewer support").assertIsDisplayed()
            rule.onNodeWithText("PDF Viewer").assertIsDisplayed()
            rule.onNodeWithText("LilyPond Viewer").assertIsDisplayed()
            rule.onNodeWithText("LilyPond Viewer").performClick()
            rule.runOnIdle {
                assertEquals(
                    com.ryanjames.lunar.library.model.ViewerSupportFilter.LILYPOND,
                    appState.query.viewerSupport,
                )
            }
            assertTrue(rule.onAllNodesWithText("Moon River").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithText("Prelude").assertIsDisplayed()
        }
    }

    @Test
    fun libraryScreenShowsSongbookActionsAndSongbookOverview() {
        runBlocking {
            val items = listOf(
                testSheetMusicItem(
                    id = "autumn_leaves",
                    title = "Autumn Leaves",
                    collection = "Standards",
                ),
                testSheetMusicItem(
                    id = "all_the_things",
                    title = "All The Things You Are",
                    collection = "Standards",
                ),
            )
            val songbook = testLibrarySongbook(
                id = "songbook_standards",
                name = "Standards Book",
                itemIds = items.map { it.id },
                pageCount = 12,
            )
            val runtime = createTestPlatformRuntime(
                initialItems = items,
                initialSongbooks = listOf(songbook),
                songbookCreationSupported = true,
                songbookCoverImageSupported = true,
            )
            val appState = createTestLunarAppState(
                scope = backgroundScope(),
                runtime = runtime,
            )
            val snapshot = runtime.repository.library.value

            rule.setContent {
                LunarTheme(skin = AppSkin.PAPER) {
                    LibraryScreen(
                        runtime = runtime,
                        snapshot = snapshot,
                        appState = appState,
                    )
                }
            }

            rule.onNodeWithContentDescription("Select Autumn Leaves").performClick()
            rule.onNodeWithText("Add to songbook").assertIsDisplayed()
            rule.onNodeWithText("Add to songbook").performClick()
            rule.onNodeWithText("Add 1 score to a songbook").assertIsDisplayed()
            rule.onNodeWithText("Choose cover").assertIsDisplayed()
            rule.onNodeWithText("Cancel").performClick()

            rule.runOnIdle { appState.selectSection(AppSection.SONGBOOKS) }
            rule.onNodeWithText("Standards Book").assertIsDisplayed()
            rule.onNodeWithText("Standards Book").performClick()
            rule.runOnIdle {
                assertEquals(ViewerTarget.Songbook(songbook.id), appState.previewTarget)
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
}

private fun ComposeContentTestRule.setShellContent(
    runtime: com.ryanjames.lunar.platform.PlatformRuntime,
    appState: com.ryanjames.lunar.ui.LunarAppState,
    width: Dp,
) {
    setContent {
        val settings by runtime.settingsStore.settings.collectAsState()
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        LunarTheme(skin = AppSkin.PAPER) {
            Box(modifier = Modifier.requiredSize(width = width, height = 700.dp)) {
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
