package com.ryanjames.lunar

import com.ryanjames.lunar.composition.CompositionDraftStore
import com.ryanjames.lunar.composition.InMemoryCompositionDraftStore
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.InMemoryLibraryStorage
import com.ryanjames.lunar.library.data.InMemorySourceRegistry
import com.ryanjames.lunar.library.data.LibrarySource
import com.ryanjames.lunar.library.data.LocalFilesSource
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.library.model.LibrarySetlist
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.platform.CoverImagePicker
import com.ryanjames.lunar.platform.CompositionPdfImporter
import com.ryanjames.lunar.platform.LibraryCacheInspector
import com.ryanjames.lunar.platform.LibraryCacheSnapshot
import com.ryanjames.lunar.platform.LilyPondLiveRenderer
import com.ryanjames.lunar.platform.PlatformCapabilities
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.platform.PdfDocumentExporter
import com.ryanjames.lunar.platform.PdfPageRenderer
import com.ryanjames.lunar.platform.SelectedCoverImage
import com.ryanjames.lunar.platform.SongbookBuildResult
import com.ryanjames.lunar.platform.SongbookPdfBuilder
import com.ryanjames.lunar.platform.UnavailablePdfPageRenderer
import com.ryanjames.lunar.platform.UnsupportedCoverImagePicker
import com.ryanjames.lunar.platform.UnsupportedLilyPondLiveRenderer
import com.ryanjames.lunar.platform.UnsupportedCompositionPdfImporter
import com.ryanjames.lunar.platform.UnsupportedPdfDocumentExporter
import com.ryanjames.lunar.platform.UnsupportedPdfImporter
import com.ryanjames.lunar.platform.UnsupportedSongbookPdfBuilder
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.InMemoryAppSettingsStore
import com.ryanjames.lunar.sync.UnsupportedGoogleDriveOAuthCoordinator
import com.ryanjames.lunar.sync.rememberNoOpLibrarySyncManager
import com.ryanjames.lunar.ui.LunarAppState
import kotlinx.coroutines.CoroutineScope

internal suspend fun createTestPlatformRuntime(
    initialItems: List<SheetMusicItem> = emptyList(),
    initialSetlists: List<LibrarySetlist> = emptyList(),
    initialSongbooks: List<LibrarySongbook> = emptyList(),
    initialSources: List<LibrarySource> = emptyList(),
    initialSettings: AppSettings = AppSettings(),
    cacheSnapshot: LibraryCacheSnapshot = LibraryCacheSnapshot(storageLabel = "Test cache"),
    renderer: PdfPageRenderer = UnavailablePdfPageRenderer,
    lilyPondLiveRenderer: LilyPondLiveRenderer = UnsupportedLilyPondLiveRenderer,
    pdfExporter: PdfDocumentExporter = UnsupportedPdfDocumentExporter,
    scoreDownloadSupported: Boolean = false,
    localImageImportSupported: Boolean = false,
    lilyPondImportSupported: Boolean = false,
    lilyPondLiveViewingSupported: Boolean = false,
    museScoreImportSupported: Boolean = false,
    songbookBuilder: SongbookPdfBuilder = UnsupportedSongbookPdfBuilder,
    coverImagePicker: CoverImagePicker = UnsupportedCoverImagePicker,
    songbookCreationSupported: Boolean = false,
    songbookCoverImageSupported: Boolean = false,
    compositionStore: CompositionDraftStore = InMemoryCompositionDraftStore(),
    compositionPdfImporter: CompositionPdfImporter = UnsupportedCompositionPdfImporter,
): PlatformRuntime {
    val repository = DefaultSheetMusicRepository(
        storage = InMemoryLibraryStorage(
            initialItems = initialItems,
            initialSetlists = initialSetlists,
            initialSongbooks = initialSongbooks,
        )
    )
    repository.initialize()

    val settingsStore = InMemoryAppSettingsStore(initialSettings)
    settingsStore.initialize()

    val sourceRegistry = InMemorySourceRegistry(initialSources)
    sourceRegistry.initialize()

    compositionStore.initialize()

    return PlatformRuntime(
        platformName = "Test",
        capabilities = PlatformCapabilities(
            scoreDownloadSupported = scoreDownloadSupported,
            localImageImportSupported = localImageImportSupported,
            lilyPondImportSupported = lilyPondImportSupported,
            lilyPondLiveViewingSupported = lilyPondLiveViewingSupported,
            museScoreImportSupported = museScoreImportSupported,
            songbookCreationSupported = songbookCreationSupported,
            songbookCoverImageSupported = songbookCoverImageSupported,
        ),
        repository = repository,
        importer = UnsupportedPdfImporter("Import unavailable in tests."),
        renderer = renderer,
        lilyPondLiveRenderer = lilyPondLiveRenderer,
        pdfExporter = pdfExporter,
        songbookBuilder = songbookBuilder,
        coverImagePicker = coverImagePicker,
        syncManager = rememberNoOpLibrarySyncManager(
            repository = repository,
            renderer = renderer,
        ),
        sourceRegistry = sourceRegistry,
        compositionStore = compositionStore,
        compositionPdfImporter = compositionPdfImporter,
        settingsStore = settingsStore,
        googleDriveOAuth = UnsupportedGoogleDriveOAuthCoordinator,
        cacheInspector = StaticLibraryCacheInspector(cacheSnapshot),
    )
}

internal suspend fun createTestLunarAppState(
    scope: CoroutineScope,
): LunarAppState = createTestLunarAppState(
    scope = scope,
    runtime = createTestPlatformRuntime(),
)

internal fun createTestLunarAppState(
    scope: CoroutineScope,
    runtime: PlatformRuntime,
): LunarAppState = LunarAppState(runtime = runtime, scope = scope)

internal fun testSheetMusicItem(
    id: String,
    title: String = id.replaceFirstChar(Char::uppercase),
    composer: String = "Composer",
    collection: String? = null,
    tags: List<String> = emptyList(),
    isHidden: Boolean = false,
    dateAddedEpochMillis: Long = 1L,
    pageCount: Int? = null,
    originalFileName: String = "$title.pdf",
): SheetMusicItem = SheetMusicItem(
    id = id,
    title = title,
    composer = composer,
    tags = tags,
    collection = collection,
    isHidden = isHidden,
    document = PdfDocumentReference(
        storedPath = "/scores/$id.pdf",
        originalFileName = originalFileName,
    ),
    dateAddedEpochMillis = dateAddedEpochMillis,
    pageCount = pageCount,
)

internal fun testLibrarySetlist(
    id: String,
    name: String,
    itemIds: List<String>,
    createdAtEpochMillis: Long = 1L,
    updatedAtEpochMillis: Long = 1L,
): LibrarySetlist = LibrarySetlist(
    id = id,
    name = name,
    itemIds = itemIds,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

internal fun testLibrarySongbook(
    id: String,
    name: String,
    itemIds: List<String>,
    createdAtEpochMillis: Long = 1L,
    updatedAtEpochMillis: Long = 1L,
    pageCount: Int? = null,
): LibrarySongbook = LibrarySongbook(
    id = id,
    name = name,
    itemIds = itemIds,
    document = PdfDocumentReference(
        storedPath = "/songbooks/$id.pdf",
        originalFileName = "$name.pdf",
    ),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    pageCount = pageCount,
)

internal fun testLocalFilesSource(
    id: String,
    label: String,
    addedAtEpochMillis: Long = 1L,
    itemCount: Int = 0,
): LocalFilesSource = LocalFilesSource(
    id = id,
    label = label,
    addedAtEpochMillis = addedAtEpochMillis,
    itemCount = itemCount,
)

private class StaticLibraryCacheInspector(
    private val snapshot: LibraryCacheSnapshot,
) : LibraryCacheInspector {
    override suspend fun inspect(): LibraryCacheSnapshot = snapshot
}

internal class TestPdfDocumentExporter(
    private val exportAction: suspend (documentPath: String, suggestedFileName: String) -> String =
        { _, suggestedFileName -> "Downloads/$suggestedFileName" },
) : PdfDocumentExporter {
    override suspend fun export(
        documentPath: String,
        suggestedFileName: String,
    ): String = exportAction(documentPath, suggestedFileName)
}

internal class TestSongbookPdfBuilder(
    private val buildAction: suspend (
        songbookName: String,
        appendedDocumentPaths: List<String>,
        existingSongbookPath: String?,
        coverImage: SelectedCoverImage?,
    ) -> SongbookBuildResult = { songbookName, _, _, _ ->
        SongbookBuildResult(
            document = PdfDocumentReference(
                storedPath = "/songbooks/${songbookName.lowercase().replace(' ', '_')}.pdf",
                originalFileName = "$songbookName.pdf",
            ),
            pageCount = 1,
        )
    },
) : SongbookPdfBuilder {
    override suspend fun buildSongbook(
        songbookName: String,
        appendedDocumentPaths: List<String>,
        existingSongbookPath: String?,
        coverImage: SelectedCoverImage?,
    ): SongbookBuildResult = buildAction(
        songbookName,
        appendedDocumentPaths,
        existingSongbookPath,
        coverImage,
    )
}

internal class TestCoverImagePicker(
    private val image: SelectedCoverImage? = null,
) : CoverImagePicker {
    override suspend fun pickCoverImage(): SelectedCoverImage? = image
}
