package com.ryanjames.lunar

import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.ui.ScoreThumbnailCache
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScoreThumbnailCacheTest {

    private val String.fakeItem: SheetMusicItem
        get() = SheetMusicItem(
            id = this,
            title = this,
            document = PdfDocumentReference(
                storedPath = "/docs/$this.pdf",
                originalFileName = "$this.pdf",
            ),
            dateAddedEpochMillis = 0L,
        )

    @Test
    fun missRendersAndCachesValue() = runBlocking {
        var callCount = 0
        val cache = ScoreThumbnailCache<String>(
            renderer = { item ->
                callCount += 1
                "thumb-${item.id}"
            },
            maxSize = 8,
        )

        val item = "a".fakeItem
        assertEquals("thumb-a", cache.get(item))
        assertEquals("thumb-a", cache.get(item))

        assertEquals(1, callCount, "renderer should only be invoked once for a cached item")
        assertEquals(1, cache.size)
    }

    @Test
    fun rendererNullIsNotCachedAndMissRepeats() = runBlocking {
        var callCount = 0
        val cache = ScoreThumbnailCache<String>(
            renderer = { item ->
                callCount += 1
                null
            },
            maxSize = 8,
        )

        val item = "a".fakeItem
        assertNull(cache.get(item))
        assertNull(cache.get(item))

        assertEquals(2, callCount, "null results should not be cached")
        assertEquals(0, cache.size)
    }

    @Test
    fun lruEvictsLeastRecentlyUsedFirst() = runBlocking {
        var callCount = 0
        val cache = ScoreThumbnailCache<String>(
            renderer = { item ->
                callCount += 1
                item.id
            },
            maxSize = 2,
        )

        val a = "a".fakeItem
        val b = "b".fakeItem
        val c = "c".fakeItem

        cache.get(a) // render a; cache order [a]
        cache.get(b) // render b; cache order [a, b]
        cache.get(a) // hit; cache order [b, a]
        cache.get(c) // render c; evict least-recently-used (b); cache order [a, c]

        assertEquals(2, cache.size)
        assertEquals(3, callCount, "a, b, c rendered once each (a second access was a cache hit)")

        cache.get(b) // b was evicted -> re-render
        assertEquals(4, callCount, "re-accessing an evicted item re-renders it")
        assertEquals(2, cache.size) // a evicted to make room
    }

    @Test
    fun clearKeyRemovesCachedEntry() = runBlocking {
        var callCount = 0
        val cache = ScoreThumbnailCache<String>(
            renderer = { item ->
                callCount += 1
                item.id
            },
            maxSize = 8,
        )

        val item = "a".fakeItem
        cache.get(item)
        assertEquals(1, callCount)
        assertEquals(1, cache.size)

        cache.clearKey("a")
        assertEquals(0, cache.size)

        cache.get(item) // re-render after explicit clear
        assertEquals(2, callCount)
    }

    @Test
    fun clearRemovesAllEntries() = runBlocking {
        val cache = ScoreThumbnailCache<String>(
            renderer = { item -> item.id },
            maxSize = 8,
        )

        cache.get("a".fakeItem)
        cache.get("b".fakeItem)
        assertEquals(2, cache.size)

        cache.clear()
        assertEquals(0, cache.size)
    }
}
