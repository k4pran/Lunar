package com.ryanjames.lunar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ryanjames.lunar.library.data.CloudSupabaseSource
import com.ryanjames.lunar.library.data.LibrarySource
import com.ryanjames.lunar.library.data.LocalFilesSource
import com.ryanjames.lunar.library.data.LocalFolderSource
import com.ryanjames.lunar.library.data.SheetMusicMetadataInput
import com.ryanjames.lunar.library.data.generateSourceId
import com.ryanjames.lunar.library.model.LibraryQuery
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.platform.PlatformRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Clock

enum class LibraryLayoutMode {
    GRID,
    LIST,
}

enum class LibraryBrowseMode {
    ALL,
    BY_COLLECTION,
    BY_COMPOSER,
}

enum class AppSection {
    IMPORT,
    LIBRARY,
}

class LunarAppState(
    private val runtime: PlatformRuntime,
    private val scope: CoroutineScope,
) {
    var selectedSection: AppSection by mutableStateOf(AppSection.LIBRARY)
        private set

    var query: LibraryQuery by mutableStateOf(LibraryQuery())
        private set

    var layoutMode: LibraryLayoutMode by mutableStateOf(LibraryLayoutMode.LIST)
        private set

    var browseMode: LibraryBrowseMode by mutableStateOf(LibraryBrowseMode.ALL)
        private set

    // When browsing BY_COLLECTION or BY_COMPOSER, the selected group name (null = show group list)
    var selectedGroup: String? by mutableStateOf(null)
        private set

    var editingItemId: String? by mutableStateOf(null)
        private set

    var previewItemId: String? by mutableStateOf(null)
        private set

    var fullscreenItemId: String? by mutableStateOf(null)
        private set

    var deleteCandidateItemId: String? by mutableStateOf(null)
        private set

    var importInProgress: Boolean by mutableStateOf(false)
        private set

    var bannerMessage: String? by mutableStateOf(null)
        private set

    fun selectSection(section: AppSection) {
        selectedSection = section
    }

    fun updateSearchText(searchText: String) {
        query = query.copy(searchText = searchText)
    }

    fun updateSortOption(sortOption: LibrarySortOption) {
        query = query.copy(sortOption = sortOption)
    }

    fun updateSortDirection(sortDirection: SortDirection) {
        query = query.copy(sortDirection = sortDirection)
    }

    fun toggleFavoriteFilter() {
        query = query.copy(favoritesOnly = !query.favoritesOnly)
    }

    fun toggleTag(tag: String) {
        val selectedTags = query.selectedTags.toMutableSet()
        if (!selectedTags.add(tag)) {
            selectedTags.remove(tag)
        }
        query = query.copy(selectedTags = selectedTags)
    }

    fun selectCollection(collection: String?) {
        query = query.copy(
            selectedCollection = if (query.selectedCollection == collection) null else collection,
        )
    }

    fun updateLayoutMode(layoutMode: LibraryLayoutMode) {
        this.layoutMode = layoutMode
    }

    fun updateBrowseMode(mode: LibraryBrowseMode) {
        browseMode = mode
        selectedGroup = null // reset drill-down when switching mode
    }

    fun selectGroup(groupName: String) {
        selectedGroup = groupName
    }

    fun clearGroupSelection() {
        selectedGroup = null
    }

    fun clearFilters() {
        query = query.copy(
            searchText = "",
            selectedTags = emptySet(),
            selectedCollection = null,
            favoritesOnly = false,
        )
    }

    fun startEditing(itemId: String) {
        editingItemId = itemId
    }

    fun dismissEditor() {
        editingItemId = null
    }

    fun requestDelete(itemId: String) {
        deleteCandidateItemId = itemId
    }

    fun dismissDeleteRequest() {
        deleteCandidateItemId = null
    }

    fun clearBanner() {
        bannerMessage = null
    }

    fun importFiles() {
        runImport { runtime.importer.importPdfFiles() }
    }

    fun importFolder() {
        runImport { runtime.importer.importPdfFolder() }
    }

    fun selectSyncProvider(providerId: String) {
        scope.launch {
            runtime.syncManager.selectProvider(providerId)
        }
    }

    fun updateSupabaseProjectUrl(projectUrl: String) {
        scope.launch {
            val current = runtime.syncManager.state.value.settings.supabasePublicStorage
            runtime.syncManager.updateSupabasePublicStorageSettings(
                current.copy(projectUrl = projectUrl),
            )
        }
    }

    fun updateSupabaseBucketName(bucketName: String) {
        scope.launch {
            val current = runtime.syncManager.state.value.settings.supabasePublicStorage
            runtime.syncManager.updateSupabasePublicStorageSettings(
                current.copy(bucketName = bucketName),
            )
        }
    }

    fun updateSupabaseRootDirectory(rootDirectory: String) {
        scope.launch {
            val current = runtime.syncManager.state.value.settings.supabasePublicStorage
            runtime.syncManager.updateSupabasePublicStorageSettings(
                current.copy(rootDirectory = rootDirectory),
            )
        }
    }

    fun updateSupabaseFolderStrategy(strategy: com.ryanjames.lunar.library.data.BucketFolderStrategy) {
        scope.launch {
            val current = runtime.syncManager.state.value.settings.supabasePublicStorage
            runtime.syncManager.updateSupabasePublicStorageSettings(
                current.copy(folderStrategy = strategy),
            )
        }
    }

    fun updateSupabaseAnonKey(anonKey: String) {
        scope.launch {
            val current = runtime.syncManager.state.value.settings.supabasePublicStorage
            runtime.syncManager.updateSupabasePublicStorageSettings(
                current.copy(anonKey = anonKey),
            )
        }
    }

    fun forceSyncRefresh() {
        scope.launch {
            runtime.syncManager.refresh(force = true)
            val message = runtime.syncManager.state.value.lastMessage
            if (!message.isNullOrBlank()) {
                bannerMessage = message
            }
        }
    }

    // ─── Source management ────────────────────────────────────────────────────

    fun addLocalFilesSource(label: String) {
        val sourceId = generateSourceId()
        val source = LocalFilesSource(
            id = sourceId,
            label = label.ifBlank { "Local files" },
            addedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        scope.launch {
            runtime.sourceRegistry.addSource(source)
        }
        runImportForSource(sourceId) { runtime.importer.importPdfFiles() }
    }

    fun addLocalFolderSource(label: String) {
        val sourceId = generateSourceId()
        val source = LocalFolderSource(
            id = sourceId,
            label = label.ifBlank { "Local folder" },
            addedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        scope.launch {
            runtime.sourceRegistry.addSource(source)
        }
        runImportForSource(sourceId) { runtime.importer.importPdfFolder() }
    }

    fun addCloudSource(source: CloudSupabaseSource) {
        scope.launch {
            runtime.sourceRegistry.addSource(source)
            runtime.syncManager.refreshSource(source)
            val message = runtime.syncManager.state.value.lastMessage
            if (!message.isNullOrBlank()) {
                bannerMessage = message
            }
        }
    }

    fun removeSource(sourceId: String) {
        scope.launch {
            runtime.repository.removeItemsBySource(sourceId)
            runtime.sourceRegistry.removeSource(sourceId)
            bannerMessage = "Source removed."
        }
    }

    fun refreshCloudSource(source: CloudSupabaseSource) {
        scope.launch {
            runtime.syncManager.refreshSource(source)
            val message = runtime.syncManager.state.value.lastMessage
            if (!message.isNullOrBlank()) {
                bannerMessage = message
            }
        }
    }

    fun refreshAllCloudSources() {
        scope.launch {
            val sources = runtime.sourceRegistry.sources.value
            runtime.syncManager.refreshAllSources(sources)
            val message = runtime.syncManager.state.value.lastMessage
            if (!message.isNullOrBlank()) {
                bannerMessage = message
            }
        }
    }

    fun openPreview(item: SheetMusicItem) {
        selectedSection = AppSection.LIBRARY
        previewItemId = item.id
        scope.launch {
            runtime.repository.recordOpened(item.id, item.lastViewedPage)
        }
    }

    fun closePreview() {
        previewItemId = null
    }

    fun openFullscreen(itemId: String) {
        selectedSection = AppSection.LIBRARY
        previewItemId = null
        fullscreenItemId = itemId
    }

    fun closeFullscreen() {
        fullscreenItemId = null
        selectedSection = AppSection.LIBRARY
    }

    fun toggleFavorite(itemId: String) {
        scope.launch {
            runtime.repository.toggleFavorite(itemId)
        }
    }

    fun saveMetadata(
        itemId: String,
        title: String,
        composer: String,
        tags: String,
        collection: String,
        isFavorite: Boolean,
    ) {
        scope.launch {
            runtime.repository.updateMetadata(
                itemId = itemId,
                metadata = SheetMusicMetadataInput(
                    title = title,
                    composer = composer,
                    tags = tags.split(','),
                    collection = collection,
                    isFavorite = isFavorite,
                ),
            )
            editingItemId = null
            bannerMessage = "Library details updated."
        }
    }

    fun confirmDelete() {
        val itemId = deleteCandidateItemId ?: return
        scope.launch {
            runtime.repository.deleteItem(itemId)
            if (previewItemId == itemId) {
                previewItemId = null
            }
            if (fullscreenItemId == itemId) {
                fullscreenItemId = null
            }
            if (editingItemId == itemId) {
                editingItemId = null
            }
            deleteCandidateItemId = null
            selectedSection = AppSection.LIBRARY
            bannerMessage = "Score removed from your library."
        }
    }

    fun updateViewerProgress(itemId: String, pageIndex: Int) {
        scope.launch {
            runtime.repository.updateCurrentPage(itemId, pageIndex)
        }
    }

    fun updateViewerPageCount(itemId: String, pageCount: Int) {
        scope.launch {
            runtime.repository.updatePageCount(itemId, pageCount)
        }
    }

    private fun runImport(
        importerAction: suspend () -> com.ryanjames.lunar.platform.ImportRequestResult,
    ) {
        if (importInProgress) {
            return
        }

        scope.launch {
            importInProgress = true
            try {
                val result = importerAction()
                val imported = runtime.repository.importDocuments(result.documents)
                bannerMessage = when {
                    imported.isNotEmpty() -> {
                        selectedSection = AppSection.LIBRARY
                        clearFilters()
                        val scores = imported.size
                        "$scores PDF${if (scores == 1) "" else "s"} added to your library."
                    }

                    !result.notice.isNullOrBlank() -> result.notice
                    else -> null
                }
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Import failed."
            } finally {
                importInProgress = false
            }
        }
    }

    private fun runImportForSource(
        sourceId: String,
        importerAction: suspend () -> com.ryanjames.lunar.platform.ImportRequestResult,
    ) {
        if (importInProgress) {
            return
        }

        scope.launch {
            importInProgress = true
            try {
                val result = importerAction()
                val imported = runtime.repository.importDocumentsForSource(sourceId, result.documents)

                if (imported.isEmpty() && result.documents.isEmpty()) {
                    // No files picked — remove the empty source
                    runtime.sourceRegistry.removeSource(sourceId)
                }

                // Update the source's item count
                val currentSource = runtime.sourceRegistry.getSource(sourceId)
                if (currentSource != null) {
                    val count = runtime.repository.itemCountForSource(sourceId)
                    when (currentSource) {
                        is LocalFilesSource -> runtime.sourceRegistry.updateSource(
                            currentSource.copy(itemCount = count)
                        )
                        is LocalFolderSource -> runtime.sourceRegistry.updateSource(
                            currentSource.copy(itemCount = count)
                        )
                        else -> {}
                    }
                }

                bannerMessage = when {
                    imported.isNotEmpty() -> {
                        selectedSection = AppSection.LIBRARY
                        clearFilters()
                        val scores = imported.size
                        "$scores PDF${if (scores == 1) "" else "s"} added to your library."
                    }

                    !result.notice.isNullOrBlank() -> result.notice
                    else -> null
                }
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Import failed."
            } finally {
                importInProgress = false
            }
        }
    }
}

@Composable
fun rememberLunarAppState(runtime: PlatformRuntime): LunarAppState {
    val scope = rememberCoroutineScope()
    return remember(runtime, scope) {
        LunarAppState(runtime = runtime, scope = scope)
    }
}
