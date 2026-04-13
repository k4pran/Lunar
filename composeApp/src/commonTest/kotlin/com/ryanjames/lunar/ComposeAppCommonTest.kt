package com.ryanjames.lunar

import com.ryanjames.lunar.library.data.CloudGoogleDriveSource
import com.ryanjames.lunar.library.data.CloudPathStrategy
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.GoogleDriveImportRoot
import com.ryanjames.lunar.library.data.GoogleDriveStorageSettings
import com.ryanjames.lunar.library.data.InMemoryLibraryStorage
import com.ryanjames.lunar.library.data.InMemorySourceRegistry
import com.ryanjames.lunar.platform.PlatformCapabilities
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.platform.PdfDocumentInfo
import com.ryanjames.lunar.platform.PdfPageRenderer
import com.ryanjames.lunar.platform.RenderedPdfPage
import com.ryanjames.lunar.platform.UnavailablePdfPageRenderer
import com.ryanjames.lunar.platform.UnsupportedLibraryCacheInspector
import com.ryanjames.lunar.platform.UnsupportedPdfImporter
import com.ryanjames.lunar.settings.AppColorTheme
import com.ryanjames.lunar.settings.AutoRefreshSchedule
import com.ryanjames.lunar.settings.InMemoryAppSettingsStore
import com.ryanjames.lunar.sync.LibrarySyncManager
import com.ryanjames.lunar.sync.ManagedPdfStore
import com.ryanjames.lunar.sync.SyncHttpClient
import com.ryanjames.lunar.sync.UnsupportedGoogleDriveOAuthCoordinator
import com.ryanjames.lunar.sync.rememberNoOpLibrarySyncManager
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.LibraryBrowseMode
import com.ryanjames.lunar.ui.LunarAppState
import com.ryanjames.lunar.ui.buildRandomSightReadingSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
            randomSightReadingItem("one"),
            randomSightReadingItem("two"),
            randomSightReadingItem("three"),
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
            items = listOf(randomSightReadingItem("one"), randomSightReadingItem("two")),
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
        appState.createTemporaryRandomSetlist(
            items = listOf(randomSightReadingItem("one")),
            requestedCount = 1,
        )
        appState.showRandomSetlistBuilder()
        appState.showSaveTemporarySetlistDialog()

        appState.selectSection(AppSection.IMPORT)

        assertEquals(AppSection.IMPORT, appState.selectedSection)
        assertTrue(appState.selectedScoreIds.isEmpty())
        assertFalse(appState.setlistPickerVisible)
        assertFalse(appState.randomSetlistBuilderVisible)
        assertFalse(appState.saveTemporarySetlistDialogVisible)
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

private fun randomSightReadingItem(id: String) = com.ryanjames.lunar.library.model.SheetMusicItem(
    id = id,
    title = id.replaceFirstChar(Char::uppercase),
    composer = "Composer",
    document = com.ryanjames.lunar.library.model.PdfDocumentReference(
        storedPath = "/scores/$id.pdf",
        originalFileName = "$id.pdf",
    ),
    dateAddedEpochMillis = 1L,
)

private suspend fun createTestLunarAppState(scope: CoroutineScope): LunarAppState {
    val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())
    repository.initialize()

    val runtime = PlatformRuntime(
        platformName = "Test",
        capabilities = PlatformCapabilities(),
        repository = repository,
        importer = UnsupportedPdfImporter("Import unavailable in tests."),
        renderer = UnavailablePdfPageRenderer,
        syncManager = rememberNoOpLibrarySyncManager(
            repository = repository,
            renderer = UnavailablePdfPageRenderer,
        ),
        sourceRegistry = InMemorySourceRegistry(),
        settingsStore = InMemoryAppSettingsStore(),
        googleDriveOAuth = UnsupportedGoogleDriveOAuthCoordinator,
        cacheInspector = UnsupportedLibraryCacheInspector("Test cache"),
    )

    return LunarAppState(runtime = runtime, scope = scope)
}
