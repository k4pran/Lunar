package com.ryanjames.lunar

import com.ryanjames.lunar.library.data.CloudGoogleDriveSource
import com.ryanjames.lunar.library.data.CloudPathStrategy
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.GoogleDriveImportRoot
import com.ryanjames.lunar.library.data.GoogleDriveStorageSettings
import com.ryanjames.lunar.library.data.InMemoryLibraryStorage
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.platform.PdfDocumentInfo
import com.ryanjames.lunar.platform.PdfPageRenderer
import com.ryanjames.lunar.platform.RenderedPdfPage
import com.ryanjames.lunar.platform.SelectedCoverImage
import com.ryanjames.lunar.platform.SongbookBuildResult
import com.ryanjames.lunar.settings.AppColorTheme
import com.ryanjames.lunar.settings.AutoRefreshSchedule
import com.ryanjames.lunar.settings.InMemoryAppSettingsStore
import com.ryanjames.lunar.sync.LibrarySyncManager
import com.ryanjames.lunar.sync.ManagedPdfStore
import com.ryanjames.lunar.sync.SyncHttpClient
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.LibraryBrowseMode
import com.ryanjames.lunar.ui.ViewerTarget
import com.ryanjames.lunar.ui.buildRandomSightReadingSelection
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
