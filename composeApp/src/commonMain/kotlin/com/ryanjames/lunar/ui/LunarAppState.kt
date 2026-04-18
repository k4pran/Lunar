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
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.library.model.LibrarySetlist
import com.ryanjames.lunar.library.model.LibraryQuery
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.platform.ImportRequestResult
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.platform.SelectedCoverImage
import com.ryanjames.lunar.settings.AppColorTheme
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.AutoRefreshSchedule
import com.ryanjames.lunar.settings.CacheLimitPreset
import com.ryanjames.lunar.settings.LibraryLayoutPreference
import com.ryanjames.lunar.settings.ViewerPageModePreference
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
    SETLISTS,
    SONGBOOKS,
}

enum class AppSection {
    IMPORT,
    LIBRARY,
    SETTINGS,
}

sealed interface ViewerTarget {
    data class Score(val itemId: String) : ViewerTarget

    data class Songbook(val songbookId: String) : ViewerTarget
}

class LunarAppState(
    private val runtime: PlatformRuntime,
    private val scope: CoroutineScope,
) {
    val canDownloadScores: Boolean
        get() = runtime.capabilities.scoreDownloadSupported

    val canCreateSongbooks: Boolean
        get() = runtime.capabilities.songbookCreationSupported

    val canPickSongbookCover: Boolean
        get() = runtime.capabilities.songbookCoverImageSupported

    var selectedSection: AppSection by mutableStateOf(AppSection.LIBRARY)
        private set

    var query: LibraryQuery by mutableStateOf(LibraryQuery())
        private set

    var layoutMode: LibraryLayoutMode by mutableStateOf(
        runtime.settingsStore.settings.value.defaultLibraryLayout.toLayoutMode()
    )
        private set

    var browseMode: LibraryBrowseMode by mutableStateOf(LibraryBrowseMode.ALL)
        private set

    var selectedGroup: String? by mutableStateOf(null)
        private set

    var selectedSetlistId: String? by mutableStateOf(null)
        private set

    var temporarySetlist: TemporarySetlistSession? by mutableStateOf(null)
        private set

    var temporarySetlistOpen: Boolean by mutableStateOf(false)
        private set

    var selectedScoreIds: Set<String> by mutableStateOf(emptySet())
        private set

    var editingItemId: String? by mutableStateOf(null)
        private set

    var previewTarget: ViewerTarget? by mutableStateOf(null)
        private set

    var fullscreenTarget: ViewerTarget? by mutableStateOf(null)
        private set

    var deleteCandidateItemId: String? by mutableStateOf(null)
        private set

    var deleteCandidateSetlistId: String? by mutableStateOf(null)
        private set

    var deleteCandidateSongbookId: String? by mutableStateOf(null)
        private set

    var setlistPickerVisible: Boolean by mutableStateOf(false)
        private set

    var songbookPickerVisible: Boolean by mutableStateOf(false)
        private set

    var randomSetlistBuilderVisible: Boolean by mutableStateOf(false)
        private set

    var saveTemporarySetlistDialogVisible: Boolean by mutableStateOf(false)
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
        if (section != AppSection.LIBRARY) {
            clearScoreSelection()
            dismissLibraryTransientUi()
        }
    }

    fun updateSearchText(searchText: String) {
        if (query.searchText == searchText) {
            return
        }
        updateQuery { copy(searchText = searchText) }
    }

    fun updateSortOption(sortOption: LibrarySortOption) {
        query = query.copy(sortOption = sortOption)
    }

    fun updateSortDirection(sortDirection: SortDirection) {
        query = query.copy(sortDirection = sortDirection)
    }

    fun toggleFavoriteFilter() {
        updateQuery { copy(favoritesOnly = !favoritesOnly) }
    }

    fun toggleTag(tag: String) {
        val selectedTags = query.selectedTags.toMutableSet()
        if (!selectedTags.add(tag)) {
            selectedTags.remove(tag)
        }
        updateQuery { copy(selectedTags = selectedTags) }
    }

    fun selectCollection(collection: String?) {
        val nextCollection = if (query.selectedCollection == collection) null else collection
        updateQuery { copy(selectedCollection = nextCollection) }
        if (
            browseMode == LibraryBrowseMode.BY_COLLECTION &&
            selectedGroup != null &&
            nextCollection != null &&
            selectedGroup?.equals(nextCollection, ignoreCase = true) != true
        ) {
            selectedGroup = null
        }
    }

    fun clearRefinements() {
        updateQuery {
            copy(
                selectedTags = emptySet(),
                selectedCollection = null,
                favoritesOnly = false,
            )
        }
    }

    fun updateLayoutMode(layoutMode: LibraryLayoutMode) {
        this.layoutMode = layoutMode
        updateStoredSettings { settings -> settings.copy(defaultLibraryLayout = layoutMode.toPreference()) }
    }

    fun updateBrowseMode(mode: LibraryBrowseMode) {
        clearScoreSelection()
        browseMode = mode
        selectedGroup = null
        if (mode != LibraryBrowseMode.SETLISTS) {
            closeSetlistDetail()
        }
    }

    fun selectGroup(groupName: String) {
        clearScoreSelection()
        selectedGroup = groupName
    }

    fun clearGroupSelection() {
        clearScoreSelection()
        selectedGroup = null
    }

    fun clearFilters() {
        updateQuery {
            copy(
                searchText = "",
                selectedTags = emptySet(),
                selectedCollection = null,
                favoritesOnly = false,
            )
        }
    }

    fun toggleScoreSelection(itemId: String) {
        selectedScoreIds = selectedScoreIds.toMutableSet().apply {
            if (!add(itemId)) {
                remove(itemId)
            }
        }.toSet()
        if (selectedScoreIds.isEmpty()) {
            setlistPickerVisible = false
        }
    }

    fun clearScoreSelection() {
        selectedScoreIds = emptySet()
        setlistPickerVisible = false
        songbookPickerVisible = false
    }

    fun showSetlistPicker() {
        if (selectedScoreIds.isNotEmpty()) {
            setlistPickerVisible = true
        }
    }

    fun dismissSetlistPicker() {
        setlistPickerVisible = false
    }

    fun showSongbookPicker() {
        if (selectedScoreIds.isNotEmpty()) {
            songbookPickerVisible = true
        }
    }

    fun dismissSongbookPicker() {
        songbookPickerVisible = false
    }

    fun openSetlist(setlistId: String) {
        showSavedSetlist(setlistId)
    }

    fun closeSetlist() {
        closeSetlistDetail()
    }

    fun openTemporarySetlist() {
        if (temporarySetlist == null) {
            bannerMessage = "Create a random setlist first."
            return
        }
        showTemporarySetlist()
    }

    fun discardTemporarySetlist() {
        val hadTemporarySetlist = temporarySetlist != null
        temporarySetlist = null
        closeSetlistDetail()
        saveTemporarySetlistDialogVisible = false
        if (hadTemporarySetlist) {
            bannerMessage = "Temporary setlist discarded."
        }
    }

    fun requestDeleteSetlist(setlistId: String) {
        deleteCandidateSetlistId = setlistId
    }

    fun dismissDeleteSetlistRequest() {
        deleteCandidateSetlistId = null
    }

    fun requestDeleteSongbook(songbookId: String) {
        deleteCandidateSongbookId = songbookId
    }

    fun dismissDeleteSongbookRequest() {
        deleteCandidateSongbookId = null
    }

    fun openRandomSheet(items: List<SheetMusicItem>) {
        val randomItem = buildRandomSightReadingSelection(
            items = items,
            requestedCount = 1,
        ).firstOrNull()

        if (randomItem == null) {
            bannerMessage = "No scores are available for a random pick."
            return
        }

        openPreview(randomItem)
        bannerMessage = "Opening a random sheet."
    }

    fun showRandomSetlistBuilder() {
        randomSetlistBuilderVisible = true
    }

    fun dismissRandomSetlistBuilder() {
        randomSetlistBuilderVisible = false
    }

    fun createTemporaryRandomSetlist(
        items: List<SheetMusicItem>,
        requestedCount: Int,
    ) {
        val shuffledItems = buildRandomSightReadingSelection(
            items = items,
            requestedCount = requestedCount,
        )
        if (shuffledItems.isEmpty()) {
            randomSetlistBuilderVisible = false
            bannerMessage = "No scores are available for a random setlist."
            return
        }

        temporarySetlist = TemporarySetlistSession(
            itemIds = shuffledItems.map { it.id },
            createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        )
        selectSection(AppSection.LIBRARY)
        showTemporarySetlist()
        dismissLibraryTransientUi()
        val scoreCount = shuffledItems.size
        bannerMessage = "Built a temporary random setlist with $scoreCount score${if (scoreCount == 1) "" else "s"}."
    }

    fun showSaveTemporarySetlistDialog() {
        if (temporarySetlist != null) {
            saveTemporarySetlistDialogVisible = true
        }
    }

    fun dismissSaveTemporarySetlistDialog() {
        saveTemporarySetlistDialogVisible = false
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

    fun applySettings(settings: AppSettings) {
        layoutMode = settings.defaultLibraryLayout.toLayoutMode()
    }

    fun updateDefaultViewerPageMode(mode: ViewerPageModePreference) {
        updateStoredSettings { settings -> settings.copy(defaultViewerPageMode = mode) }
    }

    fun updateTheme(theme: AppColorTheme) {
        updateStoredSettings { settings -> settings.copy(theme = theme) }
    }

    fun updateRefreshOnLaunch(enabled: Boolean) {
        updateStoredSettings { settings -> settings.copy(refreshOnLaunch = enabled) }
    }

    fun updateAutoRefreshSchedule(schedule: AutoRefreshSchedule) {
        updateStoredSettings { settings -> settings.copy(autoRefreshSchedule = schedule) }
    }

    fun updateCacheLimit(limit: CacheLimitPreset) {
        updateStoredSettings { settings -> settings.copy(cacheLimit = limit) }
    }

    fun updateCloudConnectTimeout(seconds: Int) {
        updateStoredSettings { settings -> settings.copy(cloudConnectTimeoutSeconds = seconds) }
    }

    fun updateCloudReadTimeout(seconds: Int) {
        updateStoredSettings { settings -> settings.copy(cloudReadTimeoutSeconds = seconds) }
    }

    fun resetGlobalSettings() {
        scope.launch {
            runtime.settingsStore.reset()
            bannerMessage = "Settings reset to recommended defaults."
        }
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
        focusLibrarySection()
        previewTarget = ViewerTarget.Score(item.id)
        scope.launch {
            runtime.repository.recordOpened(item.id, item.lastViewedPage)
        }
    }

    fun openSongbook(songbookId: String) {
        focusLibrarySection()
        previewTarget = ViewerTarget.Songbook(songbookId)
    }

    fun closePreview() {
        previewTarget = null
    }

    fun openFullscreen(itemId: String) {
        focusLibrarySection()
        previewTarget = null
        fullscreenTarget = ViewerTarget.Score(itemId)
    }

    fun openSongbookFullscreen(songbookId: String) {
        focusLibrarySection()
        previewTarget = null
        fullscreenTarget = ViewerTarget.Songbook(songbookId)
    }

    fun closeFullscreen() {
        fullscreenTarget = null
        focusLibrarySection()
    }

    fun toggleFavorite(itemId: String) {
        scope.launch {
            runtime.repository.toggleFavorite(itemId)
        }
    }

    fun downloadScore(item: SheetMusicItem) {
        scope.launch {
            if (!runtime.capabilities.scoreDownloadSupported) {
                bannerMessage = "Score downloads aren't available on this device."
                return@launch
            }

            try {
                val destination = runtime.pdfExporter.export(
                    documentPath = item.document.storedPath,
                    suggestedFileName = item.document.originalFileName,
                )
                bannerMessage = "Saved \"${item.title}\" to $destination."
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not download \"${item.title}\"."
            }
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
            selectedScoreIds = selectedScoreIds - itemId
            if (selectedScoreIds.isEmpty()) {
                setlistPickerVisible = false
                songbookPickerVisible = false
            }
            if (previewTarget == ViewerTarget.Score(itemId)) {
                previewTarget = null
            }
            if (fullscreenTarget == ViewerTarget.Score(itemId)) {
                fullscreenTarget = null
            }
            if (editingItemId == itemId) {
                editingItemId = null
            }
            deleteCandidateItemId = null
            selectedSection = AppSection.LIBRARY
            bannerMessage = "Score removed from your library."
        }
    }

    fun createSetlist(name: String, itemIds: List<String>) {
        scope.launch {
            try {
                val setlist = runtime.repository.createSetlist(name, itemIds)
                showSavedSetlist(
                    setlist = setlist,
                    banner = "Saved \"${setlist.name}\".",
                )
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not save the setlist."
            }
        }
    }

    fun addSelectionToSetlist(setlistId: String, itemIds: List<String>) {
        scope.launch {
            try {
                val setlist = runtime.repository.addItemsToSetlist(setlistId, itemIds)
                showSavedSetlist(
                    setlist = setlist,
                    banner = "Updated \"${setlist.name}\".",
                )
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not update the setlist."
            }
        }
    }

    fun createSongbook(
        name: String,
        items: List<SheetMusicItem>,
        coverImage: SelectedCoverImage? = null,
    ) {
        scope.launch {
            if (!runtime.capabilities.songbookCreationSupported) {
                bannerMessage = "Songbook creation isn't available on this device."
                return@launch
            }

            val selectedItems = items.distinctBy { it.id }
            if (selectedItems.isEmpty()) {
                bannerMessage = "Select at least one score to create a songbook."
                return@launch
            }

            try {
                val buildResult = runtime.songbookBuilder.buildSongbook(
                    songbookName = name,
                    appendedDocumentPaths = selectedItems.map { it.document.storedPath },
                    coverImage = coverImage,
                )
                val songbook = runtime.repository.createSongbook(
                    name = name,
                    itemIds = selectedItems.map { it.id },
                    document = buildResult.document,
                    pageCount = buildResult.pageCount,
                )
                showSongbooksOverview()
                previewTarget = ViewerTarget.Songbook(songbook.id)
                bannerMessage = "Saved \"${songbook.name}\"."
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not create the songbook."
            }
        }
    }

    fun addSelectionToSongbook(
        songbookId: String,
        items: List<SheetMusicItem>,
    ) {
        scope.launch {
            if (!runtime.capabilities.songbookCreationSupported) {
                bannerMessage = "Songbook updates aren't available on this device."
                return@launch
            }

            val selectedItems = items.distinctBy { it.id }
            if (selectedItems.isEmpty()) {
                bannerMessage = "Select at least one score to add to a songbook."
                return@launch
            }

            val existingSongbook = runtime.repository.getSongbook(songbookId)
            if (existingSongbook == null) {
                bannerMessage = "That songbook no longer exists."
                return@launch
            }

            try {
                val buildResult = runtime.songbookBuilder.buildSongbook(
                    songbookName = existingSongbook.name,
                    existingSongbookPath = existingSongbook.document.storedPath,
                    appendedDocumentPaths = selectedItems.map { it.document.storedPath },
                )
                val updatedSongbook = runtime.repository.addItemsToSongbook(
                    songbookId = songbookId,
                    itemIds = selectedItems.map { it.id },
                    document = buildResult.document,
                    pageCount = buildResult.pageCount,
                )
                showSongbooksOverview()
                previewTarget = ViewerTarget.Songbook(updatedSongbook.id)
                bannerMessage = "Updated \"${updatedSongbook.name}\"."
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not update the songbook."
            }
        }
    }

    fun confirmDeleteSetlist() {
        val setlistId = deleteCandidateSetlistId ?: return
        scope.launch {
            runtime.repository.deleteSetlist(setlistId)
            if (selectedSetlistId == setlistId) {
                selectedSetlistId = null
            }
            deleteCandidateSetlistId = null
            bannerMessage = "Setlist deleted."
        }
    }

    fun confirmDeleteSongbook() {
        val songbookId = deleteCandidateSongbookId ?: return
        scope.launch {
            runtime.repository.deleteSongbook(songbookId)
            if (previewTarget == ViewerTarget.Songbook(songbookId)) {
                previewTarget = null
            }
            if (fullscreenTarget == ViewerTarget.Songbook(songbookId)) {
                fullscreenTarget = null
            }
            deleteCandidateSongbookId = null
            bannerMessage = "Songbook deleted."
        }
    }

    fun saveTemporarySetlist(name: String) {
        val session = temporarySetlist ?: return
        scope.launch {
            try {
                val setlist = runtime.repository.createSetlist(name, session.itemIds)
                temporarySetlist = null
                showSavedSetlist(
                    setlist = setlist,
                    banner = "Saved \"${setlist.name}\".",
                )
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not save the temporary setlist."
            }
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

    private fun focusLibrarySection() {
        selectedSection = AppSection.LIBRARY
    }

    private fun dismissLibraryTransientUi() {
        setlistPickerVisible = false
        songbookPickerVisible = false
        randomSetlistBuilderVisible = false
        saveTemporarySetlistDialogVisible = false
    }

    private inline fun updateQuery(transform: LibraryQuery.() -> LibraryQuery) {
        clearScoreSelection()
        query = query.transform()
    }

    private fun updateStoredSettings(transform: (AppSettings) -> AppSettings) {
        scope.launch {
            runtime.settingsStore.updateSettings(transform)
        }
    }

    private fun closeSetlistDetail() {
        selectedSetlistId = null
        temporarySetlistOpen = false
    }

    private fun showSavedSetlist(setlistId: String) {
        clearScoreSelection()
        saveTemporarySetlistDialogVisible = false
        browseMode = LibraryBrowseMode.SETLISTS
        selectedGroup = null
        temporarySetlistOpen = false
        selectedSetlistId = setlistId
    }

    private fun showSavedSetlist(
        setlist: LibrarySetlist,
        banner: String,
    ) {
        showSavedSetlist(setlist.id)
        bannerMessage = banner
    }

    private fun showTemporarySetlist() {
        clearScoreSelection()
        browseMode = LibraryBrowseMode.SETLISTS
        selectedGroup = null
        selectedSetlistId = null
        temporarySetlistOpen = true
    }

    private fun showSongbooksOverview() {
        clearScoreSelection()
        closeSetlistDetail()
        browseMode = LibraryBrowseMode.SONGBOOKS
        selectedGroup = null
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
                    currentStep = "Selecting local scores",
                )
                val result = importerAction()
                appendImportActivity(
                    message = "Importer returned ${result.documents.size} score file${if (result.documents.size == 1) "" else "s"}.",
                    currentStep = "Importing local scores",
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
                        message = "Imported ${imported.size} score${if (imported.size == 1) "" else "s"} into the library.",
                        currentStep = "Import complete",
                        processedDelta = imported.size,
                    )
                }
                bannerMessage = when {
                    imported.isNotEmpty() -> {
                        selectedSection = AppSection.LIBRARY
                        clearFilters()
                        val scores = imported.size
                        "$scores score${if (scores == 1) "" else "s"} added to your library."
                    }

                    !result.notice.isNullOrBlank() -> result.notice
                    else -> null
                }
                finishImportActivity(
                    summary = when {
                        imported.isNotEmpty() -> "Local import complete."
                        !result.notice.isNullOrBlank() -> result.notice
                        else -> "No local score files were added."
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
                    currentStep = "Selecting local scores",
                )
                val result = importerAction()
                appendImportActivity(
                    message = "$sourceLabel picker returned ${result.documents.size} score file${if (result.documents.size == 1) "" else "s"}.",
                    currentStep = "Importing local scores",
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
                            message = "$sourceLabel imported $scores score${if (scores == 1) "" else "s"} into the library.",
                            currentStep = "Import complete",
                            processedDelta = scores,
                        )
                        "$scores score${if (scores == 1) "" else "s"} added to your library."
                    }

                    !result.notice.isNullOrBlank() -> result.notice
                    else -> null
                }
                finishImportActivity(
                    summary = when {
                        imported.isNotEmpty() -> "$sourceLabel import complete."
                        !result.notice.isNullOrBlank() -> result.notice
                        else -> "No score files were added from $sourceLabel."
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
                "$prefix skipped duplicate score file \"${skipped.originalFileName}\" because ${skipped.reason}"
            },
            currentStep = "Importing local scores",
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

private fun LibraryLayoutPreference.toLayoutMode(): LibraryLayoutMode = when (this) {
    LibraryLayoutPreference.LIST -> LibraryLayoutMode.LIST
    LibraryLayoutPreference.GRID -> LibraryLayoutMode.GRID
}

private fun LibraryLayoutMode.toPreference(): LibraryLayoutPreference = when (this) {
    LibraryLayoutMode.LIST -> LibraryLayoutPreference.LIST
    LibraryLayoutMode.GRID -> LibraryLayoutPreference.GRID
}
