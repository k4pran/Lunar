package com.ryanjames.lunar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ryanjames.lunar.library.data.CloudGoogleDriveSource
import com.ryanjames.lunar.library.data.CloudLibrarySource
import com.ryanjames.lunar.library.data.LocalFilesSource
import com.ryanjames.lunar.library.data.LocalFolderSource
import com.ryanjames.lunar.library.data.SheetMusicMetadataInput
import com.ryanjames.lunar.library.data.SkippedImportDocument
import com.ryanjames.lunar.library.data.generateSourceId
import com.ryanjames.lunar.library.model.LibraryQuery
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.platform.ImportRequestResult
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

    var importActivityStep: String? by mutableStateOf(null)
        private set

    var importDiscoveredResources: Int by mutableStateOf(0)
        private set

    var importProcessedResources: Int by mutableStateOf(0)
        private set

    var importSkippedResources: Int by mutableStateOf(0)
        private set

    var importActivityLog: List<String> by mutableStateOf(emptyList())
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
        selectedGroup = null
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
        runImportForSource(sourceId, source.label) { runtime.importer.importPdfFiles() }
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
        runImportForSource(sourceId, source.label) { runtime.importer.importPdfFolder() }
    }

    fun addCloudSource(source: CloudLibrarySource) {
        scope.launch {
            try {
                val connectedSource = connectCloudSourceIfNeeded(source)
                runtime.sourceRegistry.addSource(connectedSource)
                runtime.syncManager.refreshSource(connectedSource)
                runtime.syncManager.state.value.lastMessage?.takeIf(String::isNotBlank)?.let {
                    bannerMessage = it
                }
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Cloud source connection failed."
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

    fun refreshCloudSource(source: CloudLibrarySource) {
        scope.launch {
            try {
                val connectedSource = connectCloudSourceIfNeeded(source)
                if (connectedSource != source) {
                    runtime.sourceRegistry.updateSource(connectedSource)
                }
                runtime.syncManager.refreshSource(connectedSource)
                runtime.syncManager.state.value.lastMessage?.takeIf(String::isNotBlank)?.let {
                    bannerMessage = it
                }
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Cloud refresh failed."
            }
        }
    }

    fun refreshAllCloudSources() {
        scope.launch {
            runtime.syncManager.refreshAllSources(runtime.sourceRegistry.sources.value, force = true)
            runtime.syncManager.state.value.lastMessage?.takeIf(String::isNotBlank)?.let {
                bannerMessage = it
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
        importerAction: suspend () -> ImportRequestResult,
    ) {
        if (importInProgress) return

        scope.launch {
            importInProgress = true
            startImportActivity("Starting local import.")
            try {
                appendImportActivity(
                    message = "Opening file picker.",
                    currentStep = "Selecting local PDFs",
                )
                val result = importerAction()
                appendImportActivity(
                    message = "Importer returned ${result.documents.size} PDF${if (result.documents.size == 1) "" else "s"}.",
                    currentStep = "Importing local PDFs",
                    discoveredDelta = result.documents.size,
                )
                val importResult = runtime.repository.importDocuments(result.documents)
                val imported = importResult.importedItems
                if (importResult.skippedDocuments.isNotEmpty()) {
                    appendSkippedImportActivity(
                        skippedDocuments = importResult.skippedDocuments,
                        sourceLabel = "Local import",
                    )
                }
                if (imported.isNotEmpty()) {
                    appendImportActivity(
                        message = "Imported ${imported.size} PDF${if (imported.size == 1) "" else "s"} into the library.",
                        currentStep = "Import complete",
                        processedDelta = imported.size,
                    )
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
                finishImportActivity(
                    summary = when {
                        imported.isNotEmpty() -> "Local import complete."
                        !result.notice.isNullOrBlank() -> result.notice
                        else -> "No local PDFs were added."
                    }
                )
            } catch (error: Throwable) {
                finishImportActivity(error.message ?: "Local import failed.")
                bannerMessage = error.message ?: "Import failed."
            } finally {
                importInProgress = false
            }
        }
    }

    private fun runImportForSource(
        sourceId: String,
        sourceLabel: String,
        importerAction: suspend () -> ImportRequestResult,
    ) {
        if (importInProgress) return

        scope.launch {
            importInProgress = true
            startImportActivity("Starting import for $sourceLabel.")
            try {
                appendImportActivity(
                    message = "Opening picker for $sourceLabel.",
                    currentStep = "Selecting local PDFs",
                )
                val result = importerAction()
                appendImportActivity(
                    message = "$sourceLabel picker returned ${result.documents.size} PDF${if (result.documents.size == 1) "" else "s"}.",
                    currentStep = "Importing local PDFs",
                    discoveredDelta = result.documents.size,
                )
                val importResult = runtime.repository.importDocumentsForSource(sourceId, result.documents)
                val imported = importResult.importedItems
                if (importResult.skippedDocuments.isNotEmpty()) {
                    appendSkippedImportActivity(
                        skippedDocuments = importResult.skippedDocuments,
                        sourceLabel = sourceLabel,
                    )
                }

                if (imported.isEmpty() && result.documents.isEmpty()) {
                    runtime.sourceRegistry.removeSource(sourceId)
                }

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
                        else -> Unit
                    }
                }

                bannerMessage = when {
                    imported.isNotEmpty() -> {
                        selectedSection = AppSection.LIBRARY
                        clearFilters()
                        val scores = imported.size
                        appendImportActivity(
                            message = "$sourceLabel imported $scores PDF${if (scores == 1) "" else "s"} into the library.",
                            currentStep = "Import complete",
                            processedDelta = scores,
                        )
                        "$scores PDF${if (scores == 1) "" else "s"} added to your library."
                    }

                    !result.notice.isNullOrBlank() -> result.notice
                    else -> null
                }
                finishImportActivity(
                    summary = when {
                        imported.isNotEmpty() -> "$sourceLabel import complete."
                        !result.notice.isNullOrBlank() -> result.notice
                        else -> "No PDFs were added from $sourceLabel."
                    }
                )
            } catch (error: Throwable) {
                finishImportActivity(error.message ?: "Import failed.")
                bannerMessage = error.message ?: "Import failed."
            } finally {
                importInProgress = false
            }
        }
    }

    private suspend fun connectCloudSourceIfNeeded(source: CloudLibrarySource): CloudLibrarySource = when (source) {
        is CloudGoogleDriveSource -> connectGoogleDriveSourceIfNeeded(source)
        else -> source
    }

    private suspend fun connectGoogleDriveSourceIfNeeded(source: CloudGoogleDriveSource): CloudGoogleDriveSource {
        val settings = source.settings
        if (settings.refreshToken.isNotBlank()) {
            return source
        }

        val session = runtime.googleDriveOAuth.getOrFetchRefreshToken(settings)
        runtime.syncManager.primeGoogleAccessToken(
            sourceId = source.id,
            accessToken = session.accessToken,
            expiresAtEpochMillis = session.expiresAtEpochMillis,
        )
        return source.copy(
            settings = settings.copy(
                refreshToken = session.refreshToken,
                accessToken = "",
            ),
        )
    }

    private fun startImportActivity(initialMessage: String) {
        importActivityStep = "Preparing import"
        importDiscoveredResources = 0
        importProcessedResources = 0
        importSkippedResources = 0
        importActivityLog = listOf(initialMessage)
    }

    private fun appendImportActivity(
        message: String,
        currentStep: String? = null,
        discoveredDelta: Int = 0,
        processedDelta: Int = 0,
        skippedDelta: Int = 0,
    ) {
        appendImportActivities(
            messages = listOf(message),
            currentStep = currentStep,
            discoveredDelta = discoveredDelta,
            processedDelta = processedDelta,
            skippedDelta = skippedDelta,
        )
    }

    private fun appendImportActivities(
        messages: List<String>,
        currentStep: String? = null,
        discoveredDelta: Int = 0,
        processedDelta: Int = 0,
        skippedDelta: Int = 0,
    ) {
        val trimmedMessages = messages
            .map(String::trim)
            .filter(String::isNotBlank)
        if (
            trimmedMessages.isEmpty() &&
            currentStep == null &&
            discoveredDelta == 0 &&
            processedDelta == 0 &&
            skippedDelta == 0
        ) {
            return
        }
        importActivityStep = currentStep ?: importActivityStep
        importDiscoveredResources = (importDiscoveredResources + discoveredDelta).coerceAtLeast(0)
        importProcessedResources = (importProcessedResources + processedDelta).coerceAtLeast(0)
        importSkippedResources = (importSkippedResources + skippedDelta).coerceAtLeast(0)
        if (trimmedMessages.isNotEmpty()) {
            importActivityLog = (importActivityLog + trimmedMessages).takeLast(MAX_IMPORT_ACTIVITY_ENTRIES)
        }
    }

    private fun finishImportActivity(summary: String?) {
        importActivityStep = summary?.takeIf(String::isNotBlank) ?: "Import complete"
        summary?.takeIf(String::isNotBlank)?.let { appendImportActivity(it) }
    }

    private fun appendSkippedImportActivity(
        skippedDocuments: List<SkippedImportDocument>,
        sourceLabel: String,
    ) {
        val prefix = sourceLabel.ifBlank { "Local import" }
        appendImportActivities(
            messages = skippedDocuments.map { skipped ->
                "$prefix skipped duplicate PDF \"${skipped.originalFileName}\" because ${skipped.reason}"
            },
            currentStep = "Importing local PDFs",
            skippedDelta = skippedDocuments.size,
        )
    }
}

@Composable
fun rememberLunarAppState(runtime: PlatformRuntime): LunarAppState {
    val scope = rememberCoroutineScope()
    return remember(runtime, scope) {
        LunarAppState(runtime = runtime, scope = scope)
    }
}

private const val MAX_IMPORT_ACTIVITY_ENTRIES = 150
