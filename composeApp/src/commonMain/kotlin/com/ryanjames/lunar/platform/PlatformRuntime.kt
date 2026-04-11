package com.ryanjames.lunar.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.InMemoryLibraryStorage
import com.ryanjames.lunar.library.data.NoOpStoredDocumentCleaner
import com.ryanjames.lunar.library.data.SheetMusicRepository
import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlatformRuntime(
    val platformName: String,
    val capabilities: PlatformCapabilities,
    val repository: SheetMusicRepository,
    val importer: PdfImporter,
    val renderer: PdfPageRenderer,
)

data class PlatformCapabilities(
    val fileImportSupported: Boolean = true,
    val folderImportSupported: Boolean = false,
    val permissionTrackingSupported: Boolean = false,
    val inAppViewingSupported: Boolean = true,
    val statusLine: String = "Local metadata only",
)

enum class ImportPermissionKind {
    FILE,
    FOLDER,
}

data class ImportPermissionGrant(
    val id: String,
    val label: String,
    val kind: ImportPermissionKind,
    val persisted: Boolean = true,
    val detail: String? = null,
)

data class ImporterState(
    val trackedPermissions: List<ImportPermissionGrant> = emptyList(),
    val statusMessage: String? = null,
)

data class ImportRequestResult(
    val documents: List<ImportedPdfDescriptor>,
    val notice: String? = null,
)

data class PdfDocumentInfo(
    val pageCount: Int,
)

data class RenderedPdfPage(
    val pageIndex: Int,
    val pageCount: Int,
    val aspectRatio: Float,
    val image: ImageBitmap,
)

interface PdfImporter {
    val state: StateFlow<ImporterState>

    suspend fun importPdfFiles(): ImportRequestResult

    suspend fun importPdfFolder(): ImportRequestResult
}

interface PdfPageRenderer {
    suspend fun inspect(documentPath: String): PdfDocumentInfo?

    suspend fun renderPage(
        documentPath: String,
        pageIndex: Int,
        targetWidth: Int = 1400,
    ): RenderedPdfPage?
}

class UnsupportedPdfImporter(
    private val message: String,
) : PdfImporter {
    private val importerState = MutableStateFlow(
        ImporterState(statusMessage = message)
    )

    override val state: StateFlow<ImporterState> = importerState.asStateFlow()

    override suspend fun importPdfFiles(): ImportRequestResult = ImportRequestResult(
        documents = emptyList(),
        notice = message,
    )

    override suspend fun importPdfFolder(): ImportRequestResult = ImportRequestResult(
        documents = emptyList(),
        notice = message,
    )
}

object UnavailablePdfPageRenderer : PdfPageRenderer {
    override suspend fun inspect(documentPath: String): PdfDocumentInfo? = null

    override suspend fun renderPage(
        documentPath: String,
        pageIndex: Int,
        targetWidth: Int,
    ): RenderedPdfPage? = null
}

@Composable
fun rememberUnsupportedPlatformRuntime(
    platformName: String,
    statusLine: String,
    importMessage: String,
): PlatformRuntime {
    val repository = remember {
        DefaultSheetMusicRepository(
            storage = InMemoryLibraryStorage(),
            storedDocumentCleaner = NoOpStoredDocumentCleaner,
        )
    }

    return remember(platformName, statusLine, importMessage, repository) {
        PlatformRuntime(
            platformName = platformName,
            capabilities = PlatformCapabilities(
                fileImportSupported = false,
                folderImportSupported = false,
                permissionTrackingSupported = false,
                inAppViewingSupported = false,
                statusLine = statusLine,
            ),
            repository = repository,
            importer = UnsupportedPdfImporter(importMessage),
            renderer = UnavailablePdfPageRenderer,
        )
    }
}

@Composable
expect fun rememberPlatformRuntime(): PlatformRuntime
