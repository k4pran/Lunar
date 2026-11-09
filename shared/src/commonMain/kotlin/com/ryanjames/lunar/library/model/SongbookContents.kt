package com.ryanjames.lunar.library.model

/** One line of a songbook's table of contents. [item] is null when the score left the library. */
data class SongbookContentsEntry(
    val itemId: String,
    val item: SheetMusicItem?,
    val startPage: Int?,
)

/**
 * Computes 1-based start pages from cumulative page counts. Once a count is unknown, every later
 * start page is unknown too, because the builder appends PDFs back to back.
 */
fun songbookStartPages(
    pageCounts: List<Int?>,
    firstPage: Int = 1,
): List<Int?> {
    var nextPage: Int? = firstPage
    return pageCounts.map { pageCount ->
        val startPage = nextPage
        nextPage = if (startPage != null && pageCount != null && pageCount > 0) {
            startPage + pageCount
        } else {
            null
        }
        startPage
    }
}

/**
 * Works out whether the bound PDF starts with a cover page. Songbooks saved before the cover was
 * recorded are inferred from their page count: one page more than the bound scores means a
 * cover. Returns null when that can't be told.
 */
fun LibrarySongbook.resolveHasCover(itemsById: Map<String, SheetMusicItem>): Boolean? {
    hasCover?.let { return it }
    val total = boundScorePageTotal(itemsById) ?: return null
    return when (pageCount) {
        total -> false
        total + 1 -> true
        else -> null
    }
}

/**
 * Table of contents for a songbook in bound order, skipping its cover page when it has one.
 * Start pages are left unknown when the bound page sequence can't be reconstructed faithfully:
 * for older songbooks whose cover or bound order wasn't recorded unless the page counts add up,
 * and for any songbook whose score page counts disagree with the bound PDF.
 */
fun LibrarySongbook.contents(itemsById: Map<String, SheetMusicItem>): List<SongbookContentsEntry> {
    val sequence = boundItemIds ?: itemIds
    val items = sequence.map(itemsById::get)
    val cover = resolveHasCover(itemsById)
    val total = boundScorePageTotal(itemsById)
    val pagesAddUp = total != null && pageCount != null && pageCount == total + if (cover == true) 1 else 0
    val pagesContradict = total != null && pageCount != null && !pagesAddUp
    val trusted = cover != null && !pagesContradict && (boundItemIds != null || pagesAddUp)
    val startPages = if (trusted) {
        songbookStartPages(
            pageCounts = items.map { it?.pageCount },
            firstPage = if (cover == true) 2 else 1,
        )
    } else {
        sequence.map { null }
    }
    return sequence.mapIndexed { index, itemId ->
        SongbookContentsEntry(
            itemId = itemId,
            item = items[index],
            startPage = startPages[index],
        )
    }
}

/** Sum of the bound scores' page counts, or null when any of them is unknown. */
private fun LibrarySongbook.boundScorePageTotal(itemsById: Map<String, SheetMusicItem>): Int? =
    (boundItemIds ?: itemIds).fold(0 as Int?) { total, itemId ->
        val count = itemsById[itemId]?.pageCount?.takeIf { it > 0 }
        if (total == null || count == null) null else total + count
    }
