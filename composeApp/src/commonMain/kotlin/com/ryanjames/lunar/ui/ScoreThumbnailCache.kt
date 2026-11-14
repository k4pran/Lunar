package com.ryanjames.lunar.ui

import com.ryanjames.lunar.library.model.SheetMusicItem
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

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
 * A renderer failure is treated like a miss (nothing cached, `null` returned)
 * so callers fall back to a placeholder instead of crashing.
 */
class ScoreThumbnailCache<T>(
    private val renderer: suspend (SheetMusicItem) -> T?,
    private val maxSize: Int = 128,
) {
    // Insertion-ordered; a hit is re-inserted so the first key is always the least recently used.
    private val cache = LinkedHashMap<String, T>()
    private val lock = SynchronizedObject()

    suspend fun get(item: SheetMusicItem): T? =
        synchronized(lock) { touch(item.id) } ?: load(item)

    private suspend fun load(item: SheetMusicItem): T? {
        val cached = synchronized(lock) { touch(item.id) }
        if (cached != null) return cached

        val value = try {
            renderer(item)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        } ?: return null
        synchronized(lock) {
            cache[item.id] = value
            while (cache.size > maxSize) {
                cache.remove(cache.keys.first())
            }
        }
        return value
    }

    fun clearKey(id: String) {
        synchronized(lock) { cache.remove(id) }
    }

    fun clear() {
        synchronized(lock) { cache.clear() }
    }

    val size: Int get() = synchronized(lock) { cache.size }

    private fun touch(id: String): T? = cache.remove(id)?.also { cache[id] = it }
}
