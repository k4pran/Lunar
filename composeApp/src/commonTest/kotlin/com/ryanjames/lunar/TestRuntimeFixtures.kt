package com.ryanjames.lunar

import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.InMemoryLibraryStorage
import com.ryanjames.lunar.library.data.InMemorySourceRegistry
import com.ryanjames.lunar.library.data.LibrarySource
import com.ryanjames.lunar.library.data.LocalFilesSource
import com.ryanjames.lunar.library.model.LibrarySetlist
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.platform.LibraryCacheInspector
import com.ryanjames.lunar.platform.LibraryCacheSnapshot
import com.ryanjames.lunar.platform.PlatformCapabilities
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.platform.UnavailablePdfPageRenderer
import com.ryanjames.lunar.platform.UnsupportedPdfImporter
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.InMemoryAppSettingsStore
import com.ryanjames.lunar.sync.UnsupportedGoogleDriveOAuthCoordinator
import com.ryanjames.lunar.sync.rememberNoOpLibrarySyncManager
import com.ryanjames.lunar.ui.LunarAppState
import kotlinx.coroutines.CoroutineScope

internal suspend fun createTestPlatformRuntime(
    initialItems: List<SheetMusicItem> = emptyList(),
    initialSetlists: List<LibrarySetlist> = emptyList(),
    initialSources: List<LibrarySource> = emptyList(),
    initialSettings: AppSettings = AppSettings(),
    cacheSnapshot: LibraryCacheSnapshot = LibraryCacheSnapshot(storageLabel = "Test cache"),
): PlatformRuntime {
    val repository = DefaultSheetMusicRepository(
        storage = InMemoryLibraryStorage(
            initialItems = initialItems,
            initialSetlists = initialSetlists,
        )
    )
    repository.initialize()

    val settingsStore = InMemoryAppSettingsStore(initialSettings)
    settingsStore.initialize()

    val sourceRegistry = InMemorySourceRegistry(initialSources)
    sourceRegistry.initialize()

    return PlatformRuntime(
        platformName = "Test",
        capabilities = PlatformCapabilities(),
        repository = repository,
        importer = UnsupportedPdfImporter("Import unavailable in tests."),
        renderer = UnavailablePdfPageRenderer,
        syncManager = rememberNoOpLibrarySyncManager(
            repository = repository,
            renderer = UnavailablePdfPageRenderer,
        ),
        sourceRegistry = sourceRegistry,
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
    dateAddedEpochMillis: Long = 1L,
    pageCount: Int? = null,
): SheetMusicItem = SheetMusicItem(
    id = id,
    title = title,
    composer = composer,
    tags = tags,
    collection = collection,
    document = PdfDocumentReference(
        storedPath = "/scores/$id.pdf",
        originalFileName = "$title.pdf",
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
