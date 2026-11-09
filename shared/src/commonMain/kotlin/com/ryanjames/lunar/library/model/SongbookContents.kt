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

/** Table of contents for a songbook, skipping its cover page when it has one. */
fun LibrarySongbook.contents(itemsById: Map<String, SheetMusicItem>): List<SongbookContentsEntry> {
    val items = itemIds.map(itemsById::get)
    val startPages = songbookStartPages(
        pageCounts = items.map { it?.pageCount },
        firstPage = if (hasCover) 2 else 1,
    )
    return itemIds.mapIndexed { index, itemId ->
        SongbookContentsEntry(
            itemId = itemId,
            item = items[index],
            startPage = startPages[index],
        )
    }
}
