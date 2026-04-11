package com.ryanjames.lunar.library.model

data class LibraryQuery(
    val searchText: String = "",
    val selectedTags: Set<String> = emptySet(),
    val selectedCollection: String? = null,
    val favoritesOnly: Boolean = false,
    val sortOption: LibrarySortOption = LibrarySortOption.DATE_ADDED,
    val sortDirection: SortDirection = SortDirection.DESCENDING,
)

fun List<SheetMusicItem>.applyLibraryQuery(query: LibraryQuery): List<SheetMusicItem> {
    val normalizedSearch = query.searchText.trim().lowercase()
    val normalizedCollection = query.selectedCollection?.trim()?.lowercase().orEmpty()
    val normalizedTags = query.selectedTags.map { it.trim().lowercase() }.toSet()

    val filtered = filter { item ->
        if (query.favoritesOnly && !item.isFavorite) {
            return@filter false
        }

        if (normalizedCollection.isNotEmpty() &&
            item.collection.orEmpty().trim().lowercase() != normalizedCollection
        ) {
            return@filter false
        }

        if (normalizedTags.isNotEmpty()) {
            val itemTags = item.tags.map { it.trim().lowercase() }.toSet()
            if (itemTags.intersect(normalizedTags).isEmpty()) {
                return@filter false
            }
        }

        if (normalizedSearch.isEmpty()) {
            return@filter true
        }

        buildSearchFields(item).any { field -> field.contains(normalizedSearch) }
    }

    return when (query.sortOption) {
        LibrarySortOption.TITLE -> sortByTitle(filtered, query.sortDirection)
        LibrarySortOption.COMPOSER -> sortByComposer(filtered, query.sortDirection)
        LibrarySortOption.DATE_ADDED -> sortByDateAdded(filtered, query.sortDirection)
        LibrarySortOption.LAST_OPENED -> sortByLastOpened(filtered, query.sortDirection)
    }
}

fun List<SheetMusicItem>.availableTags(): Set<String> =
    flatMap { it.tags }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctSortedCaseInsensitive()

fun List<SheetMusicItem>.availableCollections(): Set<String> =
    mapNotNull { it.collection?.trim()?.takeIf(String::isNotEmpty) }
        .distinctSortedCaseInsensitive()

fun List<SheetMusicItem>.availableComposers(): Set<String> =
    mapNotNull { it.composer?.trim()?.takeIf(String::isNotEmpty) }
        .distinctSortedCaseInsensitive()

private fun buildSearchFields(item: SheetMusicItem): List<String> = buildList {
    add(item.title)
    item.composer?.let(::add)
    item.collection?.let(::add)
    addAll(item.tags)
}.map { it.trim().lowercase() }

private fun sortByTitle(
    items: List<SheetMusicItem>,
    direction: SortDirection,
): List<SheetMusicItem> {
    val comparator = compareBy<SheetMusicItem, String>(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.composer.orEmpty().trim() }

    return items.sortedWith(comparatorFor(direction, comparator))
}

private fun sortByComposer(
    items: List<SheetMusicItem>,
    direction: SortDirection,
): List<SheetMusicItem> {
    val comparator = compareBy<SheetMusicItem, String>(String.CASE_INSENSITIVE_ORDER) {
        it.composer.orEmpty().trim().ifEmpty { "~" }
    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }

    return items.sortedWith(comparatorFor(direction, comparator))
}

private fun sortByDateAdded(
    items: List<SheetMusicItem>,
    direction: SortDirection,
): List<SheetMusicItem> {
    val comparator = compareBy<SheetMusicItem> { it.dateAddedEpochMillis }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }

    return items.sortedWith(comparatorFor(direction, comparator))
}

private fun sortByLastOpened(
    items: List<SheetMusicItem>,
    direction: SortDirection,
): List<SheetMusicItem> {
    val comparator = compareBy<SheetMusicItem> { it.lastOpenedEpochMillis ?: Long.MIN_VALUE }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }

    return items.sortedWith(comparatorFor(direction, comparator))
}

private fun <T> comparatorFor(
    direction: SortDirection,
    comparator: Comparator<T>,
): Comparator<T> = when (direction) {
    SortDirection.ASCENDING -> comparator
    SortDirection.DESCENDING -> comparator.reversed()
}

private fun Iterable<String>.distinctSortedCaseInsensitive(): Set<String> {
    val seen = linkedSetOf<String>()
    return this
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
        .filter { value ->
            val normalized = value.lowercase()
            seen.add(normalized)
        }
        .toSet()
}