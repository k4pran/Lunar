package com.ryanjames.lunar.library.model

/**
 * A run of catalogue items shown under one header. An empty [name] means the
 * catalogue is flat (no header), which is the case for date-based sorts.
 */
data class LibraryGroup(
    val name: String,
    val items: List<SheetMusicItem>,
)

const val UnknownComposerGroupName = "Unknown composer"
const val NonLetterIndexKey = '#'

/**
 * Splits already-sorted [items] into catalogue groups that follow [sortOption]:
 * composer sort groups by composer, title sort groups by first letter, and
 * date sorts stay flat. Groups keep the incoming order, so the sort direction
 * is preserved; consecutive items with the same (case-insensitive) key share
 * a group.
 */
fun List<SheetMusicItem>.groupForCatalogue(sortOption: LibrarySortOption): List<LibraryGroup> {
    if (isEmpty()) return emptyList()
    val keyOf: (SheetMusicItem) -> String = when (sortOption) {
        LibrarySortOption.COMPOSER -> { item ->
            item.composer?.trim()?.takeIf(String::isNotEmpty) ?: UnknownComposerGroupName
        }
        LibrarySortOption.TITLE -> { item -> indexKeyFor(item.title).toString() }
        LibrarySortOption.DATE_ADDED,
        LibrarySortOption.LAST_OPENED,
        -> return listOf(LibraryGroup(name = "", items = this))
    }

    val groups = mutableListOf<LibraryGroup>()
    var currentName: String? = null
    var currentItems = mutableListOf<SheetMusicItem>()
    for (item in this) {
        val key = keyOf(item)
        if (currentName == null || !currentName.equals(key, ignoreCase = true)) {
            currentName?.let { groups += LibraryGroup(it, currentItems) }
            currentName = key
            currentItems = mutableListOf()
        }
        currentItems += item
    }
    currentName?.let { groups += LibraryGroup(it, currentItems) }
    return groups
}

/**
 * Maps each index letter (A-Z, or [NonLetterIndexKey]) to the position of the
 * first group whose name starts with it. Flat (unnamed) groups are skipped.
 */
fun alphabetIndex(groups: List<LibraryGroup>): Map<Char, Int> {
    val index = LinkedHashMap<Char, Int>()
    groups.forEachIndexed { position, group ->
        if (group.name.isNotEmpty()) {
            index.getOrPut(indexKeyFor(group.name)) { position }
        }
    }
    return index
}

private fun indexKeyFor(name: String): Char {
    val first = name.trim().firstOrNull()?.uppercaseChar() ?: return NonLetterIndexKey
    return if (first in 'A'..'Z') first else NonLetterIndexKey
}
