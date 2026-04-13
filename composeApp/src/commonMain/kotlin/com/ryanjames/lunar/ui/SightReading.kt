package com.ryanjames.lunar.ui

import com.ryanjames.lunar.library.model.SheetMusicItem
import kotlin.random.Random

data class TemporarySetlistSession(
    val title: String = DEFAULT_TEMPORARY_SETLIST_NAME,
    val itemIds: List<String>,
    val createdAtEpochMillis: Long,
)

internal fun buildRandomSightReadingSelection(
    items: List<SheetMusicItem>,
    requestedCount: Int,
    random: Random = Random.Default,
): List<SheetMusicItem> {
    if (items.isEmpty()) {
        return emptyList()
    }

    return items
        .shuffled(random)
        .take(requestedCount.coerceIn(1, items.size))
}

const val DEFAULT_TEMPORARY_SETLIST_NAME = "Sight Reading Mix"
