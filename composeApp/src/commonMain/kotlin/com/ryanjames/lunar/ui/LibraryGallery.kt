package com.ryanjames.lunar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryanjames.lunar.library.model.LibraryGroup
import com.ryanjames.lunar.library.model.NonLetterIndexKey
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.alphabetIndex
import com.ryanjames.lunar.library.model.groupForCatalogue
import kotlinx.coroutines.launch

internal val GalleryMinCardWidth = 160.dp

private val AlphabetIndexLetters: List<Char> = ('A'..'Z').toList() + NonLetterIndexKey

/**
 * Position of each group's first lazy-grid item (its header, or its first
 * card for an unnamed flat group), in the same order as [groups].
 */
internal fun galleryGroupPositions(groups: List<LibraryGroup>): List<Int> {
    var position = 0
    return groups.map { group ->
        val start = position
        position += group.items.size + if (group.name.isNotEmpty()) 1 else 0
        start
    }
}

/** Label for the focused card's open action; page 1 just reads "Open". */
internal fun galleryOpenLabel(item: SheetMusicItem): String =
    if (item.lastViewedPage > 0) "Open at p. ${item.lastViewedPage + 1}" else "Open"

/** Stable, case-insensitive swatch slot for a collection name. */
internal fun collectionSwatchSlot(collection: String, slotCount: Int): Int =
    collection.trim().lowercase().sumOf(Char::code) % slotCount

@Composable
internal fun LibraryGallery(
    items: List<SheetMusicItem>,
    appState: LunarAppState,
    mode: ScoreListMode,
    grouped: Boolean,
) {
    val sortOption = appState.query.sortOption
    val groups = remember(items, grouped, sortOption) {
        if (grouped) items.groupForCatalogue(sortOption) else listOf(LibraryGroup(name = "", items = items))
    }
    val groupPositions = remember(groups) { galleryGroupPositions(groups) }
    val letterIndex = remember(groups) { alphabetIndex(groups) }
    val orderedIds = remember(groups) { groups.flatMap { group -> group.items.map(SheetMusicItem::id) } }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (letterIndex.isNotEmpty()) {
            AlphabetIndexBar(
                enabledLetters = letterIndex.keys,
                onSelectLetter = { letter ->
                    val groupIndex = letterIndex[letter] ?: return@AlphabetIndexBar
                    scope.launch { gridState.animateScrollToItem(groupPositions[groupIndex]) }
                },
            )
        }
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = GalleryMinCardWidth),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            groups.forEachIndexed { groupIndex, group ->
                if (group.name.isNotEmpty()) {
                    item(
                        key = "group:$groupIndex:${group.name}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        GalleryGroupHeader(name = group.name, count = group.items.size)
                    }
                }
                items(items = group.items, key = SheetMusicItem::id) { item ->
                    GalleryCard(
                        item = item,
                        appState = appState,
                        mode = mode,
                        orderedIds = orderedIds,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlphabetIndexBar(
    enabledLetters: Set<Char>,
    onSelectLetter: (Char) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AlphabetIndexLetters.forEach { letter ->
            val enabled = letter in enabledLetters
            Text(
                text = letter.toString(),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = enabled) { onSelectLetter(letter) }
                    .semantics { contentDescription = "Jump to $letter" }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                },
            )
        }
    }
}

@Composable
private fun GalleryGroupHeader(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "smcp, c2sc",
                letterSpacing = 1.2.sp,
            ),
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
                .size(height = 1.dp, width = 0.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
private fun GalleryCard(
    item: SheetMusicItem,
    appState: LunarAppState,
    mode: ScoreListMode,
    orderedIds: List<String>,
) {
    val isFocused = appState.focusedScoreId == item.id
    val isSelected = item.id in appState.selectedScoreIds
    val shape = MaterialTheme.shapes.medium
    val accent = MaterialTheme.colorScheme.primary
    var toggleOnClick by remember { mutableStateOf(false) }
    var rangeOnClick by remember { mutableStateOf(false) }

    val ring = when {
        isFocused -> Modifier.border(2.dp, accent, shape)
        isSelected -> Modifier.border(2.dp, accent.copy(alpha = 0.45f), shape)
        else -> Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(ring)
            .clip(shape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.surfaceBright.copy(alpha = 0.96f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .pointerInput(Unit) {
                // Remember the keyboard modifiers of the press so the click
                // handler can tell plain, Ctrl/Cmd and Shift clicks apart.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) {
                            val modifiers = event.keyboardModifiers
                            toggleOnClick = modifiers.isCtrlPressed || modifiers.isMetaPressed
                            rangeOnClick = modifiers.isShiftPressed
                        }
                    }
                }
            }
            .combinedClickable(
                onClick = {
                    when {
                        rangeOnClick -> appState.selectScoreRange(orderedIds, item.id)
                        toggleOnClick -> appState.toggleScoreSelectionAndFocus(item.id)
                        else -> appState.focusScore(item.id)
                    }
                    toggleOnClick = false
                    rangeOnClick = false
                },
                onDoubleClick = { appState.openScore(item) },
                // Touch has no Ctrl/Cmd: long-press toggles selection instead.
                onLongClick = { appState.toggleScoreSelectionAndFocus(item.id) },
            )
            .semantics { selected = isSelected }
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(MaterialTheme.shapes.small),
        ) {
            ScoreThumbnail(
                item = item,
                cache = appState.thumbnailCache,
                modifier = Modifier.fillMaxSize(),
            )
            GallerySelectToggle(
                title = item.title,
                selected = isSelected,
                onToggle = { appState.toggleScoreSelection(item.id) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
            )
            if (isFocused) {
                GalleryOpenBar(
                    label = galleryOpenLabel(item),
                    onOpen = { appState.openScore(item) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        TruncatedLine(
            text = item.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FontFamily.Serif,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = if (isFocused) accent else MaterialTheme.colorScheme.onSurface,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val collection = item.collection?.trim()?.takeIf(String::isNotEmpty)
            CollectionSwatch(collection = collection)
            TruncatedLine(
                text = collection ?: "No collection",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isFocused) {
            LibraryItemQuickActions(
                item = item,
                appState = appState,
                includeDownload = appState.canDownloadScores,
                mode = mode,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun TruncatedLine(
    text: String,
    style: TextStyle,
    color: Color,
) {
    LunarTooltip(text = text) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CollectionSwatch(collection: String?) {
    val swatches = collectionSwatches()
    val color = if (collection == null) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        swatches[collectionSwatchSlot(collection, swatches.size)]
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun collectionSwatches(): List<Color> {
    val palette = lunarThemePalette()
    val scheme = MaterialTheme.colorScheme
    return listOf(
        scheme.primary,
        scheme.secondary,
        scheme.tertiary,
        palette.accentGradientStart,
        palette.accentGradientEnd,
    )
}

/**
 * Always-visible selection checkbox so touch users can build a multi-selection
 * without Ctrl/Cmd or Shift.
 */
@Composable
private fun GallerySelectToggle(
    title: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = "Select $title" },
        shape = CircleShape,
        color = if (selected) scheme.primary else scheme.surface.copy(alpha = 0.85f),
        border = if (selected) null else BorderStroke(1.5.dp, scheme.outline),
    ) {
        if (selected) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun GalleryOpenBar(
    label: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
    ) {
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
