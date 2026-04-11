package com.ryanjames.lunar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ryanjames.lunar.library.data.SheetMusicMetadataInput
import com.ryanjames.lunar.library.model.LibraryQuery
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.platform.PlatformRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class LibraryLayoutMode {
    GRID,
    LIST,
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
}

@Composable
fun rememberLunarAppState(runtime: PlatformRuntime): LunarAppState {
    val scope = rememberCoroutineScope()
    return remember(runtime, scope) {
        LunarAppState(runtime = runtime, scope = scope)
    }
}
