package com.ryanjames.lunar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.library.model.applyLibraryQuery
import com.ryanjames.lunar.library.model.availableCollections
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
    val collections = snapshot.items.availableCollections()
    val tags = snapshot.items.availableTags()
    val editingItem = snapshot.items.firstOrNull { it.id == appState.editingItemId }
    val deleteCandidate = snapshot.items.firstOrNull { it.id == appState.deleteCandidateItemId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeaderPanel(
            runtime = runtime,
            snapshot = snapshot,
            visibleCount = visibleItems.size,
        )
        SearchAndSortBar(appState = appState)
        LibraryFilterRows(
            appState = appState,
            collections = collections.toList(),
            tags = tags.toList(),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (visibleItems.isEmpty()) {
                EmptyLibraryState(
                    totalItemCount = snapshot.items.size,
                    onOpenImport = { appState.selectSection(AppSection.IMPORT) },
                    onClearFilters = appState::clearFilters,
                )
            } else {
                when (appState.layoutMode) {
                    LibraryLayoutMode.GRID -> LibraryGrid(
                        items = visibleItems,
                        appState = appState,
                    )
                    LibraryLayoutMode.LIST -> LibraryList(
                        items = visibleItems,
                        appState = appState,
                    )
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
}

@Composable
private fun HeaderPanel(
    runtime: PlatformRuntime,
    snapshot: LibrarySnapshot,
    visibleCount: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF1F4F6B), Color(0xFF176A8A))),
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
                color = Color.White,
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
    Surface(
        color = Color.White.copy(alpha = 0.22f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
        )
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
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryFilterRows(
    appState: LunarAppState,
    collections: List<String>,
    tags: List<String>,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = appState.query.favoritesOnly,
                onClick = appState::toggleFavoriteFilter,
                label = { Text("★  Favorites only") },
            )
            TextButton(onClick = appState::clearFilters) {
                Text("Clear filters")
            }
        }
        if (collections.isNotEmpty()) {
            FilterRow(
                label = "Collections",
                values = collections,
                selectedValue = appState.query.selectedCollection,
                onSelect = appState::selectCollection,
            )
        }
        if (tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Tags",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                tags.forEach { tag ->
                    FilterChip(
                        selected = tag in appState.query.selectedTags,
                        onClick = { appState.toggleTag(tag) },
                        label = { Text(tag) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    values: List<String>,
    selectedValue: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        values.forEach { value ->
            FilterChip(
                selected = selectedValue == value,
                onClick = { onSelect(value) },
                label = { Text(value) },
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
        Color(0xFF1F4F6B)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

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
    Card(
        modifier = Modifier
            .fillMaxWidth()
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
                            colors = listOf(Color(0xFF176A8A), Color(0xFF4C8FD5))
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
                            color = Color(0xFF1A3E4F),
                        )
                        Text(
                            text = item.composer ?: "Composer not set",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FavoriteMarker(isFavorite = item.isFavorite)
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
                                color = Color(0xFFA7C6ED).copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.large,
                            ) {
                                Text(
                                    text = tag,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF1F4F6B),
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
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        androidx.compose.material3.TextButton(
                            onClick = { appState.toggleFavorite(item.id) },
                        ) {
                            Text(
                                if (item.isFavorite) "★ Unfav" else "☆ Fav",
                                color = if (item.isFavorite) Color(0xFF176A8A) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { appState.startEditing(item.id) },
                        ) {
                            Text("✎ Edit", color = Color(0xFF4D7C99))
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { appState.requestDelete(item.id) },
                        ) {
                            Text("Delete", color = Color(0xFFB42318))
                        }
                        androidx.compose.material3.Button(
                            onClick = { appState.openPreview(item) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1F4F6B),
                            ),
                        ) {
                            Text("Open", color = Color.White)
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
            Button(onClick = onConfirm) {
                Text("Delete")
            }
        },
    )
}

@Composable
private fun FavoriteMarker(isFavorite: Boolean) {
    val bgColor = if (isFavorite) Color(0xFF176A8A).copy(alpha = 0.18f) else Color.Transparent
    val textColor = if (isFavorite) Color(0xFF176A8A) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
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
        color = Color(0xFF1F4F6B).copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = "📁 $text",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF1F4F6B),
        )
    }
}

@Composable
private fun PagesPill(text: String) {
    Surface(
        color = Color(0xFF4C8FD5).copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.large,
    ) {
        Text(
            text = "📄 $text",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF176A8A),
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
                            Color(0xFFA7C6ED).copy(alpha = 0.3f),
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
                color = Color(0xFF1A3E4F),
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
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1F4F6B),
                    ),
                ) {
                    Text("Open import panel", color = Color.White)
                }
            } else {
                Button(
                    onClick = onClearFilters,
                    modifier = Modifier.padding(top = 18.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1F4F6B),
                    ),
                ) {
                    Text("Clear filters", color = Color.White)
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
