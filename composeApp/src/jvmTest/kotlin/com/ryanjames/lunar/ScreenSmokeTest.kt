package com.ryanjames.lunar

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.foundation.layout.fillMaxSize
import com.ryanjames.lunar.library.data.CloudGoogleDriveSource
import com.ryanjames.lunar.library.data.CloudPathStrategy
import com.ryanjames.lunar.library.data.GoogleDriveImportRoot
import com.ryanjames.lunar.library.data.GoogleDriveStorageSettings
import com.ryanjames.lunar.platform.ImporterState
import com.ryanjames.lunar.platform.LibraryCacheSnapshot
import com.ryanjames.lunar.settings.AppColorTheme
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.ViewerKeybindings
import com.ryanjames.lunar.sync.CloudSyncState
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.ComposeScreen
import com.ryanjames.lunar.ui.ViewerTarget
import com.ryanjames.lunar.ui.ImportScreen
import com.ryanjames.lunar.ui.LibraryScreen
import com.ryanjames.lunar.ui.LunarTheme
import com.ryanjames.lunar.ui.SettingsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScreenSmokeTest {

    @get:Rule
    val rule = createComposeRule()

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

            rule.onNodeWithText("Random setlist").performClick()
            rule.onNodeWithText("Create a random setlist").assertIsDisplayed()
            rule.onNodeWithText("Cancel").performClick()

            rule.onNodeWithText("Setlists").performClick()
            rule.onNodeWithText("Morning rehearsal").assertIsDisplayed()
            rule.onNodeWithText("Open").performClick()
            rule.onNodeWithText("Saved automatically. Delete it whenever you no longer need it.").assertIsDisplayed()
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
                )
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
            rule.onNodeWithText("Add local source").assertIsDisplayed()
            rule.onNodeWithText("Add cloud source").assertIsDisplayed()
            rule.onNodeWithText("Add local source").performClick()
            rule.onNodeWithText("Choose how to import local PDFs or image files into your library. Matching .json metadata sidecars are imported automatically.").assertIsDisplayed()
            rule.onNodeWithText("Pick PDFs, PNGs, JPGs, or JPEGs. Matching .json sidecars are detected automatically.").assertIsDisplayed()
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

            rule.onNodeWithText("Main Drive library").assertIsDisplayed()
            rule.onNodeWithText("Edit").assertIsDisplayed()
            rule.onNodeWithText("Edit").performClick()
            rule.onNodeWithText("Edit cloud source").assertIsDisplayed()
            rule.onNodeWithText("Refresh token").assertIsDisplayed()
            rule.onNodeWithText("Save").assertIsDisplayed()
        }
    }

    @Test
    fun composeNavigationOpensPlaceholderWorkspace() {
        runBlocking {
            val runtime = createTestPlatformRuntime()
            val appState = createTestLunarAppState(
                scope = backgroundScope(),
                runtime = runtime,
            )

            rule.setContent {
                var selectedSection by mutableStateOf(AppSection.LIBRARY)

                LunarTheme(theme = AppColorTheme.OCEAN) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        BottomNavigationPanel(
                            selectedSection = selectedSection,
                            onSelectSection = {
                                selectedSection = it
                                appState.selectSection(it)
                            },
                        )

                        if (selectedSection == AppSection.COMPOSE) {
                            ComposeScreen(
                                appState = appState,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            rule.onNodeWithText("Compose").assertIsDisplayed()
            rule.runOnIdle {
                assertTrue(
                    rule.onAllNodesWithText("Metronome").fetchSemanticsNodes().isEmpty()
                )
            }
            rule.onNodeWithText("Compose").performClick()
            rule.onNodeWithText("Internal renderer preview").assertIsDisplayed()
            rule.runOnIdle {
                assertTrue(
                    rule.onAllNodesWithContentDescription("Notation preview canvas").fetchSemanticsNodes().isNotEmpty()
                )
                assertTrue(
                    rule.onAllNodesWithText("Why this step matters").fetchSemanticsNodes().isNotEmpty()
                )
                assertTrue(
                    rule.onAllNodesWithText("Back to library").fetchSemanticsNodes().isNotEmpty()
                )
            }
            rule.runOnIdle {
                assertEquals(AppSection.COMPOSE, appState.selectedSection)
            }
        }
    }
}

private fun backgroundScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
