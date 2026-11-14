package com.ryanjames.lunar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.ryanjames.lunar.ui.design.LunarTestTags
import com.ryanjames.lunar.ui.design.lunarFocusRing
import androidx.compose.ui.unit.sp
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.SortDirection
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// --- Catalogue model -------------------------------------------------------

internal enum class CatalogueGrouping {
    NONE,
    COMPOSER,
    COLLECTION,
}

/** A run of catalogue rows. [header] is null for a flat list; [indexKey] is blank for the "no name" group. */
internal data class CatalogueGroup(
    val header: String?,
    val indexKey: String,
    val items: List<SheetMusicItem>,
) {
    /** Lazy-list key for the header; the unnamed group never collides with a group literally named like it. */
    val headerKey: String
        get() = if (indexKey.isEmpty()) {
            "${CatalogueGroupKeyPrefix}unnamed"
        } else {
            "${CatalogueGroupKeyPrefix}named:${indexKey.lowercase()}"
        }
}

/** One letter of the A-Z index; [targetIndex] points into the labels it was built from, or null when unused. */
internal data class AlphabetIndexEntry(
    val letter: Char,
    val targetIndex: Int?,
) {
    val enabled: Boolean
        get() = targetIndex != null
}

internal const val AlphabetIndexOtherLetter = '#'

/** Arrange by Composer or Collection groups by that field; All groups by composer only when sorted by composer. */
internal fun catalogueGroupingFor(
    browseMode: LibraryBrowseMode,
    sortOption: LibrarySortOption,
): CatalogueGrouping = when (browseMode) {
    LibraryBrowseMode.BY_COMPOSER -> CatalogueGrouping.COMPOSER
    LibraryBrowseMode.BY_COLLECTION -> CatalogueGrouping.COLLECTION
    else -> if (sortOption == LibrarySortOption.COMPOSER) {
        CatalogueGrouping.COMPOSER
    } else {
        CatalogueGrouping.NONE
    }
}

/**
 * Splits already-sorted [items] into catalogue groups. Named groups are alphabetical (reversed when the list
 * is sorted by the grouped field in descending order) and the unnamed group comes last. Rows keep their order.
 */
internal fun buildCatalogueGroups(
    items: List<SheetMusicItem>,
    grouping: CatalogueGrouping,
    sortOption: LibrarySortOption,
    sortDirection: SortDirection,
): List<CatalogueGroup> = when (grouping) {
    CatalogueGrouping.NONE -> if (items.isEmpty()) {
        emptyList()
    } else {
        listOf(CatalogueGroup(header = null, indexKey = "", items = items))
    }

    CatalogueGrouping.COMPOSER -> groupCatalogue(
        items = items,
        nameOf = SheetMusicItem::composer,
        unnamedHeader = "No composer",
        descending = sortOption == LibrarySortOption.COMPOSER && sortDirection == SortDirection.DESCENDING,
    )

    CatalogueGrouping.COLLECTION -> groupCatalogue(
        items = items,
        nameOf = SheetMusicItem::collection,
        unnamedHeader = "No collection",
        descending = false,
    )
}

private fun groupCatalogue(
    items: List<SheetMusicItem>,
    nameOf: (SheetMusicItem) -> String?,
    unnamedHeader: String,
    descending: Boolean,
): List<CatalogueGroup> {
    val groupedItems = linkedMapOf<String, MutableList<SheetMusicItem>>()
    val displayNames = mutableMapOf<String, String>()
    items.forEach { item ->
        val name = nameOf(item)?.trim().orEmpty()
        val key = name.lowercase()
        displayNames.getOrPut(key) { name }
        groupedItems.getOrPut(key) { mutableListOf() }.add(item)
    }
    val namedKeys = groupedItems.keys
        .filter(String::isNotEmpty)
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .let { if (descending) it.reversed() else it }
    val namedGroups = namedKeys.map { key ->
        val name = displayNames.getValue(key)
        CatalogueGroup(header = name, indexKey = name, items = groupedItems.getValue(key))
    }
    val unnamedGroup = groupedItems[""]?.let { CatalogueGroup(header = unnamedHeader, indexKey = "", items = it) }
    return namedGroups + listOfNotNull(unnamedGroup)
}

/**
 * Labels the A-Z index can jump to, paired with their LazyColumn index: group headers when the catalogue is
 * grouped, row titles when it is a flat list sorted by title, and nothing otherwise.
 */
internal fun catalogueIndexAnchors(
    groups: List<CatalogueGroup>,
    sortOption: LibrarySortOption,
    leadingItemCount: Int = 0,
): List<Pair<String, Int>> {
    var lazyIndex = leadingItemCount
    val anchors = mutableListOf<Pair<String, Int>>()
    val grouped = groups.any { it.header != null }
    if (!grouped && sortOption != LibrarySortOption.TITLE) return emptyList()
    groups.forEach { group ->
        if (group.header != null) {
            anchors += group.indexKey to lazyIndex
            lazyIndex += 1 + group.items.size
        } else {
            group.items.forEach { item ->
                anchors += item.title to lazyIndex
                lazyIndex += 1
            }
        }
    }
    return anchors
}

/** The LazyColumn index of [itemId]'s row, counting the leading strip and every group header before it. */
internal fun catalogueLazyIndexOf(
    groups: List<CatalogueGroup>,
    itemId: String?,
    leadingItemCount: Int = 0,
): Int? {
    if (itemId == null) return null
    var lazyIndex = leadingItemCount
    groups.forEach { group ->
        if (group.header != null) lazyIndex += 1
        val position = group.items.indexOfFirst { it.id == itemId }
        if (position >= 0) return lazyIndex + position
        lazyIndex += group.items.size
    }
    return null
}

/** The A-Z (then '#') index for [groups]: each letter points at the first group starting with it. */
internal fun alphabetIndex(groups: List<String>): List<AlphabetIndexEntry> {
    val firstIndexByLetter = mutableMapOf<Char, Int>()
    groups.forEachIndexed { index, label ->
        firstIndexByLetter.getOrPut(alphabetIndexLetterFor(label)) { index }
    }
    return (('A'..'Z') + AlphabetIndexOtherLetter).map { letter ->
        AlphabetIndexEntry(letter = letter, targetIndex = firstIndexByLetter[letter])
    }
}

internal fun alphabetIndexLetterFor(label: String): Char {
    val first = label.trim().firstOrNull()?.uppercaseChar() ?: return AlphabetIndexOtherLetter
    return if (first in 'A'..'Z') first else AlphabetIndexOtherLetter
}

// --- Catalogue UI ----------------------------------------------------------

internal enum class CatalogueClickKind {
    FOCUS,
    TOGGLE,
    RANGE,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryCatalogue(
    groups: List<CatalogueGroup>,
    sortOption: LibrarySortOption,
    jumpBackInItems: List<SheetMusicItem>,
    focusedItemId: String?,
    selectedItemIds: Set<String>,
    showAlphabetIndex: Boolean,
    nowEpochMillis: Long,
    selectionEnabled: Boolean,
    onRowClick: (SheetMusicItem, CatalogueClickKind) -> Unit,
    onRowOpen: (SheetMusicItem) -> Unit,
    onToggleSelection: (SheetMusicItem) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val leadingItemCount = if (jumpBackInItems.isNotEmpty()) 1 else 0
    val anchors = remember(groups, sortOption, leadingItemCount) {
        catalogueIndexAnchors(groups, sortOption, leadingItemCount)
    }
    val indexEntries = remember(anchors) { alphabetIndex(anchors.map { it.first }) }
    val scope = rememberCoroutineScope()
    val focusedLazyIndex = remember(groups, focusedItemId, leadingItemCount) {
        catalogueLazyIndexOf(groups, focusedItemId, leadingItemCount)
    }
    LaunchedEffect(focusedLazyIndex) {
        focusedLazyIndex?.let { listState.revealCatalogueItem(it) }
    }

    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(LibraryTestTags.Catalogue),
        ) {
            if (jumpBackInItems.isNotEmpty()) {
                item(key = "jump_back_in") {
                    JumpBackInStrip(
                        items = jumpBackInItems,
                        nowEpochMillis = nowEpochMillis,
                        onOpen = onRowOpen,
                    )
                }
            }
            groups.forEach { group ->
                val header = group.header
                if (header != null) {
                    stickyHeader(key = group.headerKey) {
                        CatalogueGroupHeader(title = header, count = group.items.size)
                    }
                }
                items(items = group.items, key = { it.id }) { item ->
                    CatalogueRow(
                        item = item,
                        focused = item.id == focusedItemId,
                        selected = item.id in selectedItemIds,
                        selectionEnabled = selectionEnabled,
                        onClick = { kind ->
                            onRowClick(item, if (selectionEnabled) kind else CatalogueClickKind.FOCUS)
                        },
                        onOpen = { onRowOpen(item) },
                        onToggleSelection = { onToggleSelection(item) },
                    )
                }
            }
        }
        if (showAlphabetIndex && anchors.isNotEmpty()) {
            AlphabetIndexRail(
                entries = indexEntries,
                onJump = { entry ->
                    val target = entry.targetIndex?.let(anchors::getOrNull)?.second ?: return@AlphabetIndexRail
                    scope.launch { listState.scrollToItem(target) }
                },
            )
        }
    }
}

internal const val CatalogueGroupKeyPrefix = "group:"

/**
 * Scrolls the least distance that shows the whole row at [index], keeping it clear of a pinned group header.
 * Rows that are not laid out at all are first brought in from the side they are on.
 */
private suspend fun LazyListState.revealCatalogueItem(index: Int) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        val below = index > (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1)
        scrollToItem(index)
        // Let the jump settle before measuring the row again.
        withFrameNanos { }
        if (below) {
            val info = layoutInfo
            val row = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
            // Coming from above, settle the row on the bottom edge rather than the top.
            val slack = info.viewportEndOffset - (row.offset + row.size)
            if (slack > 0) {
                scrollBy(-slack.toFloat())
                withFrameNanos { }
            }
        }
    }
    val info = layoutInfo
    val row = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val pinnedHeaderBottom = info.visibleItemsInfo
        .firstOrNull { item ->
            item.index < index &&
                (item.key as? String)?.startsWith(CatalogueGroupKeyPrefix) == true &&
                item.offset <= info.viewportStartOffset
        }
        ?.let { it.offset + it.size }
        ?: info.viewportStartOffset
    val visibleTop = maxOf(info.viewportStartOffset, pinnedHeaderBottom)
    when {
        row.offset + row.size > info.viewportEndOffset ->
            scrollBy((row.offset + row.size - info.viewportEndOffset).toFloat())
        row.offset < visibleTop -> scrollBy((row.offset - visibleTop).toFloat())
    }
}

@Composable
private fun CatalogueGroupHeader(title: String, count: Int) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SmallCapsText(text = title, modifier = Modifier.weight(1f, fill = false))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SmallCapsText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            letterSpacing = 1.6.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun CatalogueRow(
    item: SheetMusicItem,
    focused: Boolean,
    selected: Boolean,
    selectionEnabled: Boolean,
    onClick: (CatalogueClickKind) -> Unit,
    onOpen: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val band = when {
        focused -> accent.copy(alpha = 0.12f)
        selected -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .background(band)
            .testTag(LibraryTestTags.row(item.id))
            .semantics { this.selected = focused }
            .lunarFocusRing(RectangleShape)
            .catalogueRowClicks(onClick = onClick, onDoubleClick = onOpen)
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .background(if (focused) accent else Color.Transparent),
        )
        if (selectionEnabled) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.semantics { contentDescription = "Select ${item.title}" },
            )
        } else {
            Box(modifier = Modifier.width(12.dp))
        }
        LeaderRow(
            modifier = Modifier.weight(1f),
            title = {
                TruncatedText(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (focused) accent else MaterialTheme.colorScheme.onSurface,
                    textModifier = Modifier.testTag(LibraryTestTags.rowTitle(item.id)),
                )
            },
            trailing = { CatalogueRowTrailing(item) },
        )
    }
}

@Composable
private fun CatalogueRowTrailing(item: SheetMusicItem) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.isFavorite) {
            Text(
                text = "★",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { contentDescription = "Favourite" },
            )
        }
        item.collection?.trim()?.takeIf(String::isNotEmpty)?.let { collection ->
            // Weighted so the collection is the part that ellipsizes when the row is narrow.
            CollectionLabel(name = collection, modifier = Modifier.weight(1f, fill = false).widthIn(max = 160.dp))
        }
        Text(
            text = item.pageCount?.let { "$it pp" } ?: "- pp",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun CollectionLabel(name: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(collectionSwatchColor(name), MaterialTheme.shapes.extraSmall),
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A stable theme colour per collection name, so a collection keeps its swatch across sessions. */
@Composable
internal fun collectionSwatchColor(name: String): Color {
    val scheme = MaterialTheme.colorScheme
    val palette = listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.error, scheme.outline)
    return palette[collectionSwatchSlot(name, palette.size)]
}

internal fun collectionSwatchSlot(name: String, slotCount: Int): Int {
    val hash = name.trim().lowercase().fold(7) { acc, char -> acc * 31 + char.code }
    return hash.mod(slotCount)
}

/**
 * Title, a dotted leader that never shrinks below a minimum width, then trailing content. The title always keeps
 * at least half of the space left after the leader, so long trailing metadata cannot squeeze it out on narrow rows.
 */
@Composable
internal fun LeaderRow(
    title: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val leaderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    Layout(
        modifier = modifier,
        content = {
            Box { title() }
            Canvas(modifier = Modifier) {
                val y = size.height * 0.62f
                drawLine(
                    color = leaderColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.dp.toPx(), 4.dp.toPx())),
                )
            }
            Box { trailing() }
        },
    ) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val minLeader = 24.dp.roundToPx()
        val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else 4096
        val trailing = measurables[2].measure(Constraints(maxWidth = leaderRowTrailingBudget(maxWidth, minLeader, gap)))
        val titleMax = (maxWidth - trailing.width - minLeader - gap * 2).coerceAtLeast(0)
        val title = measurables[0].measure(Constraints(maxWidth = titleMax))
        val leaderWidth = (maxWidth - trailing.width - title.width - gap * 2).coerceAtLeast(0)
        val height = maxOf(title.height, trailing.height)
        val leader = measurables[1].measure(Constraints.fixed(leaderWidth, height.coerceAtLeast(1)))
        layout(maxWidth, height) {
            title.placeRelative(0, (height - title.height) / 2)
            leader.placeRelative(title.width + gap, 0)
            trailing.placeRelative(maxWidth - trailing.width, (height - trailing.height) / 2)
        }
    }
}

/** Widest the trailing content may be: whatever is left after the leader, gaps and the title's reserved half. */
internal fun leaderRowTrailingBudget(maxWidth: Int, minLeader: Int, gap: Int): Int {
    val shared = (maxWidth - minLeader - gap * 2).coerceAtLeast(0)
    return shared - shared / 2
}

/** One line with an ellipsis; the full text shows in a tooltip on hover or long-press and stays in semantics. */
@Composable
internal fun TruncatedText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    LunarTooltip(text, modifier = modifier) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = textModifier,
        )
    }
}

private class CatalogueClickTracker {
    var pendingKind: CatalogueClickKind = CatalogueClickKind.FOCUS
    var lastFocusClick: TimeMark? = null
}

/**
 * Plain click focuses (a second click within the double-click window opens), Ctrl/Cmd-click toggles the row
 * into the multi-selection and Shift-click extends it.
 */
@Composable
private fun Modifier.catalogueRowClicks(
    onClick: (CatalogueClickKind) -> Unit,
    onDoubleClick: () -> Unit,
): Modifier {
    val tracker = remember { CatalogueClickTracker() }
    return this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        val modifiers = event.keyboardModifiers
                        tracker.pendingKind = when {
                            modifiers.isShiftPressed -> CatalogueClickKind.RANGE
                            modifiers.isCtrlPressed || modifiers.isMetaPressed -> CatalogueClickKind.TOGGLE
                            else -> CatalogueClickKind.FOCUS
                        }
                    }
                }
            }
        }
        .clickable {
            val kind = tracker.pendingKind
            tracker.pendingKind = CatalogueClickKind.FOCUS
            val previous = tracker.lastFocusClick
            if (kind == CatalogueClickKind.FOCUS && previous != null && previous.elapsedNow() < DoubleClickWindow) {
                tracker.lastFocusClick = null
                onDoubleClick()
            } else {
                tracker.lastFocusClick = if (kind == CatalogueClickKind.FOCUS) TimeSource.Monotonic.markNow() else null
                onClick(kind)
            }
        }
}

private val DoubleClickWindow = 400.milliseconds

@Composable
private fun AlphabetIndexRail(
    entries: List<AlphabetIndexEntry>,
    onJump: (AlphabetIndexEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .verticalScroll(rememberScrollState())
            .testTag(LibraryTestTags.AlphabetIndex),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEach { entry ->
            val color = if (entry.enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            }
            Text(
                text = entry.letter.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = color,
                modifier = Modifier
                    .clickable(enabled = entry.enabled) { onJump(entry) }
                    .semantics { contentDescription = "Jump to ${entry.letter}" }
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun JumpBackInStrip(
    items: List<SheetMusicItem>,
    nowEpochMillis: Long,
    onOpen: (SheetMusicItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)
            .testTag(LibraryTestTags.JumpBackIn),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SmallCapsText("Jump back in")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { item ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpen(item) }
                        .semantics { contentDescription = "Jump back in to ${item.title}" },
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        TruncatedText(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = jumpBackInCaption(item, nowEpochMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

internal object LibraryTestTags {
    const val Catalogue = "library-catalogue"
    const val AlphabetIndex = "library-alphabet-index"
    const val JumpBackIn = "library-jump-back-in"
    const val DetailPanel = "library-detail-panel"
    const val DetailTitle = "library-detail-title"
    const val SelectionBar = "library-selection-bar"
    const val Toolbar = "library-toolbar"
    const val SetlistPopover = "library-setlist-popover"
    const val SongbookPopover = "library-songbook-popover"
    const val FilterPopover = "library-filter-popover"

    fun row(itemId: String): String = LunarTestTags.libraryRow(itemId)

    fun rowTitle(itemId: String): String = "library-row-title-$itemId"
}
