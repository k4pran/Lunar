package com.ryanjames.lunar

import com.ryanjames.lunar.library.model.LibraryGroup
import com.ryanjames.lunar.library.model.LibraryQuery
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.library.model.UnknownComposerGroupName
import com.ryanjames.lunar.library.model.alphabetIndex
import com.ryanjames.lunar.library.model.applyLibraryQuery
import com.ryanjames.lunar.library.model.groupForCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryGroupingTest {
    private val items = listOf(
        item("a", title = "Air", composer = "Bach"),
        item("b", title = "Bolero", composer = "Ravel"),
        item("c", title = "Gavotte", composer = "bach"),
        item("d", title = "42nd Street", composer = null),
        item("e", title = "Berceuse", composer = "Chopin"),
    )

    @Test
    fun composerSortGroupsByComposerCaseInsensitively() {
        val sorted = items.applyLibraryQuery(LibraryQuery(sortOption = LibrarySortOption.COMPOSER, sortDirection = SortDirection.ASCENDING))

        val groups = sorted.groupForCatalogue(LibrarySortOption.COMPOSER)

        assertEquals(listOf("Bach", "Chopin", "Ravel", UnknownComposerGroupName), groups.map { it.name })
        assertEquals(listOf("a", "c"), groups.first().items.map { it.id })
    }

    @Test
    fun titleSortGroupsByFirstLetterAndKeepsDirection() {
        val sorted = items.applyLibraryQuery(LibraryQuery(sortOption = LibrarySortOption.TITLE, sortDirection = SortDirection.DESCENDING))

        val groups = sorted.groupForCatalogue(LibrarySortOption.TITLE)

        assertEquals(listOf("G", "B", "A", "#"), groups.map { it.name })
        assertEquals(listOf("b", "e"), groups[1].items.map { it.id })
    }

    @Test
    fun dateSortsStayFlat() {
        val groups = items.groupForCatalogue(LibrarySortOption.DATE_ADDED)

        assertEquals(1, groups.size)
        assertEquals("", groups.single().name)
        assertEquals(items, groups.single().items)
        assertTrue(emptyList<SheetMusicItem>().groupForCatalogue(LibrarySortOption.TITLE).isEmpty())
    }

    @Test
    fun alphabetIndexPointsAtFirstGroupForEachLetter() {
        val groups = listOf(
            LibraryGroup("Bach", emptyList()),
            LibraryGroup("Brahms", emptyList()),
            LibraryGroup("chopin", emptyList()),
            LibraryGroup("3 Pieces", emptyList()),
            LibraryGroup("", emptyList()),
        )

        assertEquals(mapOf('B' to 0, 'C' to 2, '#' to 3), alphabetIndex(groups))
        assertTrue(alphabetIndex(listOf(LibraryGroup("", emptyList()))).isEmpty())
    }

    private fun item(id: String, title: String, composer: String?): SheetMusicItem = SheetMusicItem(
        id = id,
        title = title,
        composer = composer,
        document = PdfDocumentReference(storedPath = "/scores/$id.pdf", originalFileName = "$id.pdf"),
        dateAddedEpochMillis = 1L,
    )
}
