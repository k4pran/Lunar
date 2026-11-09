package com.ryanjames.lunar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
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
import com.ryanjames.lunar.library.model.HiddenScoreFilter
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.library.model.LibrarySetlist
import com.ryanjames.lunar.library.model.LibraryQuery
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.ScoreMetadata
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.resolveHasCover
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.library.model.ViewerSupportFilter
import com.ryanjames.lunar.library.model.applyLibraryQuery
import com.ryanjames.lunar.platform.ImportRequestResult
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.platform.SelectedCoverImage
import com.ryanjames.lunar.settings.AppSkin
import com.ryanjames.lunar.settings.AppSettings
import com.ryanjames.lunar.settings.AutoRefreshSchedule
import com.ryanjames.lunar.settings.CacheLimitPreset
import com.ryanjames.lunar.settings.LibraryLayoutPreference
import com.ryanjames.lunar.settings.ViewerKeybindings
import com.ryanjames.lunar.settings.ViewerPageModePreference
import com.ryanjames.lunar.settings.ViewerShortcutAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

enum class LibraryLayoutMode {
    GRID,
    LIST,
}

enum class LibraryBrowseMode {
    ALL,
    BY_COLLECTION,
    BY_COMPOSER,
    HIDDEN,
}

/**
 * The "Arrange by" choices shown in the sidebar's Library context. Each maps onto a
 * [LibraryBrowseMode] plus the `favoritesOnly` query flag.
 */
enum class LibraryArrangement(val label: String) {
    ALL("All"),
    COMPOSER("Composer"),
    COLLECTION("Collection"),
    FAVOURITES("Favourites"),
    HIDDEN("Hidden"),
}

internal fun libraryArrangementFor(
    browseMode: LibraryBrowseMode,
    favoritesOnly: Boolean,
): LibraryArrangement = when (browseMode) {
    LibraryBrowseMode.ALL -> if (favoritesOnly) LibraryArrangement.FAVOURITES else LibraryArrangement.ALL
    LibraryBrowseMode.BY_COMPOSER -> LibraryArrangement.COMPOSER
    LibraryBrowseMode.BY_COLLECTION -> LibraryArrangement.COLLECTION
    LibraryBrowseMode.HIDDEN -> LibraryArrangement.HIDDEN
}

private val LibraryArrangement.browseMode: LibraryBrowseMode
    get() = when (this) {
        LibraryArrangement.ALL, LibraryArrangement.FAVOURITES -> LibraryBrowseMode.ALL
        LibraryArrangement.COMPOSER -> LibraryBrowseMode.BY_COMPOSER
        LibraryArrangement.COLLECTION -> LibraryBrowseMode.BY_COLLECTION
        LibraryArrangement.HIDDEN -> LibraryBrowseMode.HIDDEN
    }

enum class AppSection {
    IMPORT,
    LIBRARY,
    SETLISTS,
    SONGBOOKS,
    COMPOSE,
    SETTINGS,
}

/** Sections whose content is hosted by `LibraryScreen` (and so share its score dialogs). */
internal val AppSection.isLibraryHosted: Boolean
    get() = this == AppSection.LIBRARY || this == AppSection.SETLISTS || this == AppSection.SONGBOOKS

/** Only the Library section shows the contextual part of the sidebar (Arrange by, Collections). */
internal fun sidebarShowsLibraryContext(section: AppSection): Boolean = section == AppSection.LIBRARY

sealed interface ViewerTarget {
    data class Score(val itemId: String) : ViewerTarget

    data class Songbook(val songbookId: String) : ViewerTarget

    /** Plays a setlist in order; [index] points into the setlist's visible pieces. */
    data class Setlist(val setlistId: String, val index: Int) : ViewerTarget
}

/** Where the Add scores picker will put the chosen scores. */
sealed interface AddScoresTarget {
    data class Setlist(val setlistId: String) : AddScoresTarget

    data class Songbook(val songbookId: String) : AddScoresTarget
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

    // Songbook edits read the stored PDF, rebuild it off-thread and then replace it; running two
    // at once would let the later write discard the other's pages, so they take turns.
    private val songbookMutationMutex = Mutex()

    val thumbnailCache: ScoreThumbnailCache<ImageBitmap> = ScoreThumbnailCache(
        renderer = { item ->
            runtime.renderer.renderPage(
                documentPath = item.document.storedPath,
                pageIndex = 0,
                targetWidth = ThumbnailTargetWidthPx,
            )?.image
        },
    )

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

    var selectedSongbookId: String? by mutableStateOf(null)
        private set

    var addScoresTarget: AddScoresTarget? by mutableStateOf(null)
        private set

    var selectedScoreIds: Set<String> by mutableStateOf(emptySet())
        private set

    /** The single score driving the library detail panel, separate from multi-select. */
    var focusedScoreId: String? by mutableStateOf(null)
        private set

    /** Page the viewer should start on for the score being opened, overriding its saved page. */
    var viewerStartPage: Int? by mutableStateOf(null)
        private set

    private var selectionAnchorId: String? = null

    var editingItemId: String? by mutableStateOf(null)
        private set

    var infoItemId: String? by mutableStateOf(null)
        private set

    var infoMetadata: ScoreMetadata? by mutableStateOf(null)
        private set

    var infoMetadataLoading: Boolean by mutableStateOf(false)
        private set

    var infoMetadataError: String? by mutableStateOf(null)
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

    /** The "bind a new songbook" dialog (name and cover) for the current picker targets. */
    var newSongbookDialogVisible: Boolean by mutableStateOf(false)
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

    /**
     * Session-only override for the sidebar collapse state. Compose collapses the sidebar
     * while it is open without touching the persisted [AppSettings.sidebarCollapsed].
     */
    var temporarySidebarCollapsed: Boolean? by mutableStateOf(null)
        private set

    val libraryArrangement: LibraryArrangement
        get() = libraryArrangementFor(browseMode, query.favoritesOnly)

    var quickFindVisible: Boolean by mutableStateOf(false)
        private set

    var metronomeVisible: Boolean by mutableStateOf(false)
        private set

    /** The score a quick find action applies to: the one in the viewer, else a single focused/selected score. */
    val quickFindTargetScoreId: String?
        get() = (fullscreenTarget as? ViewerTarget.Score)?.itemId
            ?: pickerTargetIds.singleOrNull()

    fun selectSection(section: AppSection) {
        enterSection(section)
    }

    fun isSidebarCollapsed(settings: AppSettings): Boolean =
        temporarySidebarCollapsed ?: settings.sidebarCollapsed

    fun toggleSidebarCollapsed() {
        val temporary = temporarySidebarCollapsed
        if (temporary != null) {
            temporarySidebarCollapsed = !temporary
            return
        }
        updateStoredSettings { settings -> settings.copy(sidebarCollapsed = !settings.sidebarCollapsed) }
    }

    fun selectArrangement(arrangement: LibraryArrangement) {
        enterSection(AppSection.LIBRARY)
        if (browseMode != arrangement.browseMode) {
            updateBrowseMode(arrangement.browseMode)
        }
        val favoritesOnly = arrangement == LibraryArrangement.FAVOURITES
        if (query.favoritesOnly != favoritesOnly) {
            updateQuery { copy(favoritesOnly = favoritesOnly) }
        }
    }

    fun updateStartSidebarCollapsed(collapsed: Boolean) {
        updateStoredSettings { settings -> settings.copy(sidebarCollapsed = collapsed) }
    }

    fun updateViewerChromeAutoHideSeconds(seconds: Int?) {
        updateStoredSettings { settings -> settings.copy(viewerChromeAutoHideSeconds = seconds) }
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

    fun updateViewerSupportFilter(filter: ViewerSupportFilter) {
        updateQuery { copy(viewerSupport = filter) }
    }

    fun clearRefinements() {
        updateQuery {
            copy(
                selectedTags = emptySet(),
                selectedCollection = null,
                viewerSupport = ViewerSupportFilter.ALL,
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
        // Rearranging keeps focus; flipping between hidden and visible scores swaps the whole list.
        if ((mode == LibraryBrowseMode.HIDDEN) != (browseMode == LibraryBrowseMode.HIDDEN)) {
            clearScoreFocus()
        } else {
            selectionAnchorId = null
        }
        browseMode = mode
        selectedGroup = null
    }

    fun selectGroup(groupName: String) {
        clearScoreSelection()
        selectionAnchorId = null
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
                viewerSupport = ViewerSupportFilter.ALL,
                favoritesOnly = false,
            )
        }
    }

    /** A plain click: make [itemId] the focused score and drop any multi-selection. */
    fun focusScore(itemId: String) {
        if (focusedScoreId != itemId) {
            dismissFocusedScoreExtras()
        }
        focusedScoreId = itemId
        selectionAnchorId = itemId
        if (selectedScoreIds.isNotEmpty()) {
            clearScoreSelection()
        }
    }

    fun clearFocus() {
        focusedScoreId = null
        dismissFocusedScoreExtras()
    }

    /** Clears focus when the focused score is no longer among [availableItemIds], e.g. after filtering. */
    fun reconcileFocus(availableItemIds: Set<String>) {
        val focused = focusedScoreId ?: return
        if (focused !in availableItemIds) {
            clearFocus()
        }
    }

    /** Moves focus [delta] rows through [orderedItemIds], starting at the first row when nothing is focused. */
    fun moveFocus(orderedItemIds: List<String>, delta: Int) {
        val nextId = nextFocusedItemId(orderedItemIds, focusedScoreId, delta) ?: return
        focusScore(nextId)
    }

    /** Ctrl/Cmd-click or the row checkbox. */
    fun toggleScoreSelection(itemId: String) {
        selectedScoreIds = selectedScoreIds.toMutableSet().apply {
            if (!add(itemId)) {
                remove(itemId)
            }
        }.toSet()
        selectionAnchorId = itemId
        if (selectedScoreIds.isEmpty()) {
            setlistPickerVisible = false
            songbookPickerVisible = false
        }
    }

    /** Shift-click: select every score between the anchor (last clicked score) and [itemId]. */
    fun extendScoreSelection(itemId: String, orderedItemIds: List<String>) {
        val range = selectionRange(
            orderedItemIds = orderedItemIds,
            anchorId = selectionAnchorId ?: focusedScoreId,
            targetId = itemId,
        )
        if (range.isEmpty()) {
            toggleScoreSelection(itemId)
            return
        }
        selectedScoreIds = selectedScoreIds + range
    }

    fun clearScoreSelection() {
        selectedScoreIds = emptySet()
        setlistPickerVisible = false
        songbookPickerVisible = false
    }

    /** Scores the setlist and songbook pickers act on: the multi-selection, else the focused score. */
    val pickerTargetIds: Set<String>
        get() = selectedScoreIds.ifEmpty { setOfNotNull(focusedScoreId) }

    /** Ctrl/Cmd-click: toggle the score in the multi-selection and focus it, keeping the selection. */
    fun toggleScoreSelectionAndFocus(itemId: String) {
        toggleScoreSelection(itemId)
        focusWithoutClearingSelection(itemId)
    }

    /** Shift-click: extend the multi-selection from the anchor to [itemId] and focus it. */
    fun selectScoreRange(orderedIds: List<String>, itemId: String) {
        extendScoreSelection(itemId, orderedIds)
        focusWithoutClearingSelection(itemId)
    }

    /** Drops the focused score and the range anchor, e.g. when the visible list changes wholesale. */
    fun clearScoreFocus() {
        clearFocus()
        selectionAnchorId = null
    }

    private fun focusWithoutClearingSelection(itemId: String) {
        if (focusedScoreId != itemId) {
            dismissFocusedScoreExtras()
        }
        focusedScoreId = itemId
    }

    fun showSetlistPicker() {
        if (pickerTargetIds.isNotEmpty()) {
            songbookPickerVisible = false
            setlistPickerVisible = true
        }
    }

    fun showSetlistPickerForScore(itemId: String) {
        if (runtime.repository.getItem(itemId) == null) {
            bannerMessage = "That score is no longer available."
            return
        }

        focusScoreFromViewer(itemId)
        songbookPickerVisible = false
        setlistPickerVisible = true
    }

    fun dismissSetlistPicker() {
        setlistPickerVisible = false
    }

    fun showSongbookPicker() {
        if (pickerTargetIds.isNotEmpty()) {
            setlistPickerVisible = false
            songbookPickerVisible = true
        }
    }

    fun showSongbookPickerForScore(itemId: String) {
        if (!runtime.capabilities.songbookCreationSupported) {
            bannerMessage = "Songbook creation isn't available on this device."
            return
        }
        if (runtime.repository.getItem(itemId) == null) {
            bannerMessage = "That score is no longer available."
            return
        }

        focusScoreFromViewer(itemId)
        setlistPickerVisible = false
        songbookPickerVisible = true
    }

    fun dismissSongbookPicker() {
        songbookPickerVisible = false
    }

    fun showNewSongbookDialog() {
        if (!runtime.capabilities.songbookCreationSupported || pickerTargetIds.isEmpty()) return
        songbookPickerVisible = false
        newSongbookDialogVisible = true
    }

    fun dismissNewSongbookDialog() {
        newSongbookDialogVisible = false
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
        clearSetlistViewerTargets(TEMPORARY_SETLIST_ID)
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

    fun selectSongbook(songbookId: String) {
        enterSection(AppSection.SONGBOOKS)
        selectedSongbookId = songbookId
    }

    fun clearSongbookSelection() {
        selectedSongbookId = null
    }

    fun showAddScores(target: AddScoresTarget) {
        addScoresTarget = target
    }

    fun dismissAddScores() {
        addScoresTarget = null
    }

    /** The visible pieces of a saved setlist or of the Random Mix, in programme order. */
    fun setlistItems(setlistId: String): List<SheetMusicItem> = resolveSetlistItems(
        snapshot = runtime.repository.library.value,
        setlistId = setlistId,
        temporarySetlist = temporarySetlist,
    )

    fun startSetlist(setlistId: String, index: Int = 0) {
        val items = setlistItems(setlistId)
        if (items.isEmpty()) {
            bannerMessage = "Add scores to this setlist before starting it."
            return
        }

        showProgramme(setlistId)
        val target = ViewerTarget.Setlist(setlistId, index.coerceIn(0, items.lastIndex))
        viewerStartPage = null
        fullscreenTarget = target
        recordOpened(items[target.index])
    }

    fun hasNextInSetlist(target: ViewerTarget?): Boolean {
        val setlistTarget = target as? ViewerTarget.Setlist ?: return false
        return setlistTarget.next(setlistItems(setlistTarget.setlistId).size) != null
    }

    fun nextInSetlist() {
        val current = fullscreenTarget as? ViewerTarget.Setlist ?: return
        val items = setlistItems(current.setlistId)
        val next = current.next(items.size)
        if (next == null) {
            bannerMessage = "That was the last piece in the set."
            return
        }

        viewerStartPage = null
        fullscreenTarget = next
        recordOpened(items[next.index])
    }

    fun renameSetlist(setlistId: String, name: String) {
        scope.launch {
            try {
                val setlist = runtime.repository.renameSetlist(setlistId, name)
                bannerMessage = "Renamed to \"${setlist.name}\"."
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not rename the setlist."
            }
        }
    }

    /** Applies a new order for the pieces shown in the programme; hidden pieces keep their slots. */
    fun reorderSetlist(setlistId: String, visibleItemIds: List<String>) {
        if (setlistId == TEMPORARY_SETLIST_ID) {
            val session = temporarySetlist ?: return
            temporarySetlist = session.copy(
                itemIds = mergeVisibleSetlistOrder(session.itemIds, visibleItemIds),
            )
            return
        }

        val setlist = runtime.repository.getSetlist(setlistId) ?: return
        val reorderedIds = mergeVisibleSetlistOrder(setlist.itemIds, visibleItemIds)
        if (reorderedIds == setlist.itemIds) return
        scope.launch {
            try {
                runtime.repository.reorderSetlist(setlistId, reorderedIds)
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not reorder the setlist."
            }
        }
    }

    fun removeFromSetlist(setlistId: String, itemId: String) {
        if (setlistId == TEMPORARY_SETLIST_ID) {
            val session = temporarySetlist ?: return
            temporarySetlist = session.copy(itemIds = session.itemIds - itemId)
            return
        }

        scope.launch {
            try {
                runtime.repository.removeFromSetlist(setlistId, listOf(itemId))
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not update the setlist."
            }
        }
    }

    fun exportSongbook(songbookId: String) {
        scope.launch {
            val songbook = runtime.repository.getSongbook(songbookId)
            if (songbook == null) {
                bannerMessage = "That songbook no longer exists."
                return@launch
            }
            if (!runtime.capabilities.scoreDownloadSupported) {
                bannerMessage = "PDF export isn't available on this device."
                return@launch
            }

            try {
                val destination = runtime.pdfExporter.export(
                    documentPath = songbook.document.storedPath,
                    suggestedFileName = songbook.document.originalFileName,
                )
                bannerMessage = "Saved \"${songbook.name}\" to $destination."
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not export \"${songbook.name}\"."
            }
        }
    }

    /**
     * Rebinds the songbook PDF behind a newly picked cover page. The existing score pages are
     * carried over from the bound document (minus any previous cover) rather than rebuilt from
     * [LibrarySongbook.itemIds], which is de-duplicated and can't reproduce repeated scores.
     */
    fun changeSongbookCover(songbookId: String) {
        scope.launch {
            if (!canCreateSongbooks || !canPickSongbookCover) {
                bannerMessage = "Songbook covers aren't available on this device."
                return@launch
            }
            if (existingSongbook(songbookId) == null) return@launch

            try {
                val coverImage = runtime.coverImagePicker.pickCoverImage() ?: return@launch
                songbookMutationMutex.withLock {
                    // Re-read under the lock: scores may have been added while the picker was open.
                    val songbook = existingSongbook(songbookId) ?: return@withLock
                    val hasCover = songbook.resolveHasCover(
                        runtime.repository.library.value.items.associateBy { it.id },
                    )
                    if (hasCover == null) {
                        // Guessing wrong would either keep the old cover or drop a score page.
                        bannerMessage = "Couldn't tell whether \"${songbook.name}\" already has a cover, " +
                            "so it was left unchanged."
                        return@withLock
                    }
                    val buildResult = runtime.songbookBuilder.buildSongbook(
                        songbookName = songbook.name,
                        appendedDocumentPaths = emptyList(),
                        existingSongbookPath = songbook.document.storedPath,
                        coverImage = coverImage,
                        dropExistingCoverPage = hasCover,
                    )
                    runtime.repository.replaceSongbookDocument(
                        songbookId = songbookId,
                        document = buildResult.document,
                        pageCount = buildResult.pageCount,
                        hasCover = true,
                    )
                    bannerMessage = "Updated the cover of \"${songbook.name}\"."
                }
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not change the songbook cover."
            }
        }
    }

    private fun existingSongbook(songbookId: String): LibrarySongbook? =
        runtime.repository.getSongbook(songbookId).also { songbook ->
            if (songbook == null) bannerMessage = "That songbook no longer exists."
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

        openScore(randomItem)
        bannerMessage = "Opening a random sheet."
    }

    fun openRandomSheetInCurrentViewer(items: List<SheetMusicItem>) {
        val randomItem = buildRandomSightReadingSelection(
            items = items,
            requestedCount = 1,
        ).firstOrNull()

        if (randomItem == null) {
            bannerMessage = "No scores are available for a random pick."
            return
        }

        openScore(randomItem)
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
        // Always route through focusScore so an active multi-selection (which hides the detail panel) is cleared.
        focusScore(itemId)
        dismissScoreInfo()
        editingItemId = itemId
    }

    fun dismissEditor() {
        editingItemId = null
    }

    fun showScoreInfo(itemId: String) {
        if (runtime.repository.getItem(itemId) == null) {
            bannerMessage = "That score is no longer available."
            return
        }

        focusScore(itemId)
        editingItemId = null
        infoItemId = itemId
        infoMetadata = null
        infoMetadataError = null
        infoMetadataLoading = true
        scope.launch {
            runCatching {
                runtime.repository.getScoreMetadata(itemId)
            }.onSuccess { metadata ->
                if (infoItemId == itemId) {
                    infoMetadata = metadata
                    infoMetadataError = null
                }
            }.onFailure { error ->
                if (infoItemId == itemId) {
                    infoMetadata = null
                    infoMetadataError = error.message ?: "Score info is unavailable."
                }
            }
            if (infoItemId == itemId) {
                infoMetadataLoading = false
            }
        }
    }

    fun dismissScoreInfo() {
        infoItemId = null
        infoMetadata = null
        infoMetadataError = null
        infoMetadataLoading = false
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

    fun updateViewerKeybinding(
        action: ViewerShortcutAction,
        keyId: String?,
    ) {
        updateStoredSettings { settings ->
            settings.copy(
                viewerKeybindings = settings.viewerKeybindings.withBinding(
                    action = action,
                    keyId = keyId,
                )
            )
        }
    }

    fun clearViewerKeybinding(action: ViewerShortcutAction) {
        updateStoredSettings { settings ->
            settings.copy(
                viewerKeybindings = settings.viewerKeybindings.clear(action)
            )
        }
    }

    fun resetViewerKeybindings() {
        updateStoredSettings { settings ->
            settings.copy(viewerKeybindings = ViewerKeybindings())
        }
        bannerMessage = "Viewer keybindings reset to defaults."
    }

    fun updateSkin(skin: AppSkin) {
        updateStoredSettings { settings -> settings.copy(skin = skin) }
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

    fun importDroppedPaths(paths: List<String>) {
        val normalizedPaths = paths
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        if (normalizedPaths.isEmpty()) return

        runImport(
            startMessage = "Starting drag-and-drop import.",
            selectionMessage = "Reading dropped files and folders.",
            resultMessage = { count ->
                "Drop contained $count score file${if (count == 1) "" else "s"}."
            },
            importerAction = { runtime.importer.importDroppedPaths(normalizedPaths) },
        )
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
            runtime.syncManager.forgetSource(sourceId)
            runtime.sourceRegistry.removeSource(sourceId)
            bannerMessage = "Source removed."
        }
    }

    fun updateCloudSource(source: CloudLibrarySource) {
        scope.launch {
            try {
                val sourceToPersist = when (source) {
                    is CloudGoogleDriveSource -> source.copy(
                        settings = source.settings.copy(accessToken = ""),
                    )
                    else -> source
                }
                runtime.syncManager.clearGoogleAccessToken(source.id)
                runtime.sourceRegistry.updateSource(sourceToPersist)
                bannerMessage = when (sourceToPersist) {
                    is CloudGoogleDriveSource -> {
                        if (sourceToPersist.settings.refreshToken.isBlank()) {
                            "Cloud source updated. Press Refresh to reconnect Google Drive."
                        } else {
                            "Cloud source updated. Press Refresh to sync with the new Google Drive token."
                        }
                    }
                    else -> "Cloud source updated."
                }
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Cloud source update failed."
            }
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

    /**
     * Opens [item] straight in the viewer, at its saved page or, with [fromTop], at page 1.
     * In the catalogue the score also becomes focused, so the library shows it when the viewer closes.
     */
    fun openScore(item: SheetMusicItem, fromTop: Boolean = false) {
        val startPage = if (fromTop) 0 else item.lastViewedPage.coerceAtLeast(0)
        focusLibrarySection()
        if (selectedSection == AppSection.LIBRARY) {
            if (focusedScoreId != item.id) {
                dismissFocusedScoreExtras()
            }
            focusedScoreId = item.id
            selectionAnchorId = item.id
        }
        viewerStartPage = startPage
        fullscreenTarget = ViewerTarget.Score(item.id)
        scope.launch {
            runtime.repository.recordOpened(item.id, startPage)
        }
    }

    fun openSongbook(songbookId: String) {
        focusLibrarySection()
        viewerStartPage = null
        fullscreenTarget = ViewerTarget.Songbook(songbookId)
    }

    fun openQuickFind() {
        quickFindVisible = true
    }

    fun dismissQuickFind() {
        quickFindVisible = false
    }

    /** Opens a score picked in quick find in the viewer, replacing whatever the viewer shows. */
    fun openScoreFromQuickFind(item: SheetMusicItem) {
        dismissQuickFind()
        openScore(item)
    }

    fun openSetlistFromQuickFind(setlistId: String) {
        dismissQuickFind()
        closeViewers()
        dismissLibraryTransientUi()
        openSetlist(setlistId)
    }

    fun openSongbookFromQuickFind(songbookId: String) {
        dismissQuickFind()
        openSongbook(songbookId)
    }

    fun runQuickFindAction(
        action: QuickFindAction,
        randomScopeItems: List<SheetMusicItem>,
    ) {
        dismissQuickFind()
        when (action) {
            QuickFindAction.OPEN_RANDOM_SCORE -> openRandomSheetInCurrentViewer(randomScopeItems)
            QuickFindAction.START_METRONOME -> metronomeVisible = true
            QuickFindAction.TOGGLE_TWO_PAGE_VIEW -> toggleDefaultViewerPageMode()
            QuickFindAction.ADD_FOCUSED_SCORE_TO_SETLIST -> quickFindTargetScoreId?.let(::showSetlistPickerForScore)
                ?: run { bannerMessage = "Open or select a score first." }
            QuickFindAction.SWITCH_SKIN -> switchToNextSkin()
        }
    }

    fun dismissMetronome() {
        metronomeVisible = false
    }

    fun toggleDefaultViewerPageMode() {
        val twoPage = runtime.settingsStore.settings.value.defaultViewerPageMode == ViewerPageModePreference.TWO_PAGE
        val next = if (twoPage) ViewerPageModePreference.SINGLE_PAGE else ViewerPageModePreference.TWO_PAGE
        updateDefaultViewerPageMode(next)
        bannerMessage = if (twoPage) "Two-page view off." else "Two-page view on."
    }

    fun switchToNextSkin() {
        val skins = AppSkin.entries
        val current = runtime.settingsStore.settings.value.resolvedSkin
        updateSkin(skins[(skins.indexOf(current) + 1) % skins.size])
    }

    fun closeFullscreen() {
        val target = fullscreenTarget
        fullscreenTarget = null
        viewerStartPage = null
        if (target is ViewerTarget.Setlist) {
            showProgramme(target.setlistId)
        } else {
            focusLibrarySection()
        }
    }

    /** Opens [target] (a score, songbook or setlist piece) straight in the viewer. */
    fun openFullscreen(target: ViewerTarget) {
        focusLibrarySection()
        viewerStartPage = null
        fullscreenTarget = target
    }

    fun toggleFavorite(itemId: String) {
        scope.launch {
            runtime.repository.toggleFavorite(itemId)
        }
    }

    fun hideScore(itemId: String) {
        scope.launch {
            runtime.repository.updateHidden(itemId, isHidden = true)
            forgetScore(itemId)
            bannerMessage = "Score hidden. You can restore it from Hidden."
        }
    }

    fun restoreScore(itemId: String) {
        scope.launch {
            runtime.repository.updateHidden(itemId, isHidden = false)
            forgetScore(itemId)
            bannerMessage = "Score restored to your library."
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
            thumbnailCache.clearKey(itemId)
            forgetScore(itemId)
            deleteCandidateItemId = null
            focusLibrarySection()
            bannerMessage = "Score removed from your library."
        }
    }

    /** Creates a setlist; with [open] the Setlists section jumps to its new programme. */
    fun createSetlist(name: String, itemIds: List<String>, open: Boolean = false) {
        scope.launch {
            try {
                val setlist = runtime.repository.createSetlist(name, itemIds)
                if (open) {
                    showSavedSetlist(setlist = setlist, banner = "Saved \"${setlist.name}\".")
                } else {
                    bannerMessage = "Saved \"${setlist.name}\"."
                }
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not save the setlist."
            }
        }
    }

    fun addSelectionToSetlist(setlistId: String, itemIds: List<String>) {
        if (setlistId == TEMPORARY_SETLIST_ID) {
            val session = temporarySetlist ?: return
            temporarySetlist = session.copy(itemIds = (session.itemIds + itemIds).distinct())
            showTemporarySetlist()
            return
        }
        scope.launch {
            try {
                val setlist = runtime.repository.addItemsToSetlist(setlistId, itemIds)
                bannerMessage = "Added to \"${setlist.name}\"."
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
                    hasCover = coverImage != null,
                )
                songbookPickerVisible = false
                newSongbookDialogVisible = false
                enterSection(AppSection.SONGBOOKS)
                selectedSongbookId = songbook.id
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

            try {
                songbookMutationMutex.withLock {
                    appendToSongbook(songbookId, selectedItems)
                }
            } catch (error: Throwable) {
                bannerMessage = error.message ?: "Could not update the songbook."
            }
        }
    }

    private suspend fun appendToSongbook(
        songbookId: String,
        selectedItems: List<SheetMusicItem>,
    ) {
        val existingSongbook = runtime.repository.getSongbook(songbookId)
        if (existingSongbook == null) {
            bannerMessage = "That songbook no longer exists."
            return
        }

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
        enterSection(AppSection.SONGBOOKS)
        selectedSongbookId = updatedSongbook.id
        bannerMessage = "Added to \"${updatedSongbook.name}\"."
    }

    fun confirmDeleteSetlist() {
        val setlistId = deleteCandidateSetlistId ?: return
        scope.launch {
            runtime.repository.deleteSetlist(setlistId)
            if (selectedSetlistId == setlistId) {
                selectedSetlistId = null
            }
            clearSetlistViewerTargets(setlistId)
            deleteCandidateSetlistId = null
            bannerMessage = "Setlist deleted."
        }
    }

    fun confirmDeleteSongbook() {
        val songbookId = deleteCandidateSongbookId ?: return
        scope.launch {
            songbookMutationMutex.withLock { runtime.repository.deleteSongbook(songbookId) }
            if (selectedSongbookId == songbookId) {
                selectedSongbookId = null
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

    private fun recordOpened(item: SheetMusicItem) {
        scope.launch {
            runtime.repository.recordOpened(item.id, item.lastViewedPage)
        }
    }

    private fun showProgramme(setlistId: String) {
        if (setlistId == TEMPORARY_SETLIST_ID) {
            if (temporarySetlist != null) {
                showTemporarySetlist()
            }
        } else if (runtime.repository.getSetlist(setlistId) != null) {
            showSavedSetlist(setlistId)
        }
    }

    private fun clearSetlistViewerTargets(setlistId: String) {
        if ((fullscreenTarget as? ViewerTarget.Setlist)?.setlistId == setlistId) {
            fullscreenTarget = null
        }
    }

    private fun closeViewers() {
        fullscreenTarget = null
        viewerStartPage = null
    }

    /**
     * Score dialogs (setlist/songbook pickers) live in the library-hosted sections; stay in
     * Setlists or Songbooks if already there, otherwise fall back to Library.
     */
    private fun focusLibrarySection() {
        if (!selectedSection.isLibraryHosted) {
            enterSection(AppSection.LIBRARY)
        }
    }

    /**
     * The single transition for changing top-level sections. Switching clears the score
     * selection and transient dialogs, closes any open setlist detail and manages the
     * Compose-only sidebar collapse.
     */
    private fun enterSection(section: AppSection) {
        if (selectedSection == section) return
        clearScoreSelection()
        if (section != AppSection.LIBRARY) {
            clearFocus()
        }
        dismissLibraryTransientUi()
        closeSetlistDetail()
        selectedSongbookId = null
        selectedSection = section
        temporarySidebarCollapsed = if (section == AppSection.COMPOSE) true else null
    }

    /** Setlists can show the focused score's detail panel, so stay there; anywhere else lands in Library. */
    private fun focusScoreFromViewer(itemId: String) {
        if (selectedSection != AppSection.SETLISTS) {
            enterSection(AppSection.LIBRARY)
        }
        fullscreenTarget = null
        viewerStartPage = null
        if (selectedSection == AppSection.LIBRARY) {
            revealScoreThroughFilters(itemId)
        }
        focusScore(itemId)
    }

    /** Clears filters that would hide [itemId] so its detail panel and anchored pickers can render. */
    private fun revealScoreThroughFilters(itemId: String) {
        val item = runtime.repository.getItem(itemId) ?: return
        val itemQuery = query.copy(
            hiddenFilter = if (item.isHidden) HiddenScoreFilter.HIDDEN else HiddenScoreFilter.VISIBLE,
        )
        if (listOf(item).applyLibraryQuery(itemQuery).isEmpty()) {
            clearFilters()
        }
    }

    private fun dismissFocusedScoreExtras() {
        editingItemId = null
        dismissScoreInfo()
        setlistPickerVisible = false
        songbookPickerVisible = false
    }

    /** Drops every reference to a score that just left the current view (hidden, restored or deleted). */
    private fun forgetScore(itemId: String) {
        selectedScoreIds = selectedScoreIds - itemId
        if (selectedScoreIds.isEmpty()) {
            setlistPickerVisible = false
            songbookPickerVisible = false
        }
        if (focusedScoreId == itemId) {
            clearFocus()
        }
        if (fullscreenTarget == ViewerTarget.Score(itemId)) {
            fullscreenTarget = null
            viewerStartPage = null
        }
        if (editingItemId == itemId) {
            editingItemId = null
        }
        if (infoItemId == itemId) {
            dismissScoreInfo()
        }
    }

    private fun dismissLibraryTransientUi() {
        setlistPickerVisible = false
        songbookPickerVisible = false
        newSongbookDialogVisible = false
        randomSetlistBuilderVisible = false
        saveTemporarySetlistDialogVisible = false
        addScoresTarget = null
        dismissScoreInfo()
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
        enterSection(AppSection.SETLISTS)
        saveTemporarySetlistDialogVisible = false
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
        enterSection(AppSection.SETLISTS)
        clearScoreSelection()
        selectedSetlistId = null
        temporarySetlistOpen = true
    }

    private fun runImport(
        startMessage: String = "Starting local import.",
        selectionMessage: String = "Opening file picker.",
        resultMessage: (Int) -> String = { count ->
            "Importer returned $count score file${if (count == 1) "" else "s"}."
        },
        importerAction: suspend () -> ImportRequestResult,
    ) {
        if (importInProgress) return

        scope.launch {
            importInProgress = true
            startImportActivity(startMessage)
            try {
                appendImportActivity(
                    message = selectionMessage,
                    currentStep = "Selecting local scores",
                )
                val result = importerAction()
                appendImportActivity(
                    message = resultMessage(result.documents.size),
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
                    uploadImportedScoresToGoogleDrive(imported)
                }
                bannerMessage = when {
                    imported.isNotEmpty() -> {
                        enterSection(AppSection.LIBRARY)
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
                        enterSection(AppSection.LIBRARY)
                        clearFilters()
                        val scores = imported.size
                        appendImportActivity(
                            message = "$sourceLabel imported $scores score${if (scores == 1) "" else "s"} into the library.",
                            currentStep = "Import complete",
                            processedDelta = scores,
                        )
                        uploadImportedScoresToGoogleDrive(imported)
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

    private suspend fun uploadImportedScoresToGoogleDrive(imported: List<SheetMusicItem>) {
        val summary = runtime.syncManager.uploadImportedItemsToGoogleDrive(
            sources = runtime.sourceRegistry.sources.value,
            items = imported,
        )
        if (summary.attemptedCount == 0) return

        when {
            summary.uploadedCount > 0 && summary.errors.isEmpty() -> appendImportActivity(
                message = "Uploaded ${summary.uploadedCount} score${if (summary.uploadedCount == 1) "" else "s"} to Google Drive.",
                currentStep = "Google Drive upload complete",
            )
            summary.uploadedCount > 0 -> appendImportActivities(
                messages = listOf(
                    "Uploaded ${summary.uploadedCount} score${if (summary.uploadedCount == 1) "" else "s"} to Google Drive.",
                ) + summary.errors,
                currentStep = "Google Drive upload completed with issues",
            )
            summary.errors.isNotEmpty() -> appendImportActivities(
                messages = summary.errors,
                currentStep = "Google Drive upload failed",
            )
        }
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

/** Ids from [anchorId] to [targetId] inclusive, in list order; empty when either is not listed. */
internal fun selectionRange(
    orderedItemIds: List<String>,
    anchorId: String?,
    targetId: String,
): List<String> {
    val anchorIndex = anchorId?.let(orderedItemIds::indexOf) ?: -1
    val targetIndex = orderedItemIds.indexOf(targetId)
    if (anchorIndex < 0 || targetIndex < 0) return emptyList()
    val range = if (anchorIndex <= targetIndex) anchorIndex..targetIndex else targetIndex..anchorIndex
    return orderedItemIds.slice(range)
}

/** The id [delta] rows away from [currentId], clamped to the list; the first row when nothing matches. */
internal fun nextFocusedItemId(
    orderedItemIds: List<String>,
    currentId: String?,
    delta: Int,
): String? {
    if (orderedItemIds.isEmpty()) return null
    val currentIndex = currentId?.let(orderedItemIds::indexOf) ?: -1
    if (currentIndex < 0) return orderedItemIds.first()
    return orderedItemIds[(currentIndex + delta).coerceIn(0, orderedItemIds.lastIndex)]
}

private fun LibraryLayoutPreference.toLayoutMode(): LibraryLayoutMode = when (this) {
    LibraryLayoutPreference.LIST -> LibraryLayoutMode.LIST
    LibraryLayoutPreference.GRID -> LibraryLayoutMode.GRID
}

private fun LibraryLayoutMode.toPreference(): LibraryLayoutPreference = when (this) {
    LibraryLayoutMode.LIST -> LibraryLayoutPreference.LIST
    LibraryLayoutMode.GRID -> LibraryLayoutPreference.GRID
}
