package com.ryanjames.lunar.ui

import com.ryanjames.lunar.library.data.LibrarySnapshot
import com.ryanjames.lunar.library.model.SheetMusicItem

/** Setlist id used for the unsaved Random Mix when it is played or edited like a setlist. */
const val TEMPORARY_SETLIST_ID = "temporary_random_mix"

/** The next piece in the set, or null once the last piece is showing. */
fun ViewerTarget.Setlist.next(itemCount: Int): ViewerTarget.Setlist? =
    if (index + 1 < itemCount) copy(index = index + 1) else null

/** Resolves a setlist (or the Random Mix) to its visible pieces, in programme order. */
internal fun resolveSetlistItems(
    snapshot: LibrarySnapshot,
    setlistId: String,
    temporarySetlist: TemporarySetlistSession?,
): List<SheetMusicItem> {
    val itemIds = if (setlistId == TEMPORARY_SETLIST_ID) {
        temporarySetlist?.itemIds
    } else {
        snapshot.setlists.firstOrNull { it.id == setlistId }?.itemIds
    }.orEmpty()
    val itemsById = snapshot.items.filterNot { it.isHidden }.associateBy { it.id }
    return itemIds.mapNotNull(itemsById::get)
}

/**
 * Applies the order of [visibleItemIds] to [allItemIds]. Ids that are not visible (for example
 * hidden scores) keep their slots, so the result always contains every original id exactly once.
 */
internal fun mergeVisibleSetlistOrder(
    allItemIds: List<String>,
    visibleItemIds: List<String>,
): List<String> {
    val visibleSet = visibleItemIds.toSet()
    val reorderedVisible = visibleItemIds.filter { it in allItemIds }.distinct().iterator()
    return allItemIds.map { itemId ->
        if (itemId in visibleSet && reorderedVisible.hasNext()) reorderedVisible.next() else itemId
    }
}

/** Returns a copy of the list with the element at [fromIndex] moved to [toIndex]. */
internal fun <T> List<T>.moved(fromIndex: Int, toIndex: Int): List<T> {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return this
    return toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
}

/** "3 pieces · 12 pages"; pages read "12+ pages" when some counts are unknown. */
internal fun setlistSummaryLabel(items: List<SheetMusicItem>): String {
    val pieces = "${items.size} piece${if (items.size == 1) "" else "s"}"
    val knownPages = items.mapNotNull { it.pageCount }
    if (knownPages.isEmpty()) return pieces
    val pages = knownPages.sum()
    val suffix = if (knownPages.size < items.size) "+" else ""
    return "$pieces · $pages$suffix page${if (pages == 1 && suffix.isEmpty()) "" else "s"}"
}
