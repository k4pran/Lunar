package com.ryanjames.lunar.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.contents

private val SongbookTwoPaneMinWidth = 720.dp
private val SongbookIndexWidth = 300.dp

/** Songbooks as bound editions: an index of covers beside the selected edition's contents. */
@Composable
internal fun SongbooksPane(
    snapshot: LibrarySnapshot,
    appState: LunarAppState,
    libraryItems: List<SheetMusicItem>,
    modifier: Modifier = Modifier,
) {
    val orderedSongbooks = snapshot.songbooks.sortedByDescending { it.updatedAtEpochMillis }
    val selected = snapshot.songbooks.firstOrNull { it.id == appState.selectedSongbookId }
    val itemsById = snapshot.items.associateBy { it.id }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val twoPane = maxWidth >= SongbookTwoPaneMinWidth
        if (twoPane) {
            Row(modifier = Modifier.fillMaxSize()) {
                SongbookIndexPane(
                    songbooks = orderedSongbooks,
                    selectedId = selected?.id,
                    onSelect = appState::selectSongbook,
                    modifier = Modifier
                        .width(SongbookIndexWidth)
                        .fillMaxHeight(),
                )
                VerticalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (selected == null) {
                        PanePlaceholder("Choose a songbook to see its contents.")
                    } else {
                        SongbookDetailPane(
                            songbook = selected,
                            itemsById = itemsById,
                            appState = appState,
                            onBack = null,
                        )
                    }
                }
            }
        } else if (selected == null) {
            SongbookIndexPane(
                songbooks = orderedSongbooks,
                selectedId = null,
                onSelect = appState::selectSongbook,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SongbookDetailPane(
                songbook = selected,
                itemsById = itemsById,
                appState = appState,
                onBack = appState::clearSongbookSelection,
            )
        }
    }

    val addTarget = appState.addScoresTarget as? AddScoresTarget.Songbook
    val addTargetSongbook = addTarget?.let { target -> snapshot.songbooks.firstOrNull { it.id == target.songbookId } }
    if (addTargetSongbook != null) {
        AddScoresDialog(
            title = "Add scores to ${addTargetSongbook.name}",
            candidates = libraryItems.filterNot { it.id in addTargetSongbook.itemIds },
            onDismiss = appState::dismissAddScores,
            onAdd = { chosen ->
                appState.dismissAddScores()
                appState.addSelectionToSongbook(
                    songbookId = addTargetSongbook.id,
                    items = chosen,
                    openAfterUpdate = false,
                )
            },
        )
    }
}

@Composable
private fun SongbookIndexPane(
    songbooks: List<LibrarySongbook>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PaneLabel("Editions")
        if (songbooks.isEmpty()) {
            Text(
                text = "No songbooks yet. Select scores in the library, then use Add to songbook to bind them into one PDF.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        songbooks.forEach { songbook ->
            key(songbook.id) {
                IndexEntry(
                    title = songbook.name,
                    summary = songbookSummaryLabel(songbook),
                    selected = songbook.id == selectedId,
                    onClick = { onSelect(songbook.id) },
                    leading = {
                        ClothCover(title = songbook.name, width = 34.dp, showTitle = false)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongbookDetailPane(
    songbook: LibrarySongbook,
    itemsById: Map<String, SheetMusicItem>,
    appState: LunarAppState,
    onBack: (() -> Unit)?,
) {
    val contents = songbook.contents(itemsById)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                Text("Back to songbooks")
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            ClothCover(
                title = songbook.name,
                width = 120.dp,
                modifier = Modifier.semantics { contentDescription = "Cover of ${songbook.name}" },
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = songbook.name,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = songbookSummaryLabel(songbook),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(onClick = { appState.openSongbook(songbook.id) }) {
                        Text("Open songbook")
                    }
                    if (appState.canCreateSongbooks) {
                        OutlinedButton(onClick = { appState.showAddScores(AddScoresTarget.Songbook(songbook.id)) }) {
                            Text("Add scores")
                        }
                    }
                    if (appState.canDownloadScores) {
                        OutlinedButton(onClick = { appState.exportSongbook(songbook.id) }) {
                            Text("Export PDF")
                        }
                    }
                    if (appState.canCreateSongbooks && appState.canPickSongbookCover) {
                        TextButton(onClick = { appState.changeSongbookCover(songbook.id) }) {
                            Text("Change cover")
                        }
                    }
                    TextButton(onClick = { appState.requestDeleteSongbook(songbook.id) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        HorizontalDivider()
        PaneLabel("Contents")
        if (contents.isEmpty()) {
            Text(
                text = "This songbook no longer lists its scores, but the bound PDF is still available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        contents.forEachIndexed { index, entry ->
            key(entry.itemId, index) {
                LeaderRow(
                    title = entry.item?.title ?: "Removed score",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    trailing = {
                        Text(
                            text = entry.startPage?.toString() ?: "\u2014",
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics {
                                contentDescription = entry.startPage
                                    ?.let { "Starts on page $it" }
                                    ?: "Start page unknown"
                            },
                        )
                    },
                )
            }
        }
    }
}

private fun songbookSummaryLabel(songbook: LibrarySongbook): String = buildList {
    add(songbook.itemIds.size.piecesLabel())
    songbook.pageCount?.let { add("$it pages") }
    if (songbook.hasCover == true) add("with cover")
}.joinToString("  |  ")
