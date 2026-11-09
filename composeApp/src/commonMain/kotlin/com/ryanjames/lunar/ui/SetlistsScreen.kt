package com.ryanjames.lunar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.model.SheetMusicItem

private val TwoPaneMinWidth = 720.dp
private val IndexPaneWidth = 300.dp

/** Setlists as concert programmes: an index of sets beside the selected programme. */
@Composable
internal fun SetlistsPane(
    snapshot: LibrarySnapshot,
    appState: LunarAppState,
    libraryItems: List<SheetMusicItem>,
    modifier: Modifier = Modifier,
) {
    val selectedId = when {
        appState.temporarySetlistOpen && appState.temporarySetlist != null -> TEMPORARY_SETLIST_ID
        else -> appState.selectedSetlistId?.takeIf { id -> snapshot.setlists.any { it.id == id } }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val twoPane = maxWidth >= TwoPaneMinWidth
        if (twoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                SetlistIndexPane(
                    snapshot = snapshot,
                    appState = appState,
                    libraryItems = libraryItems,
                    selectedId = selectedId,
                    modifier = Modifier
                        .width(IndexPaneWidth)
                        .fillMaxHeight(),
                )
                VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (selectedId == null) {
                        PanePlaceholder("Choose a setlist to see its programme.")
                    } else {
                        ProgrammePane(
                            snapshot = snapshot,
                            appState = appState,
                            setlistId = selectedId,
                            onBack = null,
                        )
                    }
                }
            }
        } else if (selectedId == null) {
            SetlistIndexPane(
                snapshot = snapshot,
                appState = appState,
                libraryItems = libraryItems,
                selectedId = null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ProgrammePane(
                snapshot = snapshot,
                appState = appState,
                setlistId = selectedId,
                onBack = appState::closeSetlist,
            )
        }
    }

    val addTarget = appState.addScoresTarget as? AddScoresTarget.Setlist
    if (addTarget != null) {
        val existingIds = resolveSetlistItems(snapshot, addTarget.setlistId, appState.temporarySetlist)
            .mapTo(mutableSetOf()) { it.id }
        AddScoresDialog(
            title = "Add scores to the programme",
            candidates = libraryItems.filterNot { it.id in existingIds },
            onDismiss = appState::dismissAddScores,
            onAdd = { chosen ->
                appState.dismissAddScores()
                appState.addSelectionToSetlist(addTarget.setlistId, chosen.map { it.id })
            },
        )
    }
}

@Composable
private fun SetlistIndexPane(
    snapshot: LibrarySnapshot,
    appState: LunarAppState,
    libraryItems: List<SheetMusicItem>,
    selectedId: String?,
    modifier: Modifier = Modifier,
) {
    val orderedSetlists = snapshot.setlists.sortedByDescending { it.updatedAtEpochMillis }
    var newSetlistOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PaneLabel("Random mix")
        val temporarySetlist = appState.temporarySetlist
        if (temporarySetlist != null) {
            val mixItems = resolveSetlistItems(snapshot, TEMPORARY_SETLIST_ID, temporarySetlist)
            IndexEntry(
                title = temporarySetlist.title,
                summary = setlistSummaryLabel(mixItems),
                selected = selectedId == TEMPORARY_SETLIST_ID,
                onClick = appState::openTemporarySetlist,
            ) {
                TextButton(onClick = appState::showSaveTemporarySetlistDialog) {
                    Text("Save")
                }
                TextButton(onClick = appState::discardTemporarySetlist) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            Text(
                text = "Shuffle a few scores into a one-off set. Save it if it works.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RandomSetlistPopover(
            expanded = appState.randomSetlistBuilderVisible,
            availableCount = libraryItems.size,
            scopeLabel = "the full library",
            onDismiss = appState::dismissRandomSetlistBuilder,
            onCreate = { count ->
                appState.createTemporaryRandomSetlist(items = libraryItems, requestedCount = count)
            },
        ) {
            OutlinedButton(
                onClick = appState::showRandomSetlistBuilder,
                enabled = libraryItems.isNotEmpty(),
            ) {
                Text("Random setlist")
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaneLabel("Saved setlists")
            NewSetlistPopover(
                expanded = newSetlistOpen,
                onDismiss = { newSetlistOpen = false },
                onCreate = { name ->
                    newSetlistOpen = false
                    appState.createSetlist(name = name, itemIds = emptyList())
                },
            ) {
                TextButton(onClick = { newSetlistOpen = true }) {
                    Text("New setlist")
                }
            }
        }
        if (orderedSetlists.isEmpty()) {
            Text(
                text = "No saved setlists yet. Select scores in the library and use Add to setlist, or start a new one here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        orderedSetlists.forEach { setlist ->
            key(setlist.id) {
                IndexEntry(
                    title = setlist.name,
                    summary = setlistSummaryLabel(
                        resolveSetlistItems(snapshot, setlist.id, temporarySetlist = null),
                    ),
                    selected = selectedId == setlist.id,
                    onClick = { appState.openSetlist(setlist.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgrammePane(
    snapshot: LibrarySnapshot,
    appState: LunarAppState,
    setlistId: String,
    onBack: (() -> Unit)?,
) {
    val isTemporary = setlistId == TEMPORARY_SETLIST_ID
    val savedSetlist = snapshot.setlists.firstOrNull { it.id == setlistId }
    val title = if (isTemporary) appState.temporarySetlist?.title.orEmpty() else savedSetlist?.name.orEmpty()
    val items = resolveSetlistItems(snapshot, setlistId, appState.temporarySetlist)
    var renaming by remember(setlistId) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                Text("Back to setlists")
            }
        }
        if (renaming && savedSetlist != null) {
            InlineRenameField(
                initialName = savedSetlist.name,
                onCancel = { renaming = false },
                onSave = { name ->
                    renaming = false
                    appState.renameSetlist(setlistId, name)
                },
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isTemporary) {
                        "Random mix  |  ${setlistSummaryLabel(items)}"
                    } else {
                        setlistSummaryLabel(items)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = { appState.startSetlist(setlistId) },
                enabled = items.isNotEmpty(),
            ) {
                Text("Start the set")
            }
            OutlinedButton(onClick = { appState.showAddScores(AddScoresTarget.Setlist(setlistId)) }) {
                Text("Add scores")
            }
            if (isTemporary) {
                TextButton(onClick = appState::showSaveTemporarySetlistDialog) {
                    Text("Save")
                }
                TextButton(onClick = appState::discardTemporarySetlist) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = { renaming = true }, enabled = !renaming) {
                    Text("Rename")
                }
                TextButton(onClick = { appState.requestDeleteSetlist(setlistId) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        HorizontalDivider()
        if (items.isEmpty()) {
            PanePlaceholder(
                if (isTemporary) {
                    "No scores are left in this random mix."
                } else {
                    "This programme is empty. Use Add scores to build it."
                }
            )
        } else {
            ProgrammeList(
                setlistId = setlistId,
                items = items,
                onReorder = { ids -> appState.reorderSetlist(setlistId, ids) },
                onOpen = { index -> appState.startSetlist(setlistId, index) },
                onRemove = { itemId -> appState.removeFromSetlist(setlistId, itemId) },
            )
        }
    }
}

/** Numbered programme rows; drag a row's handle to move it. */
@Composable
private fun ProgrammeList(
    setlistId: String,
    items: List<SheetMusicItem>,
    onReorder: (List<String>) -> Unit,
    onOpen: (Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    val itemIds = items.map { it.id }
    val itemsById = items.associateBy { it.id }
    // Only the locally dragged order is held here; row data always comes from the current
    // `items` so metadata updates (e.g. a resolved page count) show without a reorder.
    var order by remember(setlistId, itemIds) { mutableStateOf(itemIds) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableIntStateOf(1) }

    fun finishDrag() {
        draggingId = null
        dragOffset = 0f
        if (order != itemIds) {
            onReorder(order)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        order.forEachIndexed { index, orderedId ->
            val item = itemsById[orderedId] ?: return@forEachIndexed
            key(item.id) {
                val isDragging = draggingId == item.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { rowHeightPx = it.height.coerceAtLeast(1) }
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                        .background(
                            if (isDragging) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                            },
                            MaterialTheme.shapes.small,
                        )
                        .clickable { onOpen(index) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LeaderRow(
                        title = item.title,
                        modifier = Modifier.weight(1f),
                        leading = {
                            DragHandleGlyph(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .semantics { contentDescription = "Reorder ${item.title}" }
                                    // Restart when the programme or its persisted order changes so the
                                    // gesture never captures a stale `order` state or `finishDrag`.
                                    .pointerInput(setlistId, itemIds, item.id) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                draggingId = item.id
                                                dragOffset = 0f
                                            },
                                            onDragEnd = { finishDrag() },
                                            onDragCancel = { finishDrag() },
                                        ) { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount
                                            val currentIndex = order.indexOf(item.id)
                                            val step = rowHeightPx.toFloat()
                                            if (dragOffset > step / 2f && currentIndex < order.lastIndex) {
                                                order = order.moved(currentIndex, currentIndex + 1)
                                                dragOffset -= step
                                            } else if (dragOffset < -step / 2f && currentIndex > 0) {
                                                order = order.moved(currentIndex, currentIndex - 1)
                                                dragOffset += step
                                            }
                                        }
                                    },
                            )
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailing = {
                            Text(
                                text = listOfNotNull(
                                    item.composer?.takeIf(String::isNotBlank),
                                    item.pageCount?.let { "$it pp." },
                                ).joinToString("  |  ").ifEmpty { "-" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        },
                    )
                    TextButton(
                        onClick = { onRemove(item.id) },
                        modifier = Modifier.semantics { contentDescription = "Remove ${item.title} from setlist" },
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineRenameField(
    initialName: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Setlist name") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) {
            Text("Save name")
        }
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
    }
}

@Composable
internal fun RandomSetlistPopover(
    expanded: Boolean,
    availableCount: Int,
    scopeLabel: String,
    onDismiss: () -> Unit,
    onCreate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    anchor: @Composable () -> Unit,
) {
    AnchoredPopover(
        expanded = expanded,
        onDismiss = onDismiss,
        modifier = modifier,
        anchor = anchor,
    ) {
        var count by remember(availableCount) { mutableIntStateOf(minOf(5, availableCount).coerceAtLeast(1)) }
        Text(
            text = "Shuffle a random set",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
        )
        Text(
            text = if (availableCount > 0) {
                "Pick from $scopeLabel (${availableCount.piecesLabel()} available)."
            } else {
                "There are no scores available in $scopeLabel right now."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CountStepper(
            value = count,
            range = 1..availableCount.coerceAtLeast(1),
            onValueChange = { count = it },
            label = "Scores in the mix",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Button(onClick = { onCreate(count) }, enabled = availableCount > 0) {
                Text("Shuffle")
            }
        }
    }
}

@Composable
private fun NewSetlistPopover(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    anchor: @Composable () -> Unit,
) {
    AnchoredPopover(
        expanded = expanded,
        onDismiss = onDismiss,
        anchor = anchor,
    ) {
        var name by remember { mutableStateOf("") }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Setlist name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Button(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        }
    }
}

/** Picks library scores to append to a setlist or songbook. */
@Composable
internal fun AddScoresDialog(
    title: String,
    candidates: List<SheetMusicItem>,
    onDismiss: () -> Unit,
    onAdd: (List<SheetMusicItem>) -> Unit,
) {
    var chosenIds by remember(candidates) { mutableStateOf(emptyList<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    text = "Every visible score is already included.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    candidates.forEach { item ->
                        val checked = item.id in chosenIds
                        val toggle = {
                            chosenIds = if (checked) chosenIds - item.id else chosenIds + item.id
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = toggle),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { toggle() },
                                modifier = Modifier.semantics { contentDescription = "Choose ${item.title}" },
                            )
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                enabled = chosenIds.isNotEmpty(),
                onClick = {
                    // Keep the order the scores were ticked in.
                    val byId = candidates.associateBy { it.id }
                    onAdd(chosenIds.mapNotNull(byId::get))
                },
            ) {
                Text(if (chosenIds.isEmpty()) "Add" else "Add ${chosenIds.size.piecesLabel()}")
            }
        },
    )
}

@Composable
internal fun PaneLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun PanePlaceholder(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A selectable row in an index pane with an optional inline action area. */
@Composable
internal fun IndexEntry(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                },
                MaterialTheme.shapes.small,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        actions()
    }
}

internal fun Int.piecesLabel(): String = "$this score${if (this == 1) "" else "s"}"
