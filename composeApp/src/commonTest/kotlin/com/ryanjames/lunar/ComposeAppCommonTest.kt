package com.ryanjames.lunar

import com.ryanjames.lunar.library.data.CloudGoogleDriveSource
import com.ryanjames.lunar.library.data.CloudPathStrategy
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.GoogleDriveImportRoot
import com.ryanjames.lunar.library.data.GoogleDriveStorageSettings
import com.ryanjames.lunar.library.data.InMemoryLibraryStorage
import com.ryanjames.lunar.library.data.InMemorySourceRegistry
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.platform.PdfDocumentInfo
import com.ryanjames.lunar.platform.PdfPageRenderer
import com.ryanjames.lunar.platform.RenderedPdfPage
import com.ryanjames.lunar.platform.SelectedCoverImage
import com.ryanjames.lunar.platform.SongbookBuildResult
import com.ryanjames.lunar.settings.AppColorTheme
import com.ryanjames.lunar.settings.AutoRefreshSchedule
import com.ryanjames.lunar.settings.InMemoryAppSettingsStore
import com.ryanjames.lunar.settings.ViewerShortcutAction
import com.ryanjames.lunar.sync.GoogleDriveAccessToken
import com.ryanjames.lunar.sync.GoogleDriveOAuthCoordinator
import com.ryanjames.lunar.sync.GoogleDriveOAuthSession
import com.ryanjames.lunar.sync.LibrarySyncManager
import com.ryanjames.lunar.sync.ManagedPdfStore
import com.ryanjames.lunar.sync.SyncHttpClient
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.LibraryBrowseMode
import com.ryanjames.lunar.ui.ViewerTarget
import com.ryanjames.lunar.ui.buildRandomSightReadingSelection
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ComposeAppCommonTest {

    @Test
    fun settingsStoreUpdatesAndResets() = runBlocking {
        val store = InMemoryAppSettingsStore()

        store.updateSettings {
            it.copy(
                theme = AppColorTheme.FOREST,
                autoRefreshSchedule = AutoRefreshSchedule.HOURLY,
                cloudConnectTimeoutSeconds = 30,
            )
        }

        assertEquals(AppColorTheme.FOREST, store.settings.value.theme)
        assertEquals(AutoRefreshSchedule.HOURLY, store.settings.value.autoRefreshSchedule)
        assertEquals(30, store.settings.value.cloudConnectTimeoutSeconds)

        store.reset()

        assertEquals(AppColorTheme.OCEAN, store.settings.value.theme)
        assertEquals(AutoRefreshSchedule.MINUTES_15, store.settings.value.autoRefreshSchedule)
        assertEquals(15, store.settings.value.cloudConnectTimeoutSeconds)
    }

    @Test
    fun viewerKeybindingsUpdateAndReset() = runBlocking {
        val runtime = createTestPlatformRuntime()
        val appState = createTestLunarAppState(
            scope = this,
            runtime = runtime,
        )

        appState.updateViewerKeybinding(
            action = ViewerShortcutAction.NEXT_PAGE,
            keyId = "L",
        )
        yield()

        assertEquals("L", runtime.settingsStore.settings.value.viewerKeybindings.nextPage)

        appState.clearViewerKeybinding(ViewerShortcutAction.ZOOM_IN)
        yield()

        assertEquals(null, runtime.settingsStore.settings.value.viewerKeybindings.zoomIn)

        appState.resetViewerKeybindings()
        yield()

        assertEquals("ArrowRight", runtime.settingsStore.settings.value.viewerKeybindings.nextPage)
        assertEquals("ArrowUp", runtime.settingsStore.settings.value.viewerKeybindings.zoomIn)
        assertEquals("ArrowDown", runtime.settingsStore.settings.value.viewerKeybindings.zoomOut)
        assertEquals(null, runtime.settingsStore.settings.value.viewerKeybindings.openRandomScore)
    }

    @Test
    fun closingFullscreenReturnsToExistingPreview() = runBlocking {
        val item = testSheetMusicItem(
            id = "moon_river",
            title = "Moon River",
        )
        val runtime = createTestPlatformRuntime(initialItems = listOf(item))
        val appState = createTestLunarAppState(
            scope = this,
            runtime = runtime,
        )

        appState.openPreview(item)
        appState.openFullscreen(item.id)

        assertEquals(ViewerTarget.Score(item.id), appState.previewTarget)
        assertEquals(ViewerTarget.Score(item.id), appState.fullscreenTarget)

        appState.closeFullscreen()

        assertEquals(ViewerTarget.Score(item.id), appState.previewTarget)
        assertEquals(null, appState.fullscreenTarget)
    }

    @Test
    fun openingRandomSheetInFullscreenReplacesBothViewerTargets() = runBlocking {
        val first = testSheetMusicItem(
            id = "moon_river",
            title = "Moon River",
        )
        val second = testSheetMusicItem(
            id = "blue_bossa",
            title = "Blue Bossa",
        )
        val runtime = createTestPlatformRuntime(initialItems = listOf(first, second))
        val appState = createTestLunarAppState(
            scope = this,
            runtime = runtime,
        )

        appState.openPreview(first)
        appState.openFullscreen(first.id)
        appState.openRandomSheetInCurrentViewer(listOf(second))

        assertEquals(ViewerTarget.Score(second.id), appState.previewTarget)
        assertEquals(ViewerTarget.Score(second.id), appState.fullscreenTarget)
        assertEquals("Opening a random sheet.", appState.bannerMessage)
    }

    @Test
    fun googleDriveRefreshContinuesAfterSinglePdfFailure() = runBlocking {
        val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())
        val manager = LibrarySyncManager(
            repository = repository,
            httpClient = FakeGoogleDriveHttpClient(),
            pdfStore = FakeManagedPdfStore(),
            renderer = FakePdfPageRenderer(),
        )
        val source = CloudGoogleDriveSource(
            id = "drive_source",
            label = "Google Drive",
            addedAtEpochMillis = 1L,
            settings = GoogleDriveStorageSettings(
                accessToken = "token",
                roots = listOf(
                    GoogleDriveImportRoot(
                        id = "root",
                        label = "Root",
                        folderId = "root-folder",
                        folderStrategy = CloudPathStrategy.FLAT,
                    )
                ),
            ),
        )

        repository.initialize()
        manager.initialize()
        manager.refreshSource(source)

        val syncedTitles = repository.library.value.items.map { it.title }
        assertEquals(listOf("Good Score"), syncedTitles)
        assertEquals(2, manager.state.value.discoveredResources)
        assertEquals(1, manager.state.value.processedResources)
        assertEquals(1, manager.state.value.syncErrors.size)
        assertTrue(
            manager.state.value.syncErrors.single().contains("Bad Score.pdf"),
            "Expected the failing file name to appear in the sync error log.",
        )
        assertTrue(
            manager.state.value.activityLog.any { it.contains("ready Good Score.pdf") },
            "Expected the successful file to keep syncing after the failed one.",
        )
    }

    @Test
    fun googleDriveRefreshRenewsExpiredRefreshTokenAndRetries() = runBlocking {
        val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())
        val source = CloudGoogleDriveSource(
            id = "drive_source",
            label = "Google Drive",
            addedAtEpochMillis = 1L,
            settings = GoogleDriveStorageSettings(
                clientId = "client-id",
                refreshToken = "expired-refresh-token",
                roots = listOf(
                    GoogleDriveImportRoot(
                        id = "root",
                        label = "Root",
                        folderId = "root-folder",
                        folderStrategy = CloudPathStrategy.FLAT,
                    )
                ),
            ),
        )
        val sourceRegistry = InMemorySourceRegistry(listOf(source))
        val oauth = TestGoogleDriveOAuthCoordinator(
            session = GoogleDriveOAuthSession(
                accessToken = "fresh-access-token",
                refreshToken = "fresh-refresh-token",
                expiresAtEpochMillis = 60_000L,
            )
        )
        val manager = LibrarySyncManager(
            repository = repository,
            httpClient = ExpiredRefreshTokenGoogleDriveHttpClient(),
            pdfStore = FakeManagedPdfStore(),
            renderer = FakePdfPageRenderer(),
            sourceRegistry = sourceRegistry,
            googleDriveOAuth = oauth,
        )

        repository.initialize()
        sourceRegistry.initialize()
        manager.initialize()
        manager.refreshSource(source)

        val syncedTitles = repository.library.value.items.map { it.title }
        assertEquals(listOf("Recovered Score"), syncedTitles)
        assertEquals(1, oauth.requestCount)
        val updatedSource = sourceRegistry.getSource(source.id) as CloudGoogleDriveSource
        assertEquals("fresh-refresh-token", updatedSource.settings.refreshToken)
        assertTrue(
            manager.state.value.activityLog.any { it.contains("Google rejected the saved refresh token") },
            "Expected the activity log to mention automatic Google Drive reconnection.",
        )
    }

    @Test
    fun updatingGoogleDriveSourceReplacesSavedRefreshToken() = runBlocking {
        val existingSource = CloudGoogleDriveSource(
            id = "drive_source",
            label = "Google Drive",
            addedAtEpochMillis = 1L,
            settings = GoogleDriveStorageSettings(
                clientId = "client-id",
                refreshToken = "old-refresh-token",
                accessToken = "stale-access-token",
                roots = listOf(
                    GoogleDriveImportRoot(
                        id = "root",
                        label = "Root",
                        folderId = "folder-123",
                        folderStrategy = CloudPathStrategy.FLAT,
                    )
                ),
            ),
        )
        val runtime = createTestPlatformRuntime(
            initialSources = listOf(existingSource),
        )
        val appState = createTestLunarAppState(
            scope = this,
            runtime = runtime,
        )

        appState.updateCloudSource(
            existingSource.copy(
                label = "Main Drive library",
                settings = existingSource.settings.copy(
                    refreshToken = "new-refresh-token",
                    accessToken = "should-be-cleared",
                ),
            )
        )
        repeat(10) {
            val updatedSource = runtime.sourceRegistry.getSource(existingSource.id) as? CloudGoogleDriveSource
            if (updatedSource?.label == "Main Drive library") {
                return@repeat
            }
            yield()
        }

        val updatedSource = runtime.sourceRegistry.getSource(existingSource.id) as CloudGoogleDriveSource
        assertEquals("Main Drive library", updatedSource.label)
        assertEquals("new-refresh-token", updatedSource.settings.refreshToken)
        assertEquals("", updatedSource.settings.accessToken)
        assertEquals(
            "Cloud source updated. Press Refresh to sync with the new Google Drive token.",
            appState.bannerMessage,
        )
    }

    @Test
    fun randomSightReadingSelectionClampsRequestedSize() {
        val items = listOf(
            testSheetMusicItem("one"),
            testSheetMusicItem("two"),
            testSheetMusicItem("three"),
        )

        val result = buildRandomSightReadingSelection(
            items = items,
            requestedCount = 10,
            random = Random(7),
        )

        assertEquals(3, result.size)
        assertEquals(3, result.map { it.id }.distinct().size)
    }

    @Test
    fun randomSightReadingSelectionReturnsEmptyForEmptyLibrary() {
        val result = buildRandomSightReadingSelection(
            items = emptyList(),
            requestedCount = 4,
            random = Random(7),
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun leavingSetlistsBrowseClosesOpenSetlistDetail() = runBlocking {
        val appState = createTestLunarAppState(this)

        appState.openSetlist("saved-setlist")
        appState.updateBrowseMode(LibraryBrowseMode.BY_COLLECTION)

        assertEquals(LibraryBrowseMode.BY_COLLECTION, appState.browseMode)
        assertEquals(null, appState.selectedSetlistId)
        assertFalse(appState.temporarySetlistOpen)
    }

    @Test
    fun openingTemporarySetlistClearsSavedSetlistSelection() = runBlocking {
        val appState = createTestLunarAppState(this)

        appState.createTemporaryRandomSetlist(
            items = listOf(testSheetMusicItem("one"), testSheetMusicItem("two")),
            requestedCount = 2,
        )
        appState.openSetlist("saved-setlist")

        appState.openTemporarySetlist()

        assertEquals(LibraryBrowseMode.SETLISTS, appState.browseMode)
        assertEquals(null, appState.selectedSetlistId)
        assertTrue(appState.temporarySetlistOpen)
    }

    @Test
    fun leavingLibraryClosesTransientSelectionAndDialogs() = runBlocking {
        val appState = createTestLunarAppState(this)

        appState.toggleScoreSelection("score-1")
        appState.showSetlistPicker()
        appState.showSongbookPicker()
        appState.createTemporaryRandomSetlist(
            items = listOf(testSheetMusicItem("one")),
            requestedCount = 1,
        )
        appState.showRandomSetlistBuilder()
        appState.showSaveTemporarySetlistDialog()

        appState.selectSection(AppSection.IMPORT)

        assertEquals(AppSection.IMPORT, appState.selectedSection)
        assertTrue(appState.selectedScoreIds.isEmpty())
        assertFalse(appState.setlistPickerVisible)
        assertFalse(appState.songbookPickerVisible)
        assertFalse(appState.randomSetlistBuilderVisible)
        assertFalse(appState.saveTemporarySetlistDialogVisible)
    }

    @Test
    fun creatingSongbookBuildsCombinedPdfAndOpensSongbookViewer() = runBlocking {
        val items = listOf(
            testSheetMusicItem(id = "one", title = "One"),
            testSheetMusicItem(id = "two", title = "Two"),
        )
        val runtime = createTestPlatformRuntime(
            initialItems = items,
            songbookBuilder = TestSongbookPdfBuilder { songbookName, appendedDocumentPaths, existingSongbookPath, coverImage ->
                assertEquals("Evening book", songbookName)
                assertEquals(items.map { it.document.storedPath }, appendedDocumentPaths)
                assertEquals(null, existingSongbookPath)
                assertNotNull(coverImage)
                assertEquals("cover.png", coverImage.displayName)
                SongbookBuildResult(
                    document = PdfDocumentReference(
                        storedPath = "/songbooks/evening-book.pdf",
                        originalFileName = "Evening book.pdf",
                    ),
                    pageCount = 9,
                )
            },
            songbookCreationSupported = true,
            songbookCoverImageSupported = true,
        )
        val appState = createTestLunarAppState(
            scope = this,
            runtime = runtime,
        )

        appState.createSongbook(
            name = "Evening book",
            items = items,
            coverImage = SelectedCoverImage(
                displayName = "cover.png",
                bytes = byteArrayOf(1, 2, 3),
            ),
        )
        repeat(10) {
            if (runtime.repository.library.value.songbooks.isNotEmpty()) {
                return@repeat
            }
            yield()
        }

        val songbook = runtime.repository.library.value.songbooks.single()
        assertEquals("Evening book", songbook.name)
        assertEquals(items.map { it.id }, songbook.itemIds)
        assertEquals("/songbooks/evening-book.pdf", songbook.document.storedPath)
        assertEquals(LibraryBrowseMode.SONGBOOKS, appState.browseMode)
        assertEquals(ViewerTarget.Songbook(songbook.id), appState.previewTarget)
        assertEquals("Saved \"Evening book\".", appState.bannerMessage)
    }

    @Test
    fun addingSelectionToSongbookUsesExistingMergedPdf() = runBlocking {
        val existingItems = listOf(
            testSheetMusicItem(id = "one", title = "One"),
            testSheetMusicItem(id = "two", title = "Two"),
            testSheetMusicItem(id = "three", title = "Three"),
        )
        val existingSongbook = testLibrarySongbook(
            id = "songbook_evening",
            name = "Evening book",
            itemIds = listOf(existingItems[0].id),
            pageCount = 4,
        )
        val runtime = createTestPlatformRuntime(
            initialItems = existingItems,
            initialSongbooks = listOf(existingSongbook),
            songbookBuilder = TestSongbookPdfBuilder { songbookName, appendedDocumentPaths, existingSongbookPath, coverImage ->
                assertEquals("Evening book", songbookName)
                assertEquals(existingSongbook.document.storedPath, existingSongbookPath)
                assertEquals(listOf(existingItems[1].document.storedPath), appendedDocumentPaths)
                assertEquals(null, coverImage)
                SongbookBuildResult(
                    document = PdfDocumentReference(
                        storedPath = "/songbooks/evening-book-v2.pdf",
                        originalFileName = "Evening book.pdf",
                    ),
                    pageCount = 7,
                )
            },
            songbookCreationSupported = true,
        )
        val appState = createTestLunarAppState(
            scope = this,
            runtime = runtime,
        )

        appState.addSelectionToSongbook(
            songbookId = existingSongbook.id,
            items = listOf(existingItems[1]),
        )
        repeat(10) {
            if (runtime.repository.library.value.songbooks.single().document.storedPath == "/songbooks/evening-book-v2.pdf") {
                return@repeat
            }
            yield()
        }

        val updatedSongbook = runtime.repository.library.value.songbooks.single()
        assertEquals(listOf(existingItems[0].id, existingItems[1].id), updatedSongbook.itemIds)
        assertEquals("/songbooks/evening-book-v2.pdf", updatedSongbook.document.storedPath)
        assertEquals(ViewerTarget.Songbook(existingSongbook.id), appState.previewTarget)
        assertEquals("Updated \"Evening book\".", appState.bannerMessage)
    }

    @Test
    fun deletingSongbookClearsFullscreenTarget() = runBlocking {
        val existingSongbook = testLibrarySongbook(
            id = "songbook_delete",
            name = "Delete me",
            itemIds = listOf("one"),
        )
        val runtime = createTestPlatformRuntime(
            initialSongbooks = listOf(existingSongbook),
        )
        val appState = createTestLunarAppState(
            scope = this,
            runtime = runtime,
        )
        appState.openSongbookFullscreen(existingSongbook.id)
        appState.requestDeleteSongbook(existingSongbook.id)

        appState.confirmDeleteSongbook()
        repeat(10) {
            if (runtime.repository.library.value.songbooks.isEmpty()) {
                return@repeat
            }
            yield()
        }

        assertTrue(runtime.repository.library.value.songbooks.isEmpty())
        assertEquals(null, appState.fullscreenTarget)
        assertEquals("Songbook deleted.", appState.bannerMessage)
    }

    @Test
    fun downloadingScoreExportsManagedPdfAndShowsBanner() = runBlocking {
        val item = testSheetMusicItem(
            id = "moon_river",
            title = "Moon River",
        )
        val runtime = createTestPlatformRuntime(
            initialItems = listOf(item),
            pdfExporter = TestPdfDocumentExporter { documentPath, suggestedFileName ->
                assertEquals(item.document.storedPath, documentPath)
                assertEquals(item.document.originalFileName, suggestedFileName)
                "Downloads/$suggestedFileName"
            },
            scoreDownloadSupported = true,
        )
        val appState = createTestLunarAppState(
            scope = this,
            runtime = runtime,
        )

        appState.downloadScore(item)
        repeat(5) {
            if (appState.bannerMessage != null) {
                return@repeat
            }
            yield()
        }

        assertEquals("Saved \"Moon River\" to Downloads/Moon River.pdf.", appState.bannerMessage)
    }

    @Test
    fun downloadingScoreReportsExporterFailure() = runBlocking {
        val item = testSheetMusicItem(
            id = "moon_river",
            title = "Moon River",
        )
        val runtime = createTestPlatformRuntime(
            initialItems = listOf(item),
            pdfExporter = TestPdfDocumentExporter { _, _ ->
                error("Downloads folder unavailable.")
            },
            scoreDownloadSupported = true,
        )
        val appState = createTestLunarAppState(
            scope = this,
            runtime = runtime,
        )

        appState.downloadScore(item)
        repeat(5) {
            if (appState.bannerMessage != null) {
                return@repeat
            }
            yield()
        }

        assertEquals("Downloads folder unavailable.", appState.bannerMessage)
    }
}

private class FakeGoogleDriveHttpClient : SyncHttpClient {
    override suspend fun getText(url: String, headers: Map<String, String>): String {
        return if (url.contains("drive/v3/files?")) {
            """
            {
              "files": [
                {
                  "id": "bad-file",
                  "name": "Bad Score.pdf",
                  "mimeType": "application/pdf",
                  "modifiedTime": "2026-04-12T00:00:00Z"
                },
                {
                  "id": "good-file",
                  "name": "Good Score.pdf",
                  "mimeType": "application/pdf",
                  "modifiedTime": "2026-04-12T00:00:01Z"
                }
              ]
            }
            """.trimIndent()
        } else {
            error("Unexpected GET text URL: $url")
        }
    }

    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray {
        return when {
            url.contains("/bad-file?") -> error("Simulated download failure")
            url.contains("/good-file?") -> byteArrayOf(1, 2, 3, 4)
            else -> error("Unexpected GET bytes URL: $url")
        }
    }
}

private class ExpiredRefreshTokenGoogleDriveHttpClient : SyncHttpClient {
    override suspend fun getText(url: String, headers: Map<String, String>): String {
        assertEquals("Bearer fresh-access-token", headers["Authorization"])
        return """
            {
              "files": [
                {
                  "id": "recovered-file",
                  "name": "Recovered Score.pdf",
                  "mimeType": "application/pdf",
                  "modifiedTime": "2026-04-12T00:00:01Z"
                }
              ]
            }
        """.trimIndent()
    }

    override suspend fun getBytes(url: String, headers: Map<String, String>): ByteArray {
        assertEquals("Bearer fresh-access-token", headers["Authorization"])
        return byteArrayOf(1, 2, 3, 4)
    }

    override suspend fun postForm(url: String, formBody: String, headers: Map<String, String>): String {
        if (formBody.contains("refresh_token=expired-refresh-token")) {
            throw IOException(
                "HTTP 400 from POST https://oauth2.googleapis.com/token: {\"error\":\"invalid_grant\",\"error_description\":\"Bad Request\"}"
            )
        }
        error("Unexpected POST form request: $url")
    }
}

private class TestGoogleDriveOAuthCoordinator(
    private val session: GoogleDriveOAuthSession,
) : GoogleDriveOAuthCoordinator {
    var requestCount: Int = 0
        private set

    override val isSupported: Boolean = true

    override suspend fun getOrFetchRefreshToken(settings: GoogleDriveStorageSettings): GoogleDriveOAuthSession {
        requestCount += 1
        assertEquals("", settings.refreshToken)
        return session
    }

    override suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String,
    ): GoogleDriveAccessToken = error("refreshAccessToken should not be called in this test")
}

private class FakeManagedPdfStore : ManagedPdfStore {
    override suspend fun savePdf(originalFileName: String, contents: ByteArray): String =
        "/managed/$originalFileName"

    override suspend fun exists(storedPath: String): Boolean = false
}

private class FakePdfPageRenderer : PdfPageRenderer {
    override suspend fun inspect(documentPath: String): PdfDocumentInfo = PdfDocumentInfo(pageCount = 1)

    override suspend fun renderPage(
        documentPath: String,
        pageIndex: Int,
        targetWidth: Int,
    ): RenderedPdfPage? = null
}
