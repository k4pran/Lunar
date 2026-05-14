package com.ryanjames.lunar

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import com.ryanjames.lunar.library.data.CloudGoogleDriveSource
import com.ryanjames.lunar.library.data.CloudPathStrategy
import com.ryanjames.lunar.library.data.GoogleDriveImportRoot
import com.ryanjames.lunar.library.data.GoogleDriveStorageSettings
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.data.SheetMusicRepository
import com.ryanjames.lunar.platform.ImporterState
import com.ryanjames.lunar.platform.LibraryCacheSnapshot
import com.ryanjames.lunar.platform.LilyPondLiveRenderer
import com.ryanjames.lunar.platform.LilyPondLiveRenderResult
import com.ryanjames.lunar.platform.LilyPondSourceSnapshot
import com.ryanjames.lunar.platform.PdfDocumentInfo
import com.ryanjames.lunar.platform.PdfPageRenderer
import com.ryanjames.lunar.platform.RenderedPdfPage
import com.ryanjames.lunar.settings.AppColorTheme
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.AppSettingsStore
import com.ryanjames.lunar.settings.ViewerKeybindings
import com.ryanjames.lunar.sync.CloudSyncState
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.ComposeScreen
import com.ryanjames.lunar.ui.ViewerTarget
import com.ryanjames.lunar.ui.ImportScreen
import com.ryanjames.lunar.ui.LibraryScreen
import com.ryanjames.lunar.ui.LunarTooltip
import com.ryanjames.lunar.ui.LunarTheme
import com.ryanjames.lunar.ui.SettingsScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenSmokeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun appShowsStartupLoadingUntilLocalRuntimeIsReady() {
        runBlocking {
            val settingsGate = CompletableDeferred<Unit>()
            val repositoryGate = CompletableDeferred<Unit>()
            val loadedSettings = AppSettings(
                theme = AppColorTheme.DARCULA,
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

            rule.onNodeWithContentDescription("Lunar is starting").assertIsDisplayed()
            assertTrue(rule.onAllNodesWithText("Moon River").fetchSemanticsNodes().isEmpty())

            settingsGate.complete(Unit)
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Lunar is starting").assertIsDisplayed()
            assertTrue(rule.onAllNodesWithText("Moon River").fetchSemanticsNodes().isEmpty())

            repositoryGate.complete(Unit)
            rule.waitUntil(timeoutMillis = 5_000) {
                rule.onAllNodesWithText("Moon River").fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(rule.onAllNodesWithText("Loading Lunar").fetchSemanticsNodes().isEmpty())
            rule.onNodeWithText("Moon River").assertIsDisplayed()
        }
    }

    @Test
    fun lunarTooltipShowsFromLongClickSemantics() {
        rule.setContent {
            LunarTheme(theme = AppColorTheme.OCEAN) {
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
                LunarTheme(theme = AppColorTheme.DARCULA) {
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
            rule.onNodeWithContentDescription("Library search").assertIsDisplayed()
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

            rule.onNodeWithText("Setlists").performClick()
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
                LunarTheme(theme = AppColorTheme.OCEAN) {
                    LibraryScreen(
                        runtime = runtime,
                        snapshot = snapshot,
                        appState = appState,
                    )
                }
            }

            rule.onNodeWithContentDescription("Prelude opens with LilyPond Viewer").assertIsDisplayed()
            rule.onNodeWithContentDescription("Moon River opens with PDF Viewer").assertIsDisplayed()
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
                LunarTheme(theme = AppColorTheme.OCEAN) {
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

            rule.onNodeWithText("Songbooks").performClick()
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
                LunarTheme(theme = AppColorTheme.OCEAN) {
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
    fun settingsScreenResetsThemeAndReturnsToLibrary() {
        runBlocking {
            val runtime = createTestPlatformRuntime(
                initialSettings = AppSettings(theme = AppColorTheme.DARCULA),
            )
            val appState = createTestLunarAppState(
                scope = backgroundScope(),
                runtime = runtime,
            )
            appState.selectSection(AppSection.SETTINGS)

            rule.setContent {
                val settings by runtime.settingsStore.settings.collectAsState()
                LunarTheme(theme = settings.theme) {
                    SettingsScreen(
                        settings = settings,
                        appState = appState,
                    )
                }
            }

            rule.onNodeWithText("Appearance").assertIsDisplayed()
            rule.onNodeWithText("Color theme").assertIsDisplayed()
            rule.onNodeWithText("Darcula").assertIsDisplayed()
            rule.onNodeWithText("Keybindings").performClick()
            rule.onNodeWithText("Viewer keybindings").assertIsDisplayed()
            rule.onNodeWithText("Toggle fullscreen").assertIsDisplayed()
            rule.onNodeWithText("F11").assertIsDisplayed()

            rule.runOnIdle {
                appState.resetGlobalSettings()
            }
            rule.waitUntil(timeoutMillis = 5_000) {
                runtime.settingsStore.settings.value.theme == AppColorTheme.OCEAN
            }
            rule.runOnIdle {
                appState.selectSection(AppSection.LIBRARY)
            }
            rule.runOnIdle {
                assertEquals(AppSection.LIBRARY, appState.selectedSection)
            }
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
                LunarTheme(theme = AppColorTheme.OCEAN) {
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
                LunarTheme(theme = AppColorTheme.OCEAN) {
                    ComposeScreen(runtime = runtime)
                }
            }

            rule.onNodeWithText("Compose").assertIsDisplayed()
            rule.onNodeWithText("LilyPond").assertIsDisplayed()
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
    fun bottomNavigationShowsComposeSection() {
        rule.setContent {
            LunarTheme(theme = AppColorTheme.OCEAN) {
                BottomNavigationPanel(
                    selectedSection = AppSection.LIBRARY,
                    onSelectSection = {},
                )
            }
        }

        rule.onDisplayedNodeWithText("Sources").assertIsDisplayed()
        rule.onDisplayedNodeWithText("Library").assertIsDisplayed()
        rule.onDisplayedNodeWithText("Compose").assertIsDisplayed()
        rule.onDisplayedNodeWithText("Settings").assertIsDisplayed()
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
