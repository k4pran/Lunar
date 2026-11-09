package com.ryanjames.lunar.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.ui.design.LunarTestTags
import com.ryanjames.lunar.library.model.LibrarySetlist
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.library.model.ViewerSupportFilter

@Composable
internal fun LibraryToolbar(
    appState: LunarAppState,
    searchFocusRequester: FocusRequester,
    onTextInputFocusChanged: (Boolean) -> Unit,
    alphabetIndexAvailable: Boolean,
    alphabetIndexVisible: Boolean,
    onToggleAlphabetIndex: () -> Unit,
    filterCollections: List<String>,
    filterTags: List<String>,
    randomScoreAvailable: Boolean,
    onOpenRandomScore: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().testTag(LibraryTestTags.Toolbar)) {
        val stacked = maxWidth < 760.dp
        val search = @Composable { modifier: Modifier ->
            UnderlineSearchField(
                searchText = appState.query.searchText,
                onValueChange = appState::updateSearchText,
                onFocusChanged = onTextInputFocusChanged,
                focusRequester = searchFocusRequester,
                modifier = modifier,
            )
        }
        val controls = @Composable {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarTextButton(
                    label = "A-Z",
                    contentDescription = "Toggle A-Z index",
                    active = alphabetIndexAvailable && alphabetIndexVisible,
                    enabled = alphabetIndexAvailable,
                    onClick = onToggleAlphabetIndex,
                )
                LayoutTextToggle(selected = appState.layoutMode, onSelect = appState::updateLayoutMode)
                SortPopoverButton(
                    sortOption = appState.query.sortOption,
                    sortDirection = appState.query.sortDirection,
                    onSelectOption = appState::updateSortOption,
                    onSelectDirection = appState::updateSortDirection,
                )
                FilterPopoverButton(
                    appState = appState,
                    collections = filterCollections,
                    tags = filterTags,
                )
                ToolbarTextButton(
                    label = "Open a random score",
                    enabled = randomScoreAvailable,
                    onClick = onOpenRandomScore,
                )
            }
        }

        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                search(Modifier.fillMaxWidth())
                controls()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                search(Modifier.weight(1f))
                controls()
            }
        }
    }
}

@Composable
private fun UnderlineSearchField(
    searchText: String,
    onValueChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = searchText,
        onValueChange = onValueChange,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .testTag(LunarTestTags.LIBRARY_SEARCH)
            .semantics { contentDescription = "Library search" },
        singleLine = true,
        placeholder = { Text("Search title, composer, tags...") },
        trailingIcon = {
            Text(
                text = "Ctrl+K",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
    )
}

@Composable
private fun ToolbarTextButton(
    label: String,
    onClick: () -> Unit,
    contentDescription: String? = null,
    active: Boolean = false,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics {
            if (contentDescription != null) {
                this.contentDescription = contentDescription
            }
            selected = active
        },
    ) {
        Text(
            text = label,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** List | Gallery. */
@Composable
private fun LayoutTextToggle(
    selected: LibraryLayoutMode,
    onSelect: (LibraryLayoutMode) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ToolbarTextButton(
            label = LibraryLayoutMode.LIST.label(),
            contentDescription = "Show as list",
            active = selected == LibraryLayoutMode.LIST,
            onClick = { onSelect(LibraryLayoutMode.LIST) },
        )
        Text("|", color = MaterialTheme.colorScheme.outline)
        ToolbarTextButton(
            label = LibraryLayoutMode.GRID.label(),
            contentDescription = "Show as gallery",
            active = selected == LibraryLayoutMode.GRID,
            onClick = { onSelect(LibraryLayoutMode.GRID) },
        )
    }
}

@Composable
private fun SortPopoverButton(
    sortOption: LibrarySortOption,
    sortDirection: SortDirection,
    onSelectOption: (LibrarySortOption) -> Unit,
    onSelectDirection: (SortDirection) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolbarTextButton(
            label = "Sort: ${sortOption.label()}",
            contentDescription = "Sort by ${sortOption.label()}",
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PopoverHeading("Sort by")
            LibrarySortOption.entries.forEach { option ->
                CheckMenuItem(
                    label = option.label(),
                    checked = option == sortOption,
                    onClick = {
                        expanded = false
                        onSelectOption(option)
                    },
                )
            }
            HorizontalDivider()
            SortDirection.entries.forEach { direction ->
                CheckMenuItem(
                    label = direction.label(),
                    checked = direction == sortDirection,
                    onClick = {
                        expanded = false
                        onSelectDirection(direction)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterPopoverButton(
    appState: LunarAppState,
    collections: List<String>,
    tags: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }
    val query = appState.query
    val activeCount = activeLibraryFilters(appState).size

    Box {
        ToolbarTextButton(
            label = if (activeCount > 0) "Filter ($activeCount)" else "Filter",
            contentDescription = "Filter scores",
            active = activeCount > 0,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 240.dp).testTag(LibraryTestTags.FilterPopover),
        ) {
            CheckMenuItem(
                label = "Favourites only",
                checked = query.favoritesOnly,
                onClick = appState::toggleFavoriteFilter,
            )
            HorizontalDivider()
            PopoverHeading("Viewer support")
            ViewerSupportFilter.entries.forEach { filter ->
                CheckMenuItem(
                    label = filter.label(),
                    checked = query.viewerSupport == filter,
                    onClick = { appState.updateViewerSupportFilter(filter) },
                )
            }
            if (collections.isNotEmpty()) {
                HorizontalDivider()
                PopoverHeading("Collection")
                collections.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { collection ->
                    CheckMenuItem(
                        label = collection,
                        checked = query.selectedCollection.equals(collection, ignoreCase = true),
                        onClick = { appState.selectCollection(collection) },
                    )
                }
            }
            if (tags.isNotEmpty()) {
                HorizontalDivider()
                PopoverHeading(if (query.selectedTags.size > 1) "Tags (all must match)" else "Tags")
                tags.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { tag ->
                    CheckMenuItem(
                        label = tag,
                        checked = tag in query.selectedTags,
                        onClick = { appState.toggleTag(tag) },
                    )
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Done", color = MaterialTheme.colorScheme.primary) },
                onClick = { expanded = false },
            )
        }
    }
}

private fun SortDirection.label(): String = when (this) {
    SortDirection.ASCENDING -> "Ascending"
    SortDirection.DESCENDING -> "Descending"
}

internal data class ActiveLibraryFilter(
    val label: String,
    val onRemove: () -> Unit,
)

internal fun activeLibraryFilters(appState: LunarAppState): List<ActiveLibraryFilter> {
    val query = appState.query
    return buildList {
        if (query.favoritesOnly) {
            add(ActiveLibraryFilter("Favourites", appState::toggleFavoriteFilter))
        }
        query.selectedCollection?.let { collection ->
            add(ActiveLibraryFilter("Collection: $collection") { appState.selectCollection(collection) })
        }
        if (query.viewerSupport != ViewerSupportFilter.ALL) {
            add(
                ActiveLibraryFilter("Viewer: ${query.viewerSupport.label()}") {
                    appState.updateViewerSupportFilter(ViewerSupportFilter.ALL)
                }
            )
        }
        query.selectedTags.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { tag ->
            add(ActiveLibraryFilter("Tag: $tag") { appState.toggleTag(tag) })
        }
    }
}

@Composable
internal fun ActiveFilterChips(appState: LunarAppState) {
    val filters = activeLibraryFilters(appState)
    if (filters.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.forEach { filter ->
            InputChip(
                selected = true,
                onClick = filter.onRemove,
                label = { Text(filter.label) },
                trailingIcon = { Text("×") },
                modifier = Modifier.semantics { contentDescription = "Remove filter ${filter.label}" },
            )
        }
        TextButton(onClick = appState::clearRefinements) {
            Text("Clear filters")
        }
    }
}

/** Replaces the toolbar while scores are multi-selected. */
@Composable
internal fun LibrarySelectionBar(
    selectedItems: List<SheetMusicItem>,
    setlists: List<LibrarySetlist>,
    songbooks: List<LibrarySongbook>,
    appState: LunarAppState,
) {
    val targetIds = selectedItems.map { it.id }
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(LibraryTestTags.SelectionBar),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${selectedItems.size.scoreLabel()} selected",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Box {
                ToolbarTextButton(label = "Add to setlist", onClick = appState::showSetlistPicker)
                AddToSetlistPopover(
                    expanded = appState.setlistPickerVisible,
                    targetIds = targetIds,
                    setlists = setlists,
                    onDismiss = appState::dismissSetlistPicker,
                    onAddToSetlist = { setlistId -> appState.addSelectionToSetlist(setlistId, targetIds) },
                    onCreateSetlist = { name -> appState.createSetlist(name, targetIds) },
                )
            }
            if (appState.canCreateSongbooks) {
                Box {
                    ToolbarTextButton(label = "Add to songbook", onClick = appState::showSongbookPicker)
                    AddToSongbookPopover(
                        expanded = appState.songbookPickerVisible,
                        targetIds = targetIds,
                        songbooks = songbooks,
                        onDismiss = appState::dismissSongbookPicker,
                        onAddToSongbook = { songbookId -> appState.addSelectionToSongbook(songbookId, selectedItems) },
                        onNewSongbook = appState::showNewSongbookDialog,
                    )
                }
            }
            ToolbarTextButton(label = "Clear", onClick = appState::clearScoreSelection)
        }
    }
}

/** Anchored checklist of setlists plus a "New setlist" field. A checked setlist already holds every target. */
@Composable
internal fun AddToSetlistPopover(
    expanded: Boolean,
    targetIds: List<String>,
    setlists: List<LibrarySetlist>,
    onDismiss: () -> Unit,
    onAddToSetlist: (String) -> Unit,
    onCreateSetlist: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 260.dp).testTag(LibraryTestTags.SetlistPopover),
    ) {
        PopoverHeading("Add ${targetIds.size.scoreLabel()} to a setlist")
        setlists.sortedByDescending { it.updatedAtEpochMillis }.forEach { setlist ->
            val containsAll = setlist.itemIds.containsAll(targetIds)
            CheckMenuItem(
                label = setlist.name,
                checked = containsAll,
                enabled = !containsAll,
                onClick = { onAddToSetlist(setlist.id) },
            )
        }
        NewEntryField(
            value = newName,
            onValueChange = { newName = it },
            placeholder = "New setlist...",
            actionLabel = "Create",
            onSubmit = {
                onCreateSetlist(newName.trim())
                newName = ""
            },
        )
    }
}

/** Anchored checklist of songbooks; "New songbook..." opens the bind dialog for name and cover. */
@Composable
internal fun AddToSongbookPopover(
    expanded: Boolean,
    targetIds: List<String>,
    songbooks: List<LibrarySongbook>,
    onDismiss: () -> Unit,
    onAddToSongbook: (String) -> Unit,
    onNewSongbook: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 260.dp).testTag(LibraryTestTags.SongbookPopover),
    ) {
        PopoverHeading("Add ${targetIds.size.scoreLabel()} to a songbook")
        songbooks.sortedByDescending { it.updatedAtEpochMillis }.forEach { songbook ->
            val containsAll = songbook.itemIds.containsAll(targetIds)
            CheckMenuItem(
                label = songbook.name,
                checked = containsAll,
                enabled = !containsAll,
                onClick = { onAddToSongbook(songbook.id) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("New songbook...") },
            onClick = onNewSongbook,
        )
    }
}

@Composable
private fun PopoverHeading(text: String) {
    SmallCapsText(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun CheckMenuItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

@Composable
private fun NewEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    actionLabel: String,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier
                .widthIn(max = 200.dp)
                .semantics { contentDescription = placeholder },
        )
        TextButton(enabled = value.isNotBlank(), onClick = onSubmit) {
            Text(actionLabel)
        }
    }
}
