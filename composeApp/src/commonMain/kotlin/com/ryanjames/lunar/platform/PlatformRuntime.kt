package com.ryanjames.lunar.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.InMemoryLibraryStorage
import com.ryanjames.lunar.library.data.InMemorySourceRegistry
import com.ryanjames.lunar.library.data.NoOpStoredDocumentCleaner
import com.ryanjames.lunar.library.data.NoOpStoredDocumentFingerprinter
import com.ryanjames.lunar.library.data.SheetMusicRepository
import com.ryanjames.lunar.library.data.SourceRegistry
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.sync.GoogleDriveOAuthCoordinator
import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import com.ryanjames.lunar.settings.AppSettingsStore
import com.ryanjames.lunar.settings.InMemoryAppSettingsStore
import com.ryanjames.lunar.sync.LibrarySyncManager
import com.ryanjames.lunar.sync.UnsupportedGoogleDriveOAuthCoordinator
import com.ryanjames.lunar.sync.rememberNoOpLibrarySyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.round

data class PlatformRuntime(
    val platformName: String,
    val capabilities: PlatformCapabilities,
    val repository: SheetMusicRepository,
    val importer: PdfImporter,
    val renderer: PdfPageRenderer,
    val pdfExporter: PdfDocumentExporter,
    val songbookBuilder: SongbookPdfBuilder,
    val coverImagePicker: CoverImagePicker,
    val syncManager: LibrarySyncManager,
    val sourceRegistry: SourceRegistry,
    val settingsStore: AppSettingsStore,
    val googleDriveOAuth: GoogleDriveOAuthCoordinator,
    val cacheInspector: LibraryCacheInspector,
)

data class PlatformCapabilities(
    val fileImportSupported: Boolean = true,
    val folderImportSupported: Boolean = false,
    val localImageImportSupported: Boolean = false,
    val lilyPondImportSupported: Boolean = false,
    val museScoreImportSupported: Boolean = false,
    val permissionTrackingSupported: Boolean = false,
    val inAppViewingSupported: Boolean = true,
    val scoreDownloadSupported: Boolean = false,
    val songbookCreationSupported: Boolean = false,
    val songbookCoverImageSupported: Boolean = false,
    val statusLine: String = "Local metadata only",
)

data class LibraryCacheSnapshot(
    val storageLabel: String,
    val cacheRootPath: String? = null,
    val metadataCached: Boolean = false,
    val sourceRegistryCached: Boolean = false,
    val cachedPdfCount: Int = 0,
    val cachedPdfBytes: Long = 0L,
) {
    val cachedPdfBytesLabel: String
        get() = formatStorageSize(cachedPdfBytes)

    val offlineLibraryReady: Boolean
        get() = metadataCached

    val offlineViewerReady: Boolean
        get() = cachedPdfCount > 0
}

interface LibraryCacheInspector {
    suspend fun inspect(): LibraryCacheSnapshot
}

class UnsupportedLibraryCacheInspector(
    private val storageLabel: String,
) : LibraryCacheInspector {
    override suspend fun inspect(): LibraryCacheSnapshot = LibraryCacheSnapshot(
        storageLabel = storageLabel,
    )
}

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

data class SelectedCoverImage(
    val displayName: String,
    val bytes: ByteArray,
)

data class SongbookBuildResult(
    val document: PdfDocumentReference,
    val pageCount: Int? = null,
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

interface PdfDocumentExporter {
    suspend fun export(
        documentPath: String,
        suggestedFileName: String,
    ): String
}

interface SongbookPdfBuilder {
    suspend fun buildSongbook(
        songbookName: String,
        appendedDocumentPaths: List<String>,
        existingSongbookPath: String? = null,
        coverImage: SelectedCoverImage? = null,
    ): SongbookBuildResult
}

interface CoverImagePicker {
    suspend fun pickCoverImage(): SelectedCoverImage?
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

object UnsupportedPdfDocumentExporter : PdfDocumentExporter {
    override suspend fun export(
        documentPath: String,
        suggestedFileName: String,
    ): String = throw UnsupportedOperationException("Score downloads are unavailable on this target.")
}

object UnsupportedSongbookPdfBuilder : SongbookPdfBuilder {
    override suspend fun buildSongbook(
        songbookName: String,
        appendedDocumentPaths: List<String>,
        existingSongbookPath: String?,
        coverImage: SelectedCoverImage?,
    ): SongbookBuildResult = throw UnsupportedOperationException(
        "Songbook creation is unavailable on this target."
    )
}

object UnsupportedCoverImagePicker : CoverImagePicker {
    override suspend fun pickCoverImage(): SelectedCoverImage? =
        throw UnsupportedOperationException("Cover image selection is unavailable on this target.")
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
            storedDocumentFingerprinter = NoOpStoredDocumentFingerprinter,
        )
    }
    val syncManager = remember(repository) {
        rememberNoOpLibrarySyncManager(
            repository = repository,
            renderer = UnavailablePdfPageRenderer,
        )
    }

    return remember(platformName, statusLine, importMessage, repository, syncManager) {
        PlatformRuntime(
            platformName = platformName,
            capabilities = PlatformCapabilities(
                fileImportSupported = false,
                folderImportSupported = false,
                permissionTrackingSupported = false,
                inAppViewingSupported = false,
                scoreDownloadSupported = false,
                songbookCreationSupported = false,
                songbookCoverImageSupported = false,
                statusLine = statusLine,
            ),
            repository = repository,
            importer = UnsupportedPdfImporter(importMessage),
            renderer = UnavailablePdfPageRenderer,
            pdfExporter = UnsupportedPdfDocumentExporter,
            songbookBuilder = UnsupportedSongbookPdfBuilder,
            coverImagePicker = UnsupportedCoverImagePicker,
            syncManager = syncManager,
            sourceRegistry = InMemorySourceRegistry(),
            settingsStore = InMemoryAppSettingsStore(),
            googleDriveOAuth = UnsupportedGoogleDriveOAuthCoordinator,
            cacheInspector = UnsupportedLibraryCacheInspector(statusLine),
        )
    }
}

@Composable
expect fun rememberPlatformRuntime(): PlatformRuntime

fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"

    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }

    val formatted = if (value >= 10 || unitIndex == 0) {
        value.toInt().toString()
    } else {
        ((round(value * 10) / 10.0)).toString()
    }
    return "$formatted ${units[unitIndex]}"
}
