package com.ryanjames.lunar

import com.ryanjames.lunar.library.data.CloudGoogleDriveSource
import com.ryanjames.lunar.library.data.CloudPathStrategy
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.GoogleDriveImportRoot
import com.ryanjames.lunar.library.data.GoogleDriveStorageSettings
import com.ryanjames.lunar.library.data.InMemoryLibraryStorage
import com.ryanjames.lunar.platform.PdfDocumentInfo
import com.ryanjames.lunar.platform.PdfPageRenderer
import com.ryanjames.lunar.platform.RenderedPdfPage
import com.ryanjames.lunar.sync.LibrarySyncManager
import com.ryanjames.lunar.sync.ManagedPdfStore
import com.ryanjames.lunar.sync.SyncHttpClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeAppCommonTest {

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
