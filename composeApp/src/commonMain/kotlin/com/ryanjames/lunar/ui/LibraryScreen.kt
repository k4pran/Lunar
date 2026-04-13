package com.ryanjames.lunar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.data.SyncStatus
import com.ryanjames.lunar.library.model.LibrarySetlist
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.library.model.applyLibraryQuery
import com.ryanjames.lunar.library.model.availableCollections
import com.ryanjames.lunar.library.model.availableComposers
import com.ryanjames.lunar.library.model.availableTags
import com.ryanjames.lunar.platform.PlatformRuntime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
fun LibraryScreen(
    runtime: PlatformRuntime,
    snapshot: LibrarySnapshot,
    appState: LunarAppState,
    modifier: Modifier = Modifier,
) {
    val visibleItems = snapshot.items.applyLibraryQuery(appState.query)
    val headerVisibleCount = if (appState.browseMode == LibraryBrowseMode.SETLISTS) {
        snapshot.items.size
    } else {
        visibleItems.size
    }
    val collections = snapshot.items.availableCollections()
    val composers = snapshot.items.availableComposers()
    val tags = snapshot.items.availableTags()
    val itemsById = snapshot.items.associateBy { it.id }
    val temporarySetlist = appState.temporarySetlist
    val activeTemporarySetlist = temporarySetlist.takeIf { appState.temporarySetlistOpen }
    val activeSetlist = snapshot.setlists.firstOrNull { it.id == appState.selectedSetlistId }
    val activeTemporarySetlistItems = activeTemporarySetlist?.itemIds?.mapNotNull(itemsById::get).orEmpty()
    val activeSetlistItems = activeSetlist?.itemIds?.mapNotNull(itemsById::get).orEmpty()
    val currentScopeItems = when (appState.browseMode) {
        LibraryBrowseMode.ALL -> visibleItems
        LibraryBrowseMode.BY_COLLECTION -> {
            val group = appState.selectedGroup
            if (group == null) {
                visibleItems
            } else {
                visibleItems.filter { item ->
                    item.collection?.trim().equals(group, ignoreCase = true)
                }
            }
        }
        LibraryBrowseMode.BY_COMPOSER -> {
            val group = appState.selectedGroup
            if (group == null) {
                visibleItems
            } else {
                visibleItems.filter { item ->
                    item.composer?.trim().equals(group, ignoreCase = true)
                }
            }
        }
        LibraryBrowseMode.SETLISTS -> when {
            activeTemporarySetlist != null -> activeTemporarySetlistItems
            activeSetlist != null -> activeSetlistItems
            else -> snapshot.items
        }
    }
    val sightReadingScopeLabel = when (appState.browseMode) {
        LibraryBrowseMode.ALL -> "the current library view"
        LibraryBrowseMode.BY_COLLECTION -> appState.selectedGroup?.let { "\"$it\"" } ?: "the current library view"
        LibraryBrowseMode.BY_COMPOSER -> appState.selectedGroup?.let { "\"$it\"" } ?: "the current library view"
        LibraryBrowseMode.SETLISTS -> when {
            activeTemporarySetlist != null -> "this temporary setlist"
            activeSetlist != null -> "\"${activeSetlist.name}\""
            else -> "the full library"
        }
    }
    val selectedItems = currentScopeItems.filter { it.id in appState.selectedScoreIds }
    val editingItem = snapshot.items.firstOrNull { it.id == appState.editingItemId }
    val deleteCandidate = snapshot.items.firstOrNull { it.id == appState.deleteCandidateItemId }
    val deleteSetlistCandidate = snapshot.setlists.firstOrNull { it.id == appState.deleteCandidateSetlistId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeaderPanel(
            runtime = runtime,
            snapshot = snapshot,
            visibleCount = headerVisibleCount,
        )
        BrowseModeSwitcher(appState = appState)
        if (appState.browseMode != LibraryBrowseMode.SETLISTS) {
            SearchAndSortBar(appState = appState)
            RefineLibraryPanel(
                availableCollections = collections.toList(),
                availableTags = tags.toList(),
                visibleCount = visibleItems.size,
                totalCount = snapshot.items.size,
                appState = appState,
            )
        }
        if (snapshot.items.isNotEmpty()) {
            SightReadingPanel(
                availableCount = currentScopeItems.size,
                scopeLabel = sightReadingScopeLabel,
                onOpenRandomSheet = { appState.openRandomSheet(currentScopeItems) },
                onCreateRandomSetlist = appState::showRandomSetlistBuilder,
            )
        }
        if (selectedItems.isNotEmpty()) {
            SelectionActionBar(
                selectedCount = selectedItems.size,
                onAddToSetlist = appState::showSetlistPicker,
                onClear = appState::clearScoreSelection,
            )
        }

        // Main content area
        Box(modifier = Modifier.weight(1f)) {
            when (appState.browseMode) {
                LibraryBrowseMode.ALL -> {
                    if (visibleItems.isEmpty()) {
                        EmptyLibraryState(
                            totalItemCount = snapshot.items.size,
                            onOpenImport = { appState.selectSection(AppSection.IMPORT) },
                            onClearFilters = appState::clearFilters,
                        )
                    } else {
                        FlatScoreList(items = visibleItems, appState = appState)
                    }
                }

                LibraryBrowseMode.BY_COLLECTION -> {
                    val group = appState.selectedGroup
                    if (group == null) {
                        GroupList(
                            groups = collections.toList(),
                            itemsByGroup = visibleItems.groupBy { it.collection?.trim() ?: "" },
                            emptyLabel = "No collections found. Import PDFs with a collection folder strategy.",
                            onSelectGroup = appState::selectGroup,
                        )
                    } else {
                        val groupItems = visibleItems.filter {
                            it.collection?.trim().equals(group, ignoreCase = true)
                        }
                        DrillDownScoreList(
                            groupName = group,
                            items = groupItems,
                            appState = appState,
                            onBack = appState::clearGroupSelection,
                        )
                    }
                }

                LibraryBrowseMode.BY_COMPOSER -> {
                    val group = appState.selectedGroup
                    if (group == null) {
                        GroupList(
                            groups = composers.toList(),
                            itemsByGroup = visibleItems.groupBy { it.composer?.trim() ?: "" },
                            emptyLabel = "No composers found. Import PDFs with a composer folder strategy.",
                            onSelectGroup = appState::selectGroup,
                        )
                    } else {
                        val groupItems = visibleItems.filter {
                            it.composer?.trim().equals(group, ignoreCase = true)
                        }
                        DrillDownScoreList(
                            groupName = group,
                            items = groupItems,
                            appState = appState,
                            onBack = appState::clearGroupSelection,
                        )
                    }
                }

                LibraryBrowseMode.SETLISTS -> {
                    if (activeTemporarySetlist != null) {
                        TemporarySetlistDetailView(
                            session = activeTemporarySetlist,
                            items = activeTemporarySetlistItems,
                            appState = appState,
                            onBack = appState::closeSetlist,
                            onSave = appState::showSaveTemporarySetlistDialog,
                            onDiscard = appState::discardTemporarySetlist,
                        )
                    } else if (activeSetlist == null) {
                        SetlistsOverview(
                            temporarySetlist = temporarySetlist,
                            setlists = snapshot.setlists,
                            itemsById = itemsById,
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
                            items = activeSetlistItems,
                            appState = appState,
                            onBack = appState::closeSetlist,
                            onDeleteSetlist = {
                                appState.requestDeleteSetlist(activeSetlist.id)
                            },
                        )
                    }
                }
            }
        }
    }

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

    if (deleteCandidate != null) {
        DeleteScoreDialog(
            item = deleteCandidate,
            onDismiss = appState::dismissDeleteRequest,
            onConfirm = appState::confirmDelete,
        )
    }

    if (appState.setlistPickerVisible && selectedItems.isNotEmpty()) {
        AddToSetlistDialog(
            selectedCount = selectedItems.size,
            setlists = snapshot.setlists,
            onDismiss = appState::dismissSetlistPicker,
            onCreateSetlist = { name ->
                appState.createSetlist(
                    name = name,
                    itemIds = selectedItems.map { it.id },
                )
            },
            onAddToSetlist = { setlistId ->
                appState.addSelectionToSetlist(
                    setlistId = setlistId,
                    itemIds = selectedItems.map { it.id },
                )
            },
        )
    }

    if (appState.randomSetlistBuilderVisible) {
        RandomSetlistDialog(
            availableCount = currentScopeItems.size,
            scopeLabel = sightReadingScopeLabel,
            onDismiss = appState::dismissRandomSetlistBuilder,
            onCreate = { count ->
                appState.createTemporaryRandomSetlist(
                    items = currentScopeItems,
                    requestedCount = count,
                )
            },
        )
    }

    if (appState.saveTemporarySetlistDialogVisible && temporarySetlist != null) {
        SaveTemporarySetlistDialog(
            session = temporarySetlist,
            onDismiss = appState::dismissSaveTemporarySetlistDialog,
            onSave = appState::saveTemporarySetlist,
        )
    }

    if (deleteSetlistCandidate != null) {
        DeleteSetlistDialog(
            setlist = deleteSetlistCandidate,
            onDismiss = appState::dismissDeleteSetlistRequest,
            onConfirm = appState::confirmDeleteSetlist,
        )
    }
}

@Composable
private fun HeaderPanel(
    runtime: PlatformRuntime,
    snapshot: LibrarySnapshot,
    visibleCount: Int,
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
            SummaryPill("${snapshot.items.size} scores")
            if (visibleCount != snapshot.items.size) {
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
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun FlatScoreList(items: List<SheetMusicItem>, appState: LunarAppState) {
    when (appState.layoutMode) {
        LibraryLayoutMode.GRID -> LibraryGrid(items = items, appState = appState)
        LibraryLayoutMode.LIST -> LibraryList(items = items, appState = appState)
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
private fun SightReadingPanel(
    availableCount: Int,
    scopeLabel: String,
    onOpenRandomSheet: () -> Unit,
    onCreateRandomSetlist: () -> Unit,
) {
    val hasScores = availableCount > 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Sight reading",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = if (hasScores) {
                        "Shuffle $scopeLabel and jump into something unexpected. ${availableCount.scoreLabel()} available."
                    } else {
                        "There are no scores available in $scopeLabel right now."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = hasScores,
                    onClick = onOpenRandomSheet,
                ) {
                    Text("Open random sheet")
                }
                Button(
                    enabled = hasScores,
                    onClick = onCreateRandomSetlist,
                ) {
                    Text("Create random setlist")
                }
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    onAddToSetlist: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
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
                    text = "Add the current selection to a saved setlist or clear it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClear) {
                    Text("Clear", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Button(onClick = onAddToSetlist) {
                    Text("Add to setlist")
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
                text = "Select one or more scores from the library, use Add to setlist, or build a temporary random setlist for a quick sight-reading session.",
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
    val previewTitles = session.itemIds
        .mapNotNull(itemsById::get)
        .take(3)
        .joinToString("  •  ") { it.title }
        .ifBlank { "No scores are left in this temporary mix." }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                themePalette.headerGradientStart,
                                themePalette.headerGradientEnd,
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
                    text = previewTitles,
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
    val previewTitles = setlist.itemIds
        .mapNotNull(itemsById::get)
        .take(3)
        .joinToString("  •  ") { it.title }
        .ifBlank { "No scores saved yet." }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
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
                    text = previewTitles,
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
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
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
                    text = "Back",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Temporary sight-reading mix. Save it if you want to keep this run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SummaryPillDark("${items.size.scoreLabel()}")
            OutlinedButton(onClick = onSave) {
                Text("Save")
            }
            TextButton(onClick = onDiscard) {
                Text("Discard", color = MaterialTheme.colorScheme.error)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "This temporary setlist no longer has any available scores.",
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
private fun SetlistDetailView(
    setlist: LibrarySetlist,
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    onBack: () -> Unit,
    onDeleteSetlist: () -> Unit,
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = setlist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Saved automatically. Delete it whenever you no longer need it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SummaryPillDark("${items.size.scoreLabel()}")
            TextButton(onClick = onDeleteSetlist) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "This setlist is empty right now.",
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
private fun SearchAndSortBar(appState: LunarAppState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Row 1: full-width search field
        OutlinedTextField(
            value = appState.query.searchText,
            onValueChange = appState::updateSearchText,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search title, composer, tags…") },
        )
        // Row 2: sort / direction / layout controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SortMenuButton(
                current = appState.query.sortOption,
                onSelect = appState::updateSortOption,
                modifier = Modifier.weight(1f),
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
            LayoutToggle(
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sort: ${current.label()}")
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

    val selectedTags = appState.query.selectedTags
    val orderedCollections = availableCollections.sortedWith(String.CASE_INSENSITIVE_ORDER)
    val orderedTags = availableTags.sortedWith(
        compareBy<String> { if (it in selectedTags) 0 else 1 }
            .then(String.CASE_INSENSITIVE_ORDER)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = if (visibleCount == totalCount) {
                            "Browsing ${totalCount.scoreLabel()}. Narrow the library by collection or tag."
                        } else {
                            "Showing ${visibleCount.scoreLabel()} out of ${totalCount.scoreLabel()}."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasActiveRefinements) {
                    TextButton(onClick = appState::clearRefinements) {
                        Text("Clear filters")
                    }
                }
            }

            if (orderedCollections.isNotEmpty()) {
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

            if (orderedTags.isNotEmpty()) {
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
    OutlinedButton(onClick = onToggle) {
        Text(if (sortDirection == SortDirection.ASCENDING) "Asc" else "Desc")
    }
}

@Composable
private fun LayoutToggle(
    selected: LibraryLayoutMode,
    onSelect: (LibraryLayoutMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LayoutButton(
            text = "Grid",
            selected = selected == LibraryLayoutMode.GRID,
            onClick = { onSelect(LibraryLayoutMode.GRID) },
        )
        LayoutButton(
            text = "List",
            selected = selected == LibraryLayoutMode.LIST,
            onClick = { onSelect(LibraryLayoutMode.LIST) },
        )
    }
}

@Composable
private fun LayoutButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = containerColor,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
        )
    }
}

@Composable
private fun LibraryGrid(
    items: List<SheetMusicItem>,
    appState: LunarAppState,
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
            )
        }
    }
}

@Composable
private fun LibraryList(
    items: List<SheetMusicItem>,
    appState: LunarAppState,
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
            )
        }
    }
}

@Composable
private fun LibraryCard(
    item: SheetMusicItem,
    appState: LunarAppState,
) {
    val themePalette = lunarThemePalette()
    val isSelected = item.id in appState.selectedScoreIds
    val selectedBorderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    } else {
        Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = selectedBorderColor,
                shape = MaterialTheme.shapes.large,
            )
            .clickable { appState.openPreview(item) },
        shape = MaterialTheme.shapes.large,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(
            defaultElevation = 2.dp,
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
                        if (isSelected) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(
                                    text = "Selected",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
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
                        androidx.compose.material3.TextButton(
                            onClick = { appState.toggleScoreSelection(item.id) },
                        ) {
                            Text(
                                if (isSelected) "Unselect" else "Select",
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { appState.toggleFavorite(item.id) },
                        ) {
                            Text(
                                if (item.isFavorite) "★ Unfav" else "☆ Fav",
                                color = if (item.isFavorite) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { appState.startEditing(item.id) },
                        ) {
                            Text("✎ Edit", color = MaterialTheme.colorScheme.primary)
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { appState.requestDelete(item.id) },
                        ) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                        androidx.compose.material3.Button(onClick = { appState.openPreview(item) }) {
                            Text("Open")
                        }
                    }
                }
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
                        "Build a temporary sight-reading setlist from $scopeLabel. Lunar will clamp the size to the ${availableCount.scoreLabel()} currently available."
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

private fun syncStatusLabel(syncStatus: SyncStatus): String = when (syncStatus) {
    SyncStatus.LocalOnly -> "Local sync stub"
    is SyncStatus.Syncing -> "Syncing ${syncStatus.providerName}"
    is SyncStatus.Ready -> "Sync: ${syncStatus.providerName}"
    is SyncStatus.Error -> "Sync issue: ${syncStatus.providerName}"
}
