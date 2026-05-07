package com.ryanjames.lunar.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.data.SyncStatus
import com.ryanjames.lunar.library.model.HiddenScoreFilter
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.library.model.LibrarySetlist
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.ScoreMetadata
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.library.model.applyLibraryQuery
import com.ryanjames.lunar.library.model.availableCollections
import com.ryanjames.lunar.library.model.availableComposers
import com.ryanjames.lunar.library.model.availableTags
import com.ryanjames.lunar.platform.PlatformRuntime
import com.ryanjames.lunar.platform.SelectedCoverImage
import com.ryanjames.lunar.settings.ViewerKeybindings
import com.ryanjames.lunar.settings.ViewerShortcutAction
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import kotlin.time.Instant

@Composable
fun LibraryScreen(
    runtime: PlatformRuntime,
    snapshot: LibrarySnapshot,
    appState: LunarAppState,
    viewerKeybindings: ViewerKeybindings = ViewerKeybindings(),
    modifier: Modifier = Modifier,
) {
    val screenModel = buildLibraryScreenModel(snapshot = snapshot, appState = appState)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onKeyEvent false
                }
                if (viewerKeybindings.actionForKeyId(viewerShortcutKeyId(event.key)) != ViewerShortcutAction.OPEN_RANDOM_SCORE) {
                    return@onKeyEvent false
                }
                appState.openRandomSheet(screenModel.currentScopeItems)
                true
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderPanel(
            visibleCount = screenModel.headerVisibleCount,
            headerTotalCount = screenModel.headerTotalCount,
            visibleScoreCount = screenModel.visibleScoreCount,
            hiddenScoreCount = screenModel.hiddenScoreCount,
        )
        BrowseModeSwitcher(appState = appState)
        if (
            screenModel.visibleScoreCount > 0 &&
            appState.browseMode != LibraryBrowseMode.SONGBOOKS &&
            appState.browseMode != LibraryBrowseMode.HIDDEN
        ) {
            RandomLibraryActionsRow(
                availableCount = screenModel.currentScopeItems.size,
                onOpenRandomSheet = { appState.openRandomSheet(screenModel.currentScopeItems) },
                onCreateRandomSetlist = appState::showRandomSetlistBuilder,
            )
        }
        if (
            appState.browseMode != LibraryBrowseMode.SETLISTS &&
            appState.browseMode != LibraryBrowseMode.SONGBOOKS
        ) {
            SearchAndSortBar(appState = appState)
            RefineLibraryPanel(
                availableCollections = screenModel.filterCollections,
                availableTags = screenModel.filterTags,
                visibleCount = screenModel.visibleItems.size,
                totalCount = screenModel.refinementTotalCount,
                appState = appState,
            )
        }
        if (screenModel.selectedItems.isNotEmpty() && appState.browseMode != LibraryBrowseMode.HIDDEN) {
            SelectionActionBar(
                selectedCount = screenModel.selectedItems.size,
                onAddToSetlist = appState::showSetlistPicker,
                onAddToSongbook = appState::showSongbookPicker,
                canCreateSongbooks = appState.canCreateSongbooks,
                onClear = appState::clearScoreSelection,
            )
        }

        // Main content area
        Box(modifier = Modifier.weight(1f)) {
            when (appState.browseMode) {
                LibraryBrowseMode.ALL -> {
                    if (screenModel.visibleItems.isEmpty()) {
                        EmptyLibraryState(
                            totalItemCount = screenModel.visibleScoreCount,
                            onOpenImport = { appState.selectSection(AppSection.IMPORT) },
                            onClearFilters = appState::clearFilters,
                        )
                    } else {
                        FlatScoreList(items = screenModel.visibleItems, appState = appState)
                    }
                }

                LibraryBrowseMode.HIDDEN -> {
                    if (screenModel.visibleItems.isEmpty()) {
                        HiddenScoresEmptyState(
                            hiddenCount = screenModel.hiddenScoreCount,
                            onClearFilters = appState::clearFilters,
                        )
                    } else {
                        FlatScoreList(
                            items = screenModel.visibleItems,
                            appState = appState,
                            mode = ScoreListMode.HIDDEN,
                        )
                    }
                }

                LibraryBrowseMode.BY_COLLECTION -> {
                    val group = appState.selectedGroup
                    if (group == null) {
                        GroupList(
                            groups = screenModel.collections,
                            itemsByGroup = screenModel.collectionGroups,
                            emptyLabel = "No collections found. Import PDFs with a collection folder strategy.",
                            onSelectGroup = appState::selectGroup,
                        )
                    } else {
                        DrillDownScoreList(
                            groupName = group,
                            items = screenModel.visibleItems.itemsInBrowseGroup(
                                browseMode = LibraryBrowseMode.BY_COLLECTION,
                                groupName = group,
                            ),
                            appState = appState,
                            onBack = appState::clearGroupSelection,
                        )
                    }
                }

                LibraryBrowseMode.BY_COMPOSER -> {
                    val group = appState.selectedGroup
                    if (group == null) {
                        GroupList(
                            groups = screenModel.composers,
                            itemsByGroup = screenModel.composerGroups,
                            emptyLabel = "No composers found. Import PDFs with a composer folder strategy.",
                            onSelectGroup = appState::selectGroup,
                        )
                    } else {
                        DrillDownScoreList(
                            groupName = group,
                            items = screenModel.visibleItems.itemsInBrowseGroup(
                                browseMode = LibraryBrowseMode.BY_COMPOSER,
                                groupName = group,
                            ),
                            appState = appState,
                            onBack = appState::clearGroupSelection,
                        )
                    }
                }

                LibraryBrowseMode.SETLISTS -> {
                    val activeTemporarySetlist = screenModel.activeTemporarySetlist
                    val activeSetlist = screenModel.activeSetlist
                    if (activeTemporarySetlist != null) {
                        TemporarySetlistDetailView(
                            session = activeTemporarySetlist,
                            items = screenModel.activeTemporarySetlistItems,
                            appState = appState,
                            onBack = appState::closeSetlist,
                            onSave = appState::showSaveTemporarySetlistDialog,
                            onDiscard = appState::discardTemporarySetlist,
                        )
                    } else if (activeSetlist == null) {
                        SetlistsOverview(
                            temporarySetlist = screenModel.temporarySetlist,
                            setlists = snapshot.setlists,
                            itemsById = screenModel.itemsById,
                            onOpenTemporarySetlist = appState::openTemporarySetlist,
                            onSaveTemporarySetlist = appState::showSaveTemporarySetlistDialog,
                            onDiscardTemporarySetlist = appState::discardTemporarySetlist,
                            onOpenSetlist = appState::openSetlist,
                            onDeleteSetlist = appState::requestDeleteSetlist,
                            onOpenLibrary = {
                                appState.updateBrowseMode(LibraryBrowseMode.ALL)
                            },
                        )
                    } else {
                        SetlistDetailView(
                            setlist = activeSetlist,
                            items = screenModel.activeSetlistItems,
                            appState = appState,
                            onBack = appState::closeSetlist,
                            onDeleteSetlist = {
                                appState.requestDeleteSetlist(activeSetlist.id)
                            },
                        )
                    }
                }

                LibraryBrowseMode.SONGBOOKS -> {
                    SongbooksOverview(
                        songbooks = snapshot.songbooks,
                        itemsById = screenModel.itemsById,
                        onOpenSongbook = appState::openSongbook,
                        onDeleteSongbook = appState::requestDeleteSongbook,
                        onOpenLibrary = {
                            appState.updateBrowseMode(LibraryBrowseMode.ALL)
                        },
                    )
                }
            }
        }
    }

    val editingItem = screenModel.editingItem
    if (editingItem != null) {
        MetadataEditorDialog(
            item = editingItem,
            onDismiss = appState::dismissEditor,
            onSave = { title, composer, tags, collection, isFavorite ->
                appState.saveMetadata(
                    itemId = editingItem.id,
                    title = title,
                    composer = composer,
                    tags = tags,
                    collection = collection,
                    isFavorite = isFavorite,
                )
            },
        )
    }

    if (screenModel.deleteCandidate != null) {
        DeleteScoreDialog(
            item = screenModel.deleteCandidate,
            onDismiss = appState::dismissDeleteRequest,
            onConfirm = appState::confirmDelete,
        )
    }

    if (appState.setlistPickerVisible && screenModel.selectedItems.isNotEmpty()) {
        AddToSetlistDialog(
            selectedCount = screenModel.selectedItems.size,
            setlists = snapshot.setlists,
            onDismiss = appState::dismissSetlistPicker,
            onCreateSetlist = { name ->
                appState.createSetlist(
                    name = name,
                    itemIds = screenModel.selectedItems.map { it.id },
                )
            },
            onAddToSetlist = { setlistId ->
                appState.addSelectionToSetlist(
                    setlistId = setlistId,
                    itemIds = screenModel.selectedItems.map { it.id },
                )
            },
        )
    }

    if (appState.songbookPickerVisible && screenModel.selectedItems.isNotEmpty()) {
        AddToSongbookDialog(
            runtime = runtime,
            selectedCount = screenModel.selectedItems.size,
            songbooks = snapshot.songbooks,
            coverImageSupported = appState.canPickSongbookCover,
            onDismiss = appState::dismissSongbookPicker,
            onCreateSongbook = { name, coverImage ->
                appState.createSongbook(
                    name = name,
                    items = screenModel.selectedItems,
                    coverImage = coverImage,
                )
            },
            onAddToSongbook = { songbookId ->
                appState.addSelectionToSongbook(
                    songbookId = songbookId,
                    items = screenModel.selectedItems,
                )
            },
        )
    }

    if (appState.randomSetlistBuilderVisible) {
        RandomSetlistDialog(
            availableCount = screenModel.currentScopeItems.size,
            scopeLabel = screenModel.randomScopeLabel,
            onDismiss = appState::dismissRandomSetlistBuilder,
            onCreate = { count ->
                appState.createTemporaryRandomSetlist(
                    items = screenModel.currentScopeItems,
                    requestedCount = count,
                )
            },
        )
    }

    if (appState.saveTemporarySetlistDialogVisible && screenModel.temporarySetlist != null) {
        SaveTemporarySetlistDialog(
            session = screenModel.temporarySetlist,
            onDismiss = appState::dismissSaveTemporarySetlistDialog,
            onSave = appState::saveTemporarySetlist,
        )
    }

    if (screenModel.deleteSetlistCandidate != null) {
        DeleteSetlistDialog(
            setlist = screenModel.deleteSetlistCandidate,
            onDismiss = appState::dismissDeleteSetlistRequest,
            onConfirm = appState::confirmDeleteSetlist,
        )
    }

    if (screenModel.deleteSongbookCandidate != null) {
        DeleteSongbookDialog(
            songbook = screenModel.deleteSongbookCandidate,
            onDismiss = appState::dismissDeleteSongbookRequest,
            onConfirm = appState::confirmDeleteSongbook,
        )
    }

    if (screenModel.infoItem != null) {
        ScoreInfoDialog(
            item = screenModel.infoItem,
            metadata = screenModel.infoMetadata,
            loading = appState.infoMetadataLoading,
            errorMessage = appState.infoMetadataError,
            onDismiss = appState::dismissScoreInfo,
        )
    }
}

@Composable
private fun HeaderPanel(
    visibleCount: Int,
    headerTotalCount: Int,
    visibleScoreCount: Int,
    hiddenScoreCount: Int,
) {
    val themePalette = lunarThemePalette()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        themePalette.headerGradientStart,
                        themePalette.headerGradientEnd,
                    )
                ),
                shape = MaterialTheme.shapes.extraLarge,
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "🎵  Lunar",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = themePalette.headerForeground,
                modifier = Modifier.weight(1f),
            )
            SummaryPill("${visibleScoreCount.scoreLabel()}")
            if (hiddenScoreCount > 0) {
                SummaryPill("$hiddenScoreCount hidden")
            }
            if (visibleCount != headerTotalCount) {
                SummaryPill("$visibleCount shown")
            }
        }
    }
}

@Composable
private fun SummaryPill(text: String) {
    val themePalette = lunarThemePalette()

    Surface(
        color = themePalette.headerForeground.copy(alpha = 0.18f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = themePalette.headerForeground,
        )
    }
}

// ─── Browse mode switcher ────────────────────────────────────────────────────

@Composable
private fun BrowseModeSwitcher(appState: LunarAppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrowseModeTab("All", appState.browseMode == LibraryBrowseMode.ALL) {
            appState.updateBrowseMode(LibraryBrowseMode.ALL)
        }
        BrowseModeTab("Collections", appState.browseMode == LibraryBrowseMode.BY_COLLECTION) {
            appState.updateBrowseMode(LibraryBrowseMode.BY_COLLECTION)
        }
        BrowseModeTab("Composers", appState.browseMode == LibraryBrowseMode.BY_COMPOSER) {
            appState.updateBrowseMode(LibraryBrowseMode.BY_COMPOSER)
        }
        BrowseModeTab("Setlists", appState.browseMode == LibraryBrowseMode.SETLISTS) {
            appState.updateBrowseMode(LibraryBrowseMode.SETLISTS)
        }
        BrowseModeTab("Songbooks", appState.browseMode == LibraryBrowseMode.SONGBOOKS) {
            appState.updateBrowseMode(LibraryBrowseMode.SONGBOOKS)
        }
        BrowseModeTab("Hidden", appState.browseMode == LibraryBrowseMode.HIDDEN) {
            appState.updateBrowseMode(LibraryBrowseMode.HIDDEN)
        }
    }
}

@Composable
private fun BrowseModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    }
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = bg,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
        )
    }
}

// ─── Flat score list (All view) ──────────────────────────────────────────────

private enum class ScoreListMode {
    NORMAL,
    HIDDEN,
}

@Composable
private fun FlatScoreList(
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    mode: ScoreListMode = ScoreListMode.NORMAL,
) {
    when (appState.layoutMode) {
        LibraryLayoutMode.GRID -> LibraryGrid(items = items, appState = appState, mode = mode)
        LibraryLayoutMode.LIST -> LibraryList(items = items, appState = appState, mode = mode)
    }
}

// ─── Group list (Collection / Composer root view) ────────────────────────────

@Composable
private fun GroupList(
    groups: List<String>,
    itemsByGroup: Map<String, List<SheetMusicItem>>,
    emptyLabel: String,
    onSelectGroup: (String) -> Unit,
) {
    if (groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                count = groups.size,
                key = { index -> groups[index] },
            ) { index ->
                val name = groups[index]
                val count = itemsByGroup[name]?.size ?: 0
                GroupCard(name = name, scoreCount = count, onClick = { onSelectGroup(name) })
            }
        }
    }
}

@Composable
private fun GroupCard(name: String, scoreCount: Int, onClick: () -> Unit) {
    val themePalette = lunarThemePalette()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .heightIn(min = 64.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                themePalette.accentGradientStart,
                                themePalette.accentGradientEnd,
                            )
                        )
                    ),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "📁  $name",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "$scoreCount score${if (scoreCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "▸",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

// ─── Drill-down score list (inside a group) ──────────────────────────────────

@Composable
private fun DrillDownScoreList(
    groupName: String,
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Back bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.clickable(onClick = onBack),
            ) {
                Text(
                    text = "◂  Back",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = groupName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            SummaryPillDark("${items.size} scores")
        }
        // Score list
        Box(modifier = Modifier.weight(1f)) {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No scores match the current filters in this group.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FlatScoreList(items = items, appState = appState)
            }
        }
    }
}

@Composable
private fun SummaryPillDark(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun RandomLibraryActionsRow(
    availableCount: Int,
    onOpenRandomSheet: () -> Unit,
    onCreateRandomSetlist: () -> Unit,
) {
    val hasScores = availableCount > 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            enabled = hasScores,
            onClick = onOpenRandomSheet,
        ) {
            Text("Open random sheet")
        }
        Button(
            modifier = Modifier.weight(1f),
            enabled = hasScores,
            onClick = onCreateRandomSetlist,
        ) {
            Text("Random setlist")
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onAddToSetlist: () -> Unit,
    onAddToSongbook: () -> Unit,
    canCreateSongbooks: Boolean,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            val stackActions = maxWidth < 620.dp

            if (stackActions) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${selectedCount.scoreLabel()} selected",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = if (canCreateSongbooks) {
                                "Add the current selection to a setlist or turn it into a combined songbook."
                            } else {
                                "Add the current selection to a saved setlist or clear it."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onClear) {
                            Text("Clear", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Button(onClick = onAddToSetlist) {
                            Text("Add to setlist")
                        }
                        if (canCreateSongbooks) {
                            OutlinedButton(onClick = onAddToSongbook) {
                                Text("Add to songbook")
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "${selectedCount.scoreLabel()} selected",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = if (canCreateSongbooks) {
                                "Add the current selection to a saved setlist or build a combined songbook."
                            } else {
                                "Add the current selection to a saved setlist or clear it."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onClear) {
                            Text("Clear", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        if (canCreateSongbooks) {
                            OutlinedButton(onClick = onAddToSongbook) {
                                Text("Add to songbook")
                            }
                        }
                        Button(onClick = onAddToSetlist) {
                            Text("Add to setlist")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetlistsOverview(
    temporarySetlist: TemporarySetlistSession?,
    setlists: List<LibrarySetlist>,
    itemsById: Map<String, SheetMusicItem>,
    onOpenTemporarySetlist: () -> Unit,
    onSaveTemporarySetlist: () -> Unit,
    onDiscardTemporarySetlist: () -> Unit,
    onOpenSetlist: (String) -> Unit,
    onDeleteSetlist: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val orderedSetlists = setlists.sortedByDescending { it.updatedAtEpochMillis }

    if (temporarySetlist == null && orderedSetlists.isEmpty()) {
        EmptySetlistsState(onOpenLibrary = onOpenLibrary)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (temporarySetlist != null) {
            item(key = "temporary_setlist") {
                TemporarySetlistCard(
                    session = temporarySetlist,
                    itemsById = itemsById,
                    onOpen = onOpenTemporarySetlist,
                    onSave = onSaveTemporarySetlist,
                    onDiscard = onDiscardTemporarySetlist,
                )
            }
        }
        items(
            count = orderedSetlists.size,
            key = { index -> orderedSetlists[index].id },
        ) { index ->
            SetlistCard(
                setlist = orderedSetlists[index],
                itemsById = itemsById,
                onOpen = { onOpenSetlist(orderedSetlists[index].id) },
                onDelete = { onDeleteSetlist(orderedSetlists[index].id) },
            )
        }
    }
}

@Composable
private fun SongbooksOverview(
    songbooks: List<LibrarySongbook>,
    itemsById: Map<String, SheetMusicItem>,
    onOpenSongbook: (String) -> Unit,
    onDeleteSongbook: (String) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val orderedSongbooks = songbooks.sortedByDescending { it.updatedAtEpochMillis }

    if (orderedSongbooks.isEmpty()) {
        EmptySongbooksState(onOpenLibrary = onOpenLibrary)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = orderedSongbooks.size,
            key = { index -> orderedSongbooks[index].id },
        ) { index ->
            SongbookCard(
                songbook = orderedSongbooks[index],
                itemsById = itemsById,
                onOpen = { onOpenSongbook(orderedSongbooks[index].id) },
                onDelete = { onDeleteSongbook(orderedSongbooks[index].id) },
            )
        }
    }
}

@Composable
private fun EmptySetlistsState(onOpenLibrary: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.36f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Setlists are empty",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Select one or more scores from the library, use Add to setlist, or build a temporary random mix to explore something new.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .widthIn(max = 560.dp),
            )
            Button(
                onClick = onOpenLibrary,
                modifier = Modifier.padding(top = 18.dp),
            ) {
                Text("Browse scores")
            }
        }
    }
}

@Composable
private fun EmptySongbooksState(onOpenLibrary: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Songbooks are empty",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Select scores from the library, then use Add to songbook to merge them into one PDF. You can optionally add a front cover image while creating it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .widthIn(max = 560.dp),
            )
            Button(
                onClick = onOpenLibrary,
                modifier = Modifier.padding(top = 18.dp),
            ) {
                Text("Browse scores")
            }
        }
    }
}

@Composable
private fun TemporarySetlistCard(
    session: TemporarySetlistSession,
    itemsById: Map<String, SheetMusicItem>,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val themePalette = lunarThemePalette()
    SetlistCardFrame(
        accentBrush = Brush.verticalGradient(
            listOf(
                themePalette.headerGradientStart,
                themePalette.headerGradientEnd,
            )
        ),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "${session.itemIds.size.scoreLabel()}  |  Temporary session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = "Temporary",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Text(
            text = buildSetlistPreviewTitles(
                itemIds = session.itemIds,
                itemsById = itemsById,
                emptyText = "No scores are left in this temporary mix.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.84f),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Keep it temporary, or save it as a regular setlist if it clicks.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDiscard) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(onClick = onSave) {
                    Text("Save")
                }
                Button(onClick = onOpen) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun SetlistCard(
    setlist: LibrarySetlist,
    itemsById: Map<String, SheetMusicItem>,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val themePalette = lunarThemePalette()
    SetlistCardFrame(
        accentBrush = Brush.verticalGradient(
            listOf(
                themePalette.accentGradientStart,
                themePalette.accentGradientEnd,
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = setlist.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${setlist.itemIds.size.scoreLabel()}  |  Updated ${formatEpochMillis(setlist.updatedAtEpochMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SummaryPillDark("${setlist.itemIds.size.scoreLabel()}")
        }
        Text(
            text = buildSetlistPreviewTitles(
                itemIds = setlist.itemIds,
                itemsById = itemsById,
                emptyText = "No scores saved yet.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Open to browse the scores in order.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onOpen) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun SongbookCard(
    songbook: LibrarySongbook,
    itemsById: Map<String, SheetMusicItem>,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val themePalette = lunarThemePalette()
    SetlistCardFrame(
        accentBrush = Brush.verticalGradient(
            listOf(
                themePalette.headerGradientStart,
                themePalette.accentGradientEnd,
            )
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        onClick = onOpen,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = songbook.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildSongbookMetaLine(songbook),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SummaryPillDark(songbook.pageCount?.let { "$it pages" } ?: "PDF")
        }
        Text(
            text = buildSetlistPreviewTitles(
                itemIds = songbook.itemIds,
                itemsById = itemsById,
                emptyText = "This songbook no longer has linked scores, but the merged PDF is still available.",
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Open to read the merged PDF as one continuous songbook.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onOpen) {
                    Text("Open")
                }
            }
        }
    }
}

@Composable
private fun TemporarySetlistDetailView(
    session: TemporarySetlistSession,
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    SetlistDetailScaffold(
        title = session.title,
        subtitle = "Temporary random mix. Save it if you want to keep this run.",
        itemCount = items.size,
        emptyMessage = "This temporary setlist no longer has any available scores.",
        items = items,
        appState = appState,
        onBack = onBack,
    ) {
        OutlinedButton(onClick = onSave) {
            Text("Save")
        }
        TextButton(onClick = onDiscard) {
            Text("Discard", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SetlistDetailView(
    setlist: LibrarySetlist,
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    onBack: () -> Unit,
    onDeleteSetlist: () -> Unit,
) {
    SetlistDetailScaffold(
        title = setlist.name,
        subtitle = "Saved automatically. Delete it whenever you no longer need it.",
        itemCount = items.size,
        emptyMessage = "This setlist is empty right now.",
        items = items,
        appState = appState,
        onBack = onBack,
    ) {
        TextButton(onClick = onDeleteSetlist) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SearchAndSortBar(appState: LunarAppState) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stackControls = maxWidth < 680.dp

        if (stackControls) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibrarySearchField(
                    searchText = appState.query.searchText,
                    onValueChange = appState::updateSearchText,
                    modifier = Modifier.fillMaxWidth(),
                )
                SearchAndSortControlsRow(
                    appState = appState,
                    modifier = Modifier.fillMaxWidth(),
                    sortButtonModifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibrarySearchField(
                    searchText = appState.query.searchText,
                    onValueChange = appState::updateSearchText,
                    modifier = Modifier.weight(1f),
                )
                SearchAndSortControlsRow(
                    appState = appState,
                    sortButtonModifier = Modifier.widthIn(min = 144.dp, max = 164.dp),
                )
            }
        }
    }
}

@Composable
private fun LibrarySearchField(
    searchText: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = searchText,
        onValueChange = onValueChange,
        modifier = modifier.semantics {
            contentDescription = "Library search"
        },
        singleLine = true,
        label = { Text("Search title, composer, tags...") },
    )
}

@Composable
private fun SearchAndSortControlsRow(
    appState: LunarAppState,
    modifier: Modifier = Modifier,
    sortButtonModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortMenuButton(
            current = appState.query.sortOption,
            onSelect = appState::updateSortOption,
            modifier = sortButtonModifier,
        )
        DirectionToggle(
            sortDirection = appState.query.sortDirection,
            onToggle = {
                appState.updateSortDirection(
                    if (appState.query.sortDirection == SortDirection.ASCENDING) {
                        SortDirection.DESCENDING
                    } else {
                        SortDirection.ASCENDING
                    }
                )
            },
        )
        LayoutMenuButton(
            selected = appState.layoutMode,
            onSelect = appState::updateLayoutMode,
        )
        FilterChip(
            selected = appState.query.favoritesOnly,
            onClick = appState::toggleFavoriteFilter,
            label = { Text("★") },
        )
    }
}

@Composable
private fun SortMenuButton(
    current: LibrarySortOption,
    onSelect: (LibrarySortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Sort by ${current.label()}"
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(current.label())
                ChevronDownGlyph(color = MaterialTheme.colorScheme.onSurface)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LibrarySortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label()) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun RefineLibraryPanel(
    availableCollections: List<String>,
    availableTags: List<String>,
    visibleCount: Int,
    totalCount: Int,
    appState: LunarAppState,
) {
    val hasAvailableFilters = availableCollections.isNotEmpty() || availableTags.isNotEmpty()
    val hasActiveRefinements = appState.query.selectedCollection != null ||
        appState.query.selectedTags.isNotEmpty() ||
        appState.query.favoritesOnly

    if (!hasAvailableFilters && !hasActiveRefinements) {
        return
    }

    var isExpanded by rememberSaveable(hasActiveRefinements) {
        mutableStateOf(hasActiveRefinements)
    }
    val selectedTags = appState.query.selectedTags
    val activeRefinementCount = selectedTags.size +
        (if (appState.query.selectedCollection != null) 1 else 0) +
        (if (appState.query.favoritesOnly) 1 else 0)
    val orderedCollections = availableCollections.sortedWith(String.CASE_INSENSITIVE_ORDER)
    val orderedTags = availableTags.sortedWith(
        compareBy<String> { if (it in selectedTags) 0 else 1 }
            .then(String.CASE_INSENSITIVE_ORDER)
    )
    val summaryText: String? = when {
        hasActiveRefinements && visibleCount == totalCount ->
            "Browsing ${totalCount.scoreLabel()} with $activeRefinementCount active filter${if (activeRefinementCount == 1) "" else "s"}."
        hasActiveRefinements ->
            "Showing ${visibleCount.scoreLabel()} out of ${totalCount.scoreLabel()} with $activeRefinementCount active filter${if (activeRefinementCount == 1) "" else "s"}."
        visibleCount != totalCount ->
            "Showing ${visibleCount.scoreLabel()} out of ${totalCount.scoreLabel()}."
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Refine library",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
                TextButton(onClick = { isExpanded = !isExpanded }) {
                    Text(if (isExpanded) "Hide filters" else "Show filters")
                }
            }
            if (summaryText != null) {
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasActiveRefinements) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = appState::clearRefinements) {
                        Text("Clear filters")
                    }
                }
            }

            if (isExpanded && orderedCollections.isNotEmpty()) {
                FilterChipSection(
                    title = "Collection",
                    supportingText = "Choose one collection at a time.",
                ) {
                    FilterChip(
                        selected = appState.query.selectedCollection == null,
                        onClick = { appState.selectCollection(null) },
                        label = { Text("All collections") },
                    )
                    orderedCollections.forEach { collection ->
                        FilterChip(
                            selected = appState.query.selectedCollection.equals(collection, ignoreCase = true),
                            onClick = { appState.selectCollection(collection) },
                            label = { Text(collection) },
                        )
                    }
                }
            }

            if (isExpanded && orderedTags.isNotEmpty()) {
                FilterChipSection(
                    title = "Tags",
                    supportingText = if (selectedTags.size > 1) {
                        "Selected tags use AND matching."
                    } else {
                        "Choose one or more tags to narrow the list."
                    },
                ) {
                    orderedTags.forEach { tag ->
                        FilterChip(
                            selected = tag in selectedTags,
                            onClick = { appState.toggleTag(tag) },
                            label = { Text(tag) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipSection(
    title: String,
    supportingText: String,
    content: @Composable RowScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun DirectionToggle(
    sortDirection: SortDirection,
    onToggle: () -> Unit,
) {
    CompactControlButton(
        onClick = onToggle,
        contentDescription = if (sortDirection == SortDirection.ASCENDING) {
            "Sort ascending"
        } else {
            "Sort descending"
        },
    ) { contentColor ->
        SortDirectionGlyph(
            sortDirection = sortDirection,
            color = contentColor,
        )
    }
}

@Composable
private fun LayoutMenuButton(
    selected: LibraryLayoutMode,
    onSelect: (LibraryLayoutMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        CompactControlButton(
            onClick = { expanded = true },
            contentDescription = "Layout options (${selected.label()})",
        ) { contentColor ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LayoutGlyph(
                    mode = selected,
                    color = contentColor,
                )
                ChevronDownGlyph(color = contentColor)
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            LibraryLayoutMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label()) },
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    },
                )
            }
        }
    }
}

@Composable
private fun CompactControlButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable (Color) -> Unit,
) {
    val shape = MaterialTheme.shapes.medium

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
        modifier = modifier
            .size(44.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = shape,
            )
            .semantics {
                this.contentDescription = contentDescription
            }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content(MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LibraryActionIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    content: @Composable (Color) -> Unit,
) {
    val shape = MaterialTheme.shapes.small

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        shape = shape,
        modifier = modifier
            .size(36.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f),
                shape = shape,
            )
            .semantics {
                this.contentDescription = contentDescription
            }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content(MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LibraryItemQuickActions(
    item: SheetMusicItem,
    appState: LunarAppState,
    includeDownload: Boolean,
    mode: ScoreListMode,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (includeDownload) {
            LibraryActionIconButton(
                onClick = { appState.downloadScore(item) },
                contentDescription = "Download ${item.title}",
            ) { contentColor ->
                DownloadGlyph(color = contentColor)
            }
        }
        LibraryActionIconButton(
            onClick = { appState.toggleFavorite(item.id) },
            contentDescription = if (item.isFavorite) {
                "Remove ${item.title} from favorites"
            } else {
                "Mark ${item.title} as favorite"
            },
        ) {
            Text(
                text = if (item.isFavorite) "\u2605" else "\u2606",
                color = if (item.isFavorite) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LibraryActionIconButton(
            onClick = { appState.showScoreInfo(item.id) },
            contentDescription = "Show info for ${item.title}",
        ) {
            Text(
                text = "i",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (mode == ScoreListMode.HIDDEN) {
            LibraryActionIconButton(
                onClick = { appState.restoreScore(item.id) },
                contentDescription = "Restore ${item.title}",
            ) {
                Text(
                    text = "+",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        } else {
            LibraryActionIconButton(
                onClick = { appState.hideScore(item.id) },
                contentDescription = "Hide ${item.title}",
            ) {
                Text(
                    text = "\u2298",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        LibraryActionIconButton(
            onClick = { appState.startEditing(item.id) },
            contentDescription = "Edit ${item.title}",
        ) {
            Text(
                text = "\u270E",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LibraryActionIconButton(
            onClick = { appState.requestDelete(item.id) },
            contentDescription = "Delete ${item.title}",
        ) {
            Text(
                text = "\uD83D\uDDD1",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun SortDirectionGlyph(
    sortDirection: SortDirection,
    color: Color,
) {
    val barWidths = if (sortDirection == SortDirection.ASCENDING) {
        listOf(6.dp, 9.dp, 12.dp)
    } else {
        listOf(12.dp, 9.dp, 6.dp)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            barWidths.forEach { width ->
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(2.dp)
                        .background(
                            color = color,
                            shape = MaterialTheme.shapes.small,
                        ),
                )
            }
        }
        DirectionArrowGlyph(
            sortDirection = sortDirection,
            color = color,
        )
    }
}

@Composable
private fun DirectionArrowGlyph(
    sortDirection: SortDirection,
    color: Color,
) {
    Canvas(
        modifier = Modifier
            .width(8.dp)
            .height(12.dp),
    ) {
        val strokeWidth = size.minDimension * 0.28f
        val centerX = size.width / 2f

        if (sortDirection == SortDirection.ASCENDING) {
            drawLine(
                color = color,
                start = Offset(centerX, size.height * 0.85f),
                end = Offset(centerX, size.height * 0.2f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(centerX, size.height * 0.2f),
                end = Offset(size.width * 0.18f, size.height * 0.42f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(centerX, size.height * 0.2f),
                end = Offset(size.width * 0.82f, size.height * 0.42f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        } else {
            drawLine(
                color = color,
                start = Offset(centerX, size.height * 0.15f),
                end = Offset(centerX, size.height * 0.8f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(centerX, size.height * 0.8f),
                end = Offset(size.width * 0.18f, size.height * 0.58f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(centerX, size.height * 0.8f),
                end = Offset(size.width * 0.82f, size.height * 0.58f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun LayoutGlyph(
    mode: LibraryLayoutMode,
    color: Color,
) {
    when (mode) {
        LibraryLayoutMode.GRID -> GridLayoutGlyph(color = color)
        LibraryLayoutMode.LIST -> ListLayoutGlyph(color = color)
    }
}

@Composable
private fun GridLayoutGlyph(color: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                color = color,
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListLayoutGlyph(color: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .width(if (index == 1) 12.dp else 14.dp)
                    .height(2.dp)
                    .background(
                        color = color,
                        shape = MaterialTheme.shapes.small,
                    ),
            )
        }
    }
}

@Composable
private fun ChevronDownGlyph(color: Color) {
    Canvas(
        modifier = Modifier
            .width(8.dp)
            .height(8.dp),
    ) {
        val strokeWidth = size.minDimension * 0.24f
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height * 0.32f),
            end = Offset(size.width / 2f, size.height * 0.68f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width / 2f, size.height * 0.68f),
            end = Offset(size.width * 0.82f, size.height * 0.32f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun DownloadGlyph(color: Color) {
    Canvas(
        modifier = Modifier.size(16.dp),
    ) {
        val strokeWidth = size.minDimension * 0.16f
        val centerX = size.width / 2f
        val arrowTop = size.height * 0.14f
        val arrowBottom = size.height * 0.68f
        val trayTop = size.height * 0.78f

        drawLine(
            color = color,
            start = Offset(centerX, arrowTop),
            end = Offset(centerX, arrowBottom),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centerX, arrowBottom),
            end = Offset(size.width * 0.28f, size.height * 0.46f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(centerX, arrowBottom),
            end = Offset(size.width * 0.72f, size.height * 0.46f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.22f, trayTop),
            end = Offset(size.width * 0.78f, trayTop),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.22f, trayTop),
            end = Offset(size.width * 0.22f, size.height * 0.6f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.78f, trayTop),
            end = Offset(size.width * 0.78f, size.height * 0.6f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun LibraryGrid(
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    mode: ScoreListMode,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 260.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            count = items.size,
            key = { index -> items[index].id },
        ) { index ->
            LibraryCard(
                item = items[index],
                appState = appState,
                mode = mode,
            )
        }
    }
}

@Composable
private fun LibraryList(
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    mode: ScoreListMode,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            count = items.size,
            key = { index -> items[index].id },
        ) { index ->
            LibraryCard(
                item = items[index],
                appState = appState,
                mode = mode,
            )
        }
    }
}

@Composable
private fun LibraryCard(
    item: SheetMusicItem,
    appState: LunarAppState,
    mode: ScoreListMode,
) {
    val themePalette = lunarThemePalette()
    val isSelected = item.id in appState.selectedScoreIds
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { appState.openPreview(item) },
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 2.dp,
        ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                themePalette.accentGradientStart,
                                themePalette.accentGradientEnd,
                            )
                        )
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = item.composer ?: "Composer not set",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { appState.toggleScoreSelection(item.id) },
                            modifier = Modifier.semantics {
                                contentDescription = "Select ${item.title}"
                            },
                        )
                        FavoriteMarker(isFavorite = item.isFavorite)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val collection = item.collection
                    if (collection != null) {
                        CollectionPill(collection)
                    }
                    PagesPill("${item.pageCount ?: "?"} pages")
                }
                if (item.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.tags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                    }
                }
                if (appState.layoutMode == LibraryLayoutMode.GRID) {
                    GridLibraryCardFooter(
                        item = item,
                        appState = appState,
                        mode = mode,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = buildDetailsLine(item),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.weight(1f),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LibraryItemQuickActions(
                                item = item,
                                appState = appState,
                                includeDownload = appState.canDownloadScores,
                                mode = mode,
                            )
                            androidx.compose.material3.Button(onClick = { appState.openPreview(item) }) {
                                Text("Open")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridLibraryCardFooter(
    item: SheetMusicItem,
    appState: LunarAppState,
    mode: ScoreListMode,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = buildDetailsLine(item),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryItemQuickActions(
                item = item,
                appState = appState,
                includeDownload = false,
                mode = mode,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            )
            androidx.compose.material3.Button(onClick = { appState.openPreview(item) }) {
                Text("Open")
            }
        }
    }
}

@Composable
private fun DeleteScoreDialog(
    item: SheetMusicItem,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete score?") },
        text = {
            Text(
                "Remove \"${item.title}\" from the library? Lunar will also delete its imported PDF copy for this score.",
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
    )
}

@Composable
private fun RandomSetlistDialog(
    availableCount: Int,
    scopeLabel: String,
    onDismiss: () -> Unit,
    onCreate: (Int) -> Unit,
) {
    var requestedCountText by remember(availableCount, scopeLabel) {
        mutableStateOf(minOf(5, availableCount).coerceAtLeast(1).toString())
    }
    val requestedCount = requestedCountText.toIntOrNull()
    val canConfirm = availableCount > 0 && requestedCount != null && requestedCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a random setlist") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (availableCount > 0) {
                        "Build a temporary random setlist from $scopeLabel. Lunar will clamp the size to the ${availableCount.scoreLabel()} currently available."
                    } else {
                        "There are no scores available in $scopeLabel right now."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = requestedCountText,
                    onValueChange = { value ->
                        requestedCountText = value.filter(Char::isDigit)
                    },
                    label = { Text("How many scores?") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = availableCount > 0,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                enabled = canConfirm,
                onClick = {
                    requestedCount?.let(onCreate)
                },
            ) {
                Text("Shuffle")
            }
        },
    )
}

@Composable
private fun SaveTemporarySetlistDialog(
    session: TemporarySetlistSession,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var setlistName by remember(session.createdAtEpochMillis) {
        mutableStateOf(session.title)
    }
    val trimmedName = setlistName.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save temporary setlist") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Save this ${session.itemIds.size.scoreLabel()} as a regular setlist so it stays in your library.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = setlistName,
                    onValueChange = { setlistName = it },
                    label = { Text("Setlist name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                enabled = trimmedName.isNotEmpty(),
                onClick = {
                    onSave(trimmedName)
                },
            ) {
                Text("Save")
            }
        },
    )
}

@Composable
private fun AddToSetlistDialog(
    selectedCount: Int,
    setlists: List<LibrarySetlist>,
    onDismiss: () -> Unit,
    onCreateSetlist: (String) -> Unit,
    onAddToSetlist: (String) -> Unit,
) {
    val orderedSetlists = setlists.sortedByDescending { it.updatedAtEpochMillis }
    var newSetlistName by remember(selectedCount, setlists) { mutableStateOf("") }
    var selectedSetlistId by remember(selectedCount, setlists) { mutableStateOf<String?>(null) }
    val trimmedName = newSetlistName.trim()
    val canConfirm = trimmedName.isNotEmpty() || selectedSetlistId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${selectedCount.scoreLabel()} to a setlist") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (orderedSetlists.isEmpty()) {
                        "Name this setlist. Lunar saves setlists automatically."
                    } else {
                        "Create a new setlist or choose an existing one to append these scores."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = newSetlistName,
                    onValueChange = {
                        newSetlistName = it
                        if (it.isNotBlank()) {
                            selectedSetlistId = null
                        }
                    },
                    label = { Text("New setlist name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (orderedSetlists.isNotEmpty()) {
                    FilterChipSection(
                        title = "Saved setlists",
                        supportingText = "Picking one appends only scores that are not already in that setlist.",
                    ) {
                        orderedSetlists.forEach { setlist ->
                            FilterChip(
                                selected = selectedSetlistId == setlist.id,
                                onClick = {
                                    selectedSetlistId = if (selectedSetlistId == setlist.id) {
                                        null
                                    } else {
                                        newSetlistName = ""
                                        setlist.id
                                    }
                                },
                                label = { Text(setlist.name) },
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                enabled = canConfirm,
                onClick = {
                    selectedSetlistId?.let(onAddToSetlist) ?: onCreateSetlist(trimmedName)
                },
            ) {
                Text(if (selectedSetlistId != null) "Add" else "Save")
            }
        },
    )
}

@Composable
private fun AddToSongbookDialog(
    runtime: PlatformRuntime,
    selectedCount: Int,
    songbooks: List<LibrarySongbook>,
    coverImageSupported: Boolean,
    onDismiss: () -> Unit,
    onCreateSongbook: (String, SelectedCoverImage?) -> Unit,
    onAddToSongbook: (String) -> Unit,
) {
    val orderedSongbooks = songbooks.sortedByDescending { it.updatedAtEpochMillis }
    val scope = rememberCoroutineScope()
    var newSongbookName by remember(selectedCount, songbooks) { mutableStateOf("") }
    var selectedSongbookId by remember(selectedCount, songbooks) { mutableStateOf<String?>(null) }
    var selectedCoverImage by remember(selectedCount, songbooks) { mutableStateOf<SelectedCoverImage?>(null) }
    var coverPickerError by remember(selectedCount, songbooks) { mutableStateOf<String?>(null) }
    var coverPickerBusy by remember(selectedCount, songbooks) { mutableStateOf(false) }
    val trimmedName = newSongbookName.trim()
    val canConfirm = trimmedName.isNotEmpty() || selectedSongbookId != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${selectedCount.scoreLabel()} to a songbook") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (orderedSongbooks.isEmpty()) {
                        "Name this songbook and Lunar will merge the selected PDFs into one combined document."
                    } else {
                        "Create a new combined PDF or append these scores to an existing songbook."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = newSongbookName,
                    onValueChange = {
                        newSongbookName = it
                        if (it.isNotBlank()) {
                            selectedSongbookId = null
                        }
                    },
                    label = { Text("New songbook name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (coverImageSupported) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Front cover",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = if (selectedSongbookId == null) {
                                "Optional. Add an image cover when creating a new songbook."
                            } else {
                                "Existing songbooks keep their current cover when you append more scores."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                enabled = selectedSongbookId == null && !coverPickerBusy,
                                onClick = {
                                    scope.launch {
                                        coverPickerBusy = true
                                        coverPickerError = null
                                        try {
                                            selectedCoverImage = runtime.coverImagePicker.pickCoverImage()
                                        } catch (error: Throwable) {
                                            coverPickerError = error.message ?: "Could not load that cover image."
                                        } finally {
                                            coverPickerBusy = false
                                        }
                                    }
                                },
                            ) {
                                Text(
                                    when {
                                        coverPickerBusy -> "Loading..."
                                        selectedCoverImage != null -> "Change cover"
                                        else -> "Choose cover"
                                    }
                                )
                            }
                            selectedCoverImage?.let { cover ->
                                Text(
                                    text = cover.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    enabled = selectedSongbookId == null,
                                    onClick = { selectedCoverImage = null },
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                        coverPickerError?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                if (orderedSongbooks.isNotEmpty()) {
                    FilterChipSection(
                        title = "Saved songbooks",
                        supportingText = "Picking one appends these scores to the existing merged PDF.",
                    ) {
                        orderedSongbooks.forEach { songbook ->
                            FilterChip(
                                selected = selectedSongbookId == songbook.id,
                                onClick = {
                                    selectedSongbookId = if (selectedSongbookId == songbook.id) {
                                        null
                                    } else {
                                        newSongbookName = ""
                                        songbook.id
                                    }
                                },
                                label = { Text(songbook.name) },
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                enabled = canConfirm,
                onClick = {
                    selectedSongbookId?.let(onAddToSongbook)
                        ?: onCreateSongbook(trimmedName, selectedCoverImage)
                },
            ) {
                Text(if (selectedSongbookId != null) "Add" else "Create")
            }
        },
    )
}

@Composable
private fun DeleteSetlistDialog(
    setlist: LibrarySetlist,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete setlist?") },
        text = {
            Text(
                "Delete \"${setlist.name}\"? The saved score list will be removed, but the PDFs will stay in your library.",
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
    )
}

@Composable
private fun DeleteSongbookDialog(
    songbook: LibrarySongbook,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete songbook?") },
        text = {
            Text(
                "Delete \"${songbook.name}\"? Lunar will remove the merged songbook PDF, but the original scores will stay in your library.",
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
    )
}

@Composable
private fun FavoriteMarker(isFavorite: Boolean) {
    val bgColor = if (isFavorite) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f)
    } else {
        Color.Transparent
    }
    val textColor = if (isFavorite) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    Surface(
        color = bgColor,
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = if (isFavorite) "★" else "☆",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
        )
    }
}

@Composable
private fun CollectionPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = "📁 $text",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun PagesPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = "📄 $text",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun EmptyLibraryState(
    totalItemCount: Int,
    onOpenImport: () -> Unit,
    onClearFilters: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
                            Color.Transparent,
                        )
                    )
                )
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "🎵",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = if (totalItemCount == 0) {
                    "Your score library is ready"
                } else {
                    "No scores match the current view"
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = if (totalItemCount == 0) {
                    "Import a few PDFs to start building a searchable, sync-ready sheet music catalog."
                } else {
                    "Your library has scores, but the current search or filters are hiding them. Clear the filters to show everything again."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .widthIn(max = 520.dp),
            )
            if (totalItemCount == 0) {
                Button(
                    onClick = onOpenImport,
                    modifier = Modifier.padding(top = 18.dp),
                ) {
                    Text("Open import panel")
                }
            } else {
                Button(
                    onClick = onClearFilters,
                    modifier = Modifier.padding(top = 18.dp),
                ) {
                    Text("Clear filters")
                }
            }
        }
    }
}

@Composable
private fun HiddenScoresEmptyState(
    hiddenCount: Int,
    onClearFilters: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (hiddenCount == 0) {
                    "No hidden scores"
                } else {
                    "No hidden scores match the current view"
                },
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (hiddenCount == 0) {
                    "Scores you hide from the viewer or library will appear here for review and restoration."
                } else {
                    "Clear the current search or filters to review every hidden score."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .widthIn(max = 520.dp),
            )
            if (hiddenCount > 0) {
                Button(
                    onClick = onClearFilters,
                    modifier = Modifier.padding(top = 18.dp),
                ) {
                    Text("Clear filters")
                }
            }
        }
    }
}

@Composable
private fun MetadataEditorDialog(
    item: SheetMusicItem,
    onDismiss: () -> Unit,
    onSave: (title: String, composer: String, tags: String, collection: String, isFavorite: Boolean) -> Unit,
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var composer by remember(item.id) { mutableStateOf(item.composer.orEmpty()) }
    var tags by remember(item.id) { mutableStateOf(item.tags.joinToString(", ")) }
    var collection by remember(item.id) { mutableStateOf(item.collection.orEmpty()) }
    var isFavorite by remember(item.id) { mutableStateOf(item.isFavorite) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit score details") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = composer,
                    onValueChange = { composer = it },
                    label = { Text("Composer") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = collection,
                    onValueChange = { collection = it },
                    label = { Text("Collection") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = isFavorite,
                    onClick = { isFavorite = !isFavorite },
                    label = { Text(if (isFavorite) "Favorite" else "Mark as favorite") },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(title, composer, tags, collection, isFavorite)
                },
            ) {
                Text("Save")
            }
        },
    )
}

@Composable
private fun ScoreInfoDialog(
    item: SheetMusicItem,
    metadata: ScoreMetadata?,
    loading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Score info") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when {
                    loading -> {
                        Text(
                            text = "Loading score info...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    !errorMessage.isNullOrBlank() -> {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    metadata == null -> {
                        Text(
                            text = "No metadata is available for this score yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        val hasWorkSection = metadata.alternativeTitles.isNotEmpty() ||
                            metadata.catalogueNumber.isNotBlank() ||
                            metadata.opusNumber.isNotBlank() ||
                            metadata.workNumber.isNotBlank() ||
                            metadata.yearComposed != null ||
                            metadata.movements.isNotEmpty()
                        val hasMusicalDetailsSection = metadata.genre.isNotBlank() ||
                            metadata.form.isNotBlank() ||
                            metadata.stylePeriod.isNotBlank() ||
                            metadata.instrumentation.isNotEmpty() ||
                            metadata.key.isNotBlank() ||
                            metadata.timeSignature.isNotBlank() ||
                            metadata.tempoMarkings.isNotEmpty() ||
                            metadata.difficulty.isNotBlank() ||
                            metadata.pageCount != null ||
                            metadata.durationSeconds != null
                        val hasPublicationSection = metadata.publisher.isNotBlank() ||
                            metadata.editor.isNotBlank() ||
                            metadata.arranger.isNotBlank() ||
                            metadata.edition.isNotBlank() ||
                            metadata.language.isNotBlank()

                        Text(
                            text = metadata.title.ifBlank { item.title },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        scoreMetadataComposerLine(metadata)?.let { composerLine ->
                            Text(
                                text = composerLine,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        scoreInfoValue(metadata.subtitle)?.let { ScoreInfoField("Subtitle", it) }

                        if (hasWorkSection) {
                            ScoreInfoSection("Work")
                            scoreInfoValue(metadata.alternativeTitles.joinToString(", "))?.let {
                                ScoreInfoField("Alternative titles", it)
                            }
                            scoreInfoValue(metadata.catalogueNumber)?.let { ScoreInfoField("Catalogue number", it) }
                            scoreInfoValue(metadata.opusNumber)?.let { ScoreInfoField("Opus number", it) }
                            scoreInfoValue(metadata.workNumber)?.let { ScoreInfoField("Work number", it) }
                            metadata.yearComposed?.let { ScoreInfoField("Year composed", it.toString()) }
                            scoreInfoValue(metadata.movements.joinToString(", "))?.let { ScoreInfoField("Movements", it) }
                        }

                        if (hasMusicalDetailsSection) {
                            ScoreInfoSection("Musical details")
                            scoreInfoValue(metadata.genre)?.let { ScoreInfoField("Genre", it) }
                            scoreInfoValue(metadata.form)?.let { ScoreInfoField("Form", it) }
                            scoreInfoValue(metadata.stylePeriod)?.let { ScoreInfoField("Style period", it) }
                            scoreInfoValue(metadata.instrumentation.joinToString(", "))?.let {
                                ScoreInfoField("Instrumentation", it)
                            }
                            scoreInfoValue(metadata.key)?.let { ScoreInfoField("Key", it) }
                            scoreInfoValue(metadata.timeSignature)?.let { ScoreInfoField("Time signature", it) }
                            scoreInfoValue(metadata.tempoMarkings.joinToString(", "))?.let {
                                ScoreInfoField("Tempo markings", it)
                            }
                            scoreInfoValue(metadata.difficulty)?.let { ScoreInfoField("Difficulty", it) }
                            metadata.pageCount?.let { ScoreInfoField("Pages", it.toString()) }
                            metadata.durationSeconds?.let { ScoreInfoField("Duration", "${it}s") }
                        }

                        if (hasPublicationSection) {
                            ScoreInfoSection("Publication")
                            scoreInfoValue(metadata.publisher)?.let { ScoreInfoField("Publisher", it) }
                            scoreInfoValue(metadata.editor)?.let { ScoreInfoField("Editor", it) }
                            scoreInfoValue(metadata.arranger)?.let { ScoreInfoField("Arranger", it) }
                            scoreInfoValue(metadata.edition)?.let { ScoreInfoField("Edition", it) }
                            scoreInfoValue(metadata.language)?.let { ScoreInfoField("Language", it) }
                        }

                        ScoreInfoSection("Library")
                        scoreInfoValue(item.collection).let { value ->
                            if (value != null) {
                                ScoreInfoField("Collection", value)
                            }
                        }
                        if (item.isFavorite) {
                            ScoreInfoField("Favorite", "Yes")
                        }
                        if (item.isHidden) {
                            ScoreInfoField("Hidden", "Yes")
                        }
                        ScoreInfoField("Added", formatEpochMillis(item.dateAddedEpochMillis))
                        item.lastOpenedEpochMillis?.let { ScoreInfoField("Last opened", formatEpochMillis(it)) }
                        if (item.lastViewedPage > 0) {
                            ScoreInfoField("Resume page", (item.lastViewedPage + 1).toString())
                        }

                        ScoreInfoSection("Source")
                        scoreInfoValue(metadata.source.filename.ifBlank { item.document.originalFileName })?.let {
                            ScoreInfoField("Filename", it)
                        }
                        scoreInfoValue(metadata.source.fileType)?.let { ScoreInfoField("File type", it) }
                        scoreInfoValue(metadata.source.url.ifBlank { item.document.sourceUri.orEmpty() })?.let {
                            ScoreInfoField("URL", it)
                        }
                        ScoreInfoField("Schema", "${metadata.schemaId} ${metadata.schemaVersion}")

                        scoreInfoValue(metadata.tags.joinToString(", "))?.let {
                            ScoreInfoSection("Tags")
                            ScoreInfoField("Keywords", it)
                        }
                        scoreInfoValue(metadata.notes)?.let {
                            ScoreInfoSection("Notes")
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text(
                                    text = it,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@Composable
private fun ScoreInfoSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ScoreInfoField(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun scoreMetadataComposerLine(metadata: ScoreMetadata): String? {
    val composerName = scoreInfoValue(metadata.composer.name) ?: return null
    val lifespan = when {
        metadata.composer.birthYear != null && metadata.composer.deathYear != null ->
            "${metadata.composer.birthYear}-${metadata.composer.deathYear}"

        metadata.composer.birthYear != null ->
            "born ${metadata.composer.birthYear}"

        metadata.composer.deathYear != null ->
            "died ${metadata.composer.deathYear}"

        else -> null
    }
    return listOfNotNull(composerName, lifespan).joinToString(" ")
}

private fun scoreInfoValue(value: String?): String? = value
    ?.trim()
    ?.takeIf(String::isNotEmpty)

private data class LibraryScreenModel(
    val visibleItems: List<SheetMusicItem>,
    val headerVisibleCount: Int,
    val headerTotalCount: Int,
    val visibleScoreCount: Int,
    val hiddenScoreCount: Int,
    val refinementTotalCount: Int,
    val collections: List<String>,
    val composers: List<String>,
    val filterCollections: List<String>,
    val filterTags: List<String>,
    val itemsById: Map<String, SheetMusicItem>,
    val temporarySetlist: TemporarySetlistSession?,
    val activeTemporarySetlist: TemporarySetlistSession?,
    val activeTemporarySetlistItems: List<SheetMusicItem>,
    val activeSetlist: LibrarySetlist?,
    val activeSetlistItems: List<SheetMusicItem>,
    val currentScopeItems: List<SheetMusicItem>,
    val randomScopeLabel: String,
    val selectedItems: List<SheetMusicItem>,
    val editingItem: SheetMusicItem?,
    val infoItem: SheetMusicItem?,
    val infoMetadata: ScoreMetadata?,
    val deleteCandidate: SheetMusicItem?,
    val deleteSetlistCandidate: LibrarySetlist?,
    val deleteSongbookCandidate: LibrarySongbook?,
    val collectionGroups: Map<String, List<SheetMusicItem>>,
    val composerGroups: Map<String, List<SheetMusicItem>>,
)

internal fun currentLibraryScopeItems(
    snapshot: LibrarySnapshot,
    appState: LunarAppState,
): List<SheetMusicItem> {
    val visibleItems = snapshot.items.applyLibraryQuery(
        appState.query.withHiddenFilterFor(appState.browseMode)
    )
    val visibleLibraryItems = snapshot.items.filterNot { it.isHidden }
    val itemsById = visibleLibraryItems.associateBy { it.id }
    val temporarySetlist = appState.temporarySetlist
    val activeTemporarySetlist = temporarySetlist.takeIf { appState.temporarySetlistOpen }
    val activeTemporarySetlistItems = activeTemporarySetlist
        ?.itemIds
        ?.mapNotNull(itemsById::get)
        .orEmpty()
    val activeSetlist = snapshot.setlists.firstOrNull { it.id == appState.selectedSetlistId }
    val activeSetlistItems = activeSetlist
        ?.itemIds
        ?.mapNotNull(itemsById::get)
        .orEmpty()
    return currentScopeItems(
        snapshotItems = visibleLibraryItems,
        visibleItems = visibleItems,
        browseMode = appState.browseMode,
        selectedGroup = appState.selectedGroup,
        activeTemporarySetlistItems = activeTemporarySetlistItems,
        activeSetlistItems = activeSetlistItems,
        hasActiveTemporarySetlist = activeTemporarySetlist != null,
        hasActiveSetlist = activeSetlist != null,
    )
}

private fun buildLibraryScreenModel(
    snapshot: LibrarySnapshot,
    appState: LunarAppState,
): LibraryScreenModel {
    val visibleLibraryItems = snapshot.items.filterNot { it.isHidden }
    val hiddenLibraryItems = snapshot.items.filter { it.isHidden }
    val query = appState.query.withHiddenFilterFor(appState.browseMode)
    val visibleItems = snapshot.items.applyLibraryQuery(query)
    val collections = visibleLibraryItems.availableCollections().toList()
    val composers = visibleLibraryItems.availableComposers().toList()
    val filterBaseItems = if (appState.browseMode == LibraryBrowseMode.HIDDEN) {
        hiddenLibraryItems
    } else {
        visibleLibraryItems
    }
    val itemsById = visibleLibraryItems.associateBy { it.id }
    val temporarySetlist = appState.temporarySetlist
    val activeTemporarySetlist = temporarySetlist.takeIf { appState.temporarySetlistOpen }
    val activeTemporarySetlistItems = activeTemporarySetlist
        ?.itemIds
        ?.mapNotNull(itemsById::get)
        .orEmpty()
    val activeSetlist = snapshot.setlists.firstOrNull { it.id == appState.selectedSetlistId }
    val activeSetlistItems = activeSetlist
        ?.itemIds
        ?.mapNotNull(itemsById::get)
        .orEmpty()
    val currentScopeItems = currentLibraryScopeItems(
        snapshot = snapshot,
        appState = appState,
    )
    return LibraryScreenModel(
        visibleItems = visibleItems,
        headerVisibleCount = if (
            appState.browseMode == LibraryBrowseMode.SETLISTS ||
            appState.browseMode == LibraryBrowseMode.SONGBOOKS
        ) {
            visibleLibraryItems.size
        } else {
            visibleItems.size
        },
        headerTotalCount = if (appState.browseMode == LibraryBrowseMode.HIDDEN) {
            hiddenLibraryItems.size
        } else {
            visibleLibraryItems.size
        },
        visibleScoreCount = visibleLibraryItems.size,
        hiddenScoreCount = hiddenLibraryItems.size,
        refinementTotalCount = filterBaseItems.size,
        collections = collections,
        composers = composers,
        filterCollections = filterBaseItems.availableCollections().toList(),
        filterTags = filterBaseItems.availableTags().toList(),
        itemsById = itemsById,
        temporarySetlist = temporarySetlist,
        activeTemporarySetlist = activeTemporarySetlist,
        activeTemporarySetlistItems = activeTemporarySetlistItems,
        activeSetlist = activeSetlist,
        activeSetlistItems = activeSetlistItems,
        currentScopeItems = currentScopeItems,
        randomScopeLabel = randomScopeLabel(
            browseMode = appState.browseMode,
            selectedGroup = appState.selectedGroup,
            activeTemporarySetlist = activeTemporarySetlist,
            activeSetlist = activeSetlist,
        ),
        selectedItems = currentScopeItems.filter { it.id in appState.selectedScoreIds },
        editingItem = snapshot.items.firstOrNull { it.id == appState.editingItemId },
        infoItem = snapshot.items.firstOrNull { it.id == appState.infoItemId },
        infoMetadata = appState.infoMetadata,
        deleteCandidate = snapshot.items.firstOrNull { it.id == appState.deleteCandidateItemId },
        deleteSetlistCandidate = snapshot.setlists.firstOrNull { it.id == appState.deleteCandidateSetlistId },
        deleteSongbookCandidate = snapshot.songbooks.firstOrNull { it.id == appState.deleteCandidateSongbookId },
        collectionGroups = visibleItems.groupByCollectionName(),
        composerGroups = visibleItems.groupByComposerName(),
    )
}

private fun currentScopeItems(
    snapshotItems: List<SheetMusicItem>,
    visibleItems: List<SheetMusicItem>,
    browseMode: LibraryBrowseMode,
    selectedGroup: String?,
    activeTemporarySetlistItems: List<SheetMusicItem>,
    activeSetlistItems: List<SheetMusicItem>,
    hasActiveTemporarySetlist: Boolean,
    hasActiveSetlist: Boolean,
): List<SheetMusicItem> = when (browseMode) {
    LibraryBrowseMode.ALL -> visibleItems
    LibraryBrowseMode.BY_COLLECTION -> selectedGroup?.let {
        visibleItems.itemsInBrowseGroup(browseMode, it)
    } ?: visibleItems
    LibraryBrowseMode.BY_COMPOSER -> selectedGroup?.let {
        visibleItems.itemsInBrowseGroup(browseMode, it)
    } ?: visibleItems
    LibraryBrowseMode.SETLISTS -> when {
        hasActiveTemporarySetlist -> activeTemporarySetlistItems
        hasActiveSetlist -> activeSetlistItems
        else -> snapshotItems
    }
    LibraryBrowseMode.SONGBOOKS -> emptyList()
    LibraryBrowseMode.HIDDEN -> emptyList()
}

private fun com.ryanjames.lunar.library.model.LibraryQuery.withHiddenFilterFor(
    browseMode: LibraryBrowseMode,
): com.ryanjames.lunar.library.model.LibraryQuery = copy(
    hiddenFilter = if (browseMode == LibraryBrowseMode.HIDDEN) {
        HiddenScoreFilter.HIDDEN
    } else {
        HiddenScoreFilter.VISIBLE
    },
)

private fun randomScopeLabel(
    browseMode: LibraryBrowseMode,
    selectedGroup: String?,
    activeTemporarySetlist: TemporarySetlistSession?,
    activeSetlist: LibrarySetlist?,
): String = when (browseMode) {
    LibraryBrowseMode.ALL -> "the current library view"
    LibraryBrowseMode.BY_COLLECTION -> selectedGroup?.let { "\"$it\"" } ?: "the current library view"
    LibraryBrowseMode.BY_COMPOSER -> selectedGroup?.let { "\"$it\"" } ?: "the current library view"
    LibraryBrowseMode.SETLISTS -> when {
        activeTemporarySetlist != null -> "this temporary setlist"
        activeSetlist != null -> "\"${activeSetlist.name}\""
        else -> "the full library"
    }
    LibraryBrowseMode.SONGBOOKS -> "songbooks"
    LibraryBrowseMode.HIDDEN -> "hidden scores"
}

private fun List<SheetMusicItem>.groupByCollectionName(): Map<String, List<SheetMusicItem>> =
    groupBy { it.collection?.trim() ?: "" }

private fun List<SheetMusicItem>.groupByComposerName(): Map<String, List<SheetMusicItem>> =
    groupBy { it.composer?.trim() ?: "" }

private fun List<SheetMusicItem>.itemsInBrowseGroup(
    browseMode: LibraryBrowseMode,
    groupName: String,
): List<SheetMusicItem> = filter { item ->
    when (browseMode) {
        LibraryBrowseMode.BY_COLLECTION ->
            item.collection?.trim().equals(groupName, ignoreCase = true)

        LibraryBrowseMode.BY_COMPOSER ->
            item.composer?.trim().equals(groupName, ignoreCase = true)

        else -> false
    }
}

private fun buildSetlistPreviewTitles(
    itemIds: List<String>,
    itemsById: Map<String, SheetMusicItem>,
    emptyText: String,
): String = itemIds
    .mapNotNull(itemsById::get)
    .take(3)
    .joinToString("  •  ") { it.title }
    .ifBlank { emptyText }

private fun buildSongbookMetaLine(songbook: LibrarySongbook): String = buildList {
    add(songbook.itemIds.size.scoreLabel())
    songbook.pageCount?.let { add("$it pages") }
    add("Updated ${formatEpochMillis(songbook.updatedAtEpochMillis)}")
}.joinToString("  |  ")

@Composable
private fun SetlistCardFrame(
    accentBrush: Brush,
    containerColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accentBrush),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SetlistDetailScaffold(
    title: String,
    subtitle: String,
    itemCount: Int,
    emptyMessage: String,
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    onBack: () -> Unit,
    headerActions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetlistBackButton(onBack = onBack)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SummaryPillDark("${itemCount.scoreLabel()}")
            headerActions()
        }
        Box(modifier = Modifier.weight(1f)) {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FlatScoreList(items = items, appState = appState)
            }
        }
    }
}

@Composable
private fun SetlistBackButton(onBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.clickable(onClick = onBack),
    ) {
        Text(
            text = "Back",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun buildDetailsLine(item: SheetMusicItem): String {
    val parts = buildList {
        add("Added ${formatEpochMillis(item.dateAddedEpochMillis)}")
        item.lastOpenedEpochMillis?.let { add("Last opened ${formatEpochMillis(it)}") }
        if (item.lastViewedPage > 0) {
            add("Resume page ${item.lastViewedPage + 1}")
        }
    }
    return parts.joinToString("  |  ")
}

private fun Int.scoreLabel(): String = "$this score${if (this == 1) "" else "s"}"

private fun formatEpochMillis(value: Long): String {
    val date = Instant.fromEpochMilliseconds(value)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
    return "${date.year}-${date.monthNumber.toString().padStart(2, '0')}-${date.dayOfMonth.toString().padStart(2, '0')}"
}

private fun LibrarySortOption.label(): String = when (this) {
    LibrarySortOption.TITLE -> "Title"
    LibrarySortOption.COMPOSER -> "Composer"
    LibrarySortOption.DATE_ADDED -> "Date added"
    LibrarySortOption.LAST_OPENED -> "Last opened"
}

private fun LibraryLayoutMode.label(): String = when (this) {
    LibraryLayoutMode.GRID -> "Grid"
    LibraryLayoutMode.LIST -> "List"
}

private fun syncStatusLabel(syncStatus: SyncStatus): String = when (syncStatus) {
    SyncStatus.LocalOnly -> "Local sync stub"
    is SyncStatus.Syncing -> "Syncing ${syncStatus.providerName}"
    is SyncStatus.Ready -> "Sync: ${syncStatus.providerName}"
    is SyncStatus.Error -> "Sync issue: ${syncStatus.providerName}"
}
