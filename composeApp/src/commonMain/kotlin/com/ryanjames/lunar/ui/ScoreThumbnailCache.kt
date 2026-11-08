package com.ryanjames.lunar.ui

import com.ryanjames.lunar.library.model.SheetMusicItem

const val ThumbnailTargetWidthPx = 256

/**
 * Lazily renders and caches the first page of a score's PDF as a thumbnail.
 *
 * The cache is intentionally generic over the rendered value [T] so the
 * LRU behaviour (hit/miss, eviction, clearing) can be unit-tested in common
 * tests with a lightweight sentinel value, independent of platform
 * [ImageBitmap] construction. In the app, [T] is
 * `androidx.compose.ui.graphics.ImageBitmap`, produced by
 * [PdfPageRenderer.renderPage] with [ThumbnailTargetWidthPx].
 *
 * Rendering happens outside the cache lock so a slow PDF decode never blocks
 * other cache readers; concurrent misses for the same id may render twice,
 * which is acceptable because the list only lazily composes visible cards.
 */
class ScoreThumbnailCache<T>(
    private val renderer: suspend (SheetMusicItem) -> T?,
    private val maxSize: Int = 128,
) {
    private val cache = LinkedHashMap<String, T>(0, 0.75f, true)

    suspend fun get(item: SheetMusicItem): T? =
        synchronized(cache) { cache[item.id] } ?: load(item)

    private suspend fun load(item: SheetMusicItem): T? {
        val cached = synchronized(cache) { cache[item.id] }
        if (cached != null) return cached

        val value = renderer(item) ?: return null
        synchronized(cache) {
            cache[item.id] = value
            while (cache.size > maxSize) {
                cache.remove(cache.keys.first())
            }
        }
        return value
    }

    fun clearKey(id: String) {
        synchronized(cache) { cache.remove(id) }
    }

    fun clear() {
        synchronized(cache) { cache.clear() }
    }

    val size: Int get() = synchronized(cache) { cache.size }
}
