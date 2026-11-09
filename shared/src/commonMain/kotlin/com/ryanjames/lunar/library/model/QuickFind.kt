package com.ryanjames.lunar.library.model

/** How closely a quick find query matched a field. Lower ordinals rank first. */
enum class QuickFindMatch {
    PREFIX,
    WORD_START,
    SUBSTRING,
}

data class QuickFindResults(
    val scores: List<SheetMusicItem> = emptyList(),
    val setlists: List<LibrarySetlist> = emptyList(),
    val songbooks: List<LibrarySongbook> = emptyList(),
) {
    val isEmpty: Boolean
        get() = scores.isEmpty() && setlists.isEmpty() && songbooks.isEmpty()
}

/**
 * Ranks library content for the quick find palette. Scores match the same fields as the library
 * search (title, composer, collection, tags) and hidden scores never appear. Prefix matches rank
 * ahead of word-start matches, which rank ahead of plain substring matches; title matches win ties.
 * An empty query lists recently opened scores first and setlists/songbooks alphabetically.
 */
fun quickFind(
    items: List<SheetMusicItem>,
    setlists: List<LibrarySetlist>,
    songbooks: List<LibrarySongbook>,
    query: String,
    limitPerGroup: Int = Int.MAX_VALUE,
): QuickFindResults {
    val normalizedQuery = query.trim().lowercase()
    val visibleItems = items.filterNot(SheetMusicItem::isHidden)

    if (normalizedQuery.isEmpty()) {
        return QuickFindResults(
            scores = visibleItems
                .sortedWith(
                    compareByDescending<SheetMusicItem> { it.lastOpenedEpochMillis ?: Long.MIN_VALUE }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.trim() }
                )
                .take(limitPerGroup),
            setlists = setlists.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.trim() })
                .take(limitPerGroup),
            songbooks = songbooks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.trim() })
                .take(limitPerGroup),
        )
    }

    return QuickFindResults(
        scores = rankQuickFind(
            candidates = visibleItems,
            query = normalizedQuery,
            fields = ::librarySearchFields,
            label = SheetMusicItem::title,
        ).take(limitPerGroup),
        setlists = rankQuickFind(
            candidates = setlists,
            query = normalizedQuery,
            fields = { listOf(it.name.trim().lowercase()) },
            label = LibrarySetlist::name,
        ).take(limitPerGroup),
        songbooks = rankQuickFind(
            candidates = songbooks,
            query = normalizedQuery,
            fields = { listOf(it.name.trim().lowercase()) },
            label = LibrarySongbook::name,
        ).take(limitPerGroup),
    )
}

/**
 * Filters and orders [candidates] by how well [query] matches their [fields]. The first field is
 * treated as the primary label, so a title prefix beats a tag prefix. Ties fall back to [label].
 */
fun <T> rankQuickFind(
    candidates: List<T>,
    query: String,
    fields: (T) -> List<String>,
    label: (T) -> String,
): List<T> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) {
        return candidates
    }

    return candidates
        .mapNotNull { candidate ->
            val rank = fields(candidate)
                .mapIndexedNotNull { index, field ->
                    quickFindMatch(field, normalizedQuery)?.let { match -> QuickFindRank(match, index) }
                }
                .minOrNull()
            rank?.let { candidate to it }
        }
        .sortedWith(
            compareBy<Pair<T, QuickFindRank>> { it.second }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { label(it.first).trim() }
        )
        .map { it.first }
}

/** Returns how [query] matches [field], or null when it does not. Both are compared case-insensitively. */
fun quickFindMatch(
    field: String,
    query: String,
): QuickFindMatch? {
    val normalizedField = field.trim().lowercase()
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isEmpty()) {
        return null
    }
    return when {
        normalizedField.startsWith(normalizedQuery) -> QuickFindMatch.PREFIX
        !normalizedField.contains(normalizedQuery) -> null
        normalizedField.hasWordStartingAt(normalizedQuery) -> QuickFindMatch.WORD_START
        else -> QuickFindMatch.SUBSTRING
    }
}

private data class QuickFindRank(
    val match: QuickFindMatch,
    val fieldIndex: Int,
) : Comparable<QuickFindRank> {
    override fun compareTo(other: QuickFindRank): Int =
        compareValuesBy(this, other, QuickFindRank::match, { if (it.fieldIndex == 0) 0 else 1 })
}

private fun String.hasWordStartingAt(query: String): Boolean {
    var index = indexOf(query)
    while (index >= 0) {
        if (index == 0 || !this[index - 1].isLetterOrDigit()) {
            return true
        }
        index = indexOf(query, startIndex = index + 1)
    }
    return false
}
