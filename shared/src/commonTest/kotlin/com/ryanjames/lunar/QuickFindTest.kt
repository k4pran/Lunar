package com.ryanjames.lunar

import com.ryanjames.lunar.library.model.LibraryQuery
import com.ryanjames.lunar.library.model.LibrarySetlist
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.library.model.QuickFindMatch
import com.ryanjames.lunar.library.model.SheetMusicItem
import com.ryanjames.lunar.library.model.applyLibraryQuery
import com.ryanjames.lunar.library.model.quickFind
import com.ryanjames.lunar.library.model.quickFindMatch
import com.ryanjames.lunar.library.model.rankQuickFind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickFindTest {
    @Test
    fun quickFindMatchClassifiesPrefixWordStartAndSubstring() {
        assertEquals(QuickFindMatch.PREFIX, quickFindMatch("Moon River", "moo"))
        assertEquals(QuickFindMatch.WORD_START, quickFindMatch("Moon River", "riv"))
        assertEquals(QuickFindMatch.WORD_START, quickFindMatch("Clair-de-lune", "lun"))
        assertEquals(QuickFindMatch.SUBSTRING, quickFindMatch("Moon River", "iver"))
        assertNull(quickFindMatch("Moon River", "sonata"))
        assertNull(quickFindMatch("Moon River", "   "))
    }

    @Test
    fun quickFindRanksPrefixThenWordStartThenSubstring() {
        val items = listOf(
            item("substring", "Fantasmoon"),
            item("word_start", "Blue Moon"),
            item("prefix", "Moonlight Sonata"),
        )

        val results = quickFind(items, emptyList(), emptyList(), query = "moon")

        assertEquals(listOf("prefix", "word_start", "substring"), results.scores.map(SheetMusicItem::id))
    }

    @Test
    fun quickFindPrefersTitleMatchesOverOtherFieldsAtTheSameMatchQuality() {
        val items = listOf(
            item("tagged", "Etude", tags = listOf("Nocturne")),
            item("titled", "Nocturne in E flat"),
        )

        val results = quickFind(items, emptyList(), emptyList(), query = "noc")

        assertEquals(listOf("titled", "tagged"), results.scores.map(SheetMusicItem::id))
    }

    @Test
    fun quickFindBreaksTiesAlphabetically() {
        val items = listOf(item("b", "Ballade No. 2"), item("a", "ballade No. 1"))

        val results = quickFind(items, emptyList(), emptyList(), query = "ballade")

        assertEquals(listOf("a", "b"), results.scores.map(SheetMusicItem::id))
    }

    @Test
    fun quickFindExcludesHiddenScores() {
        val items = listOf(
            item("visible", "Moon River"),
            item("hidden", "Moonlight", isHidden = true),
        )

        assertEquals(listOf("visible"), quickFind(items, emptyList(), emptyList(), "moon").scores.map { it.id })
        assertEquals(listOf("visible"), quickFind(items, emptyList(), emptyList(), "").scores.map { it.id })
    }

    @Test
    fun quickFindEmptyQueryListsRecentScoresAndAlphabeticalCollections() {
        val items = listOf(
            item("never", "Alpha"),
            item("old", "Beta", lastOpened = 10L),
            item("recent", "Gamma", lastOpened = 20L),
        )
        val setlists = listOf(setlist("s2", "Wedding"), setlist("s1", "audition"))
        val songbooks = listOf(songbook("b2", "Jazz"), songbook("b1", "Classics"))

        val results = quickFind(items, setlists, songbooks, query = "  ", limitPerGroup = 2)

        assertEquals(listOf("recent", "old"), results.scores.map(SheetMusicItem::id))
        assertEquals(listOf("s1", "s2"), results.setlists.map(LibrarySetlist::id))
        assertEquals(listOf("b1", "b2"), results.songbooks.map(LibrarySongbook::id))
    }

    @Test
    fun quickFindMatchesSetlistsAndSongbooksByName() {
        val setlists = listOf(setlist("gig", "Friday gig"), setlist("practice", "Practice"))
        val songbooks = listOf(songbook("gig_book", "Gig standards"), songbook("other", "Hymns"))

        val results = quickFind(emptyList(), setlists, songbooks, query = "gig")

        assertEquals(listOf("gig"), results.setlists.map(LibrarySetlist::id))
        assertEquals(listOf("gig_book"), results.songbooks.map(LibrarySongbook::id))
        assertTrue(quickFind(emptyList(), setlists, songbooks, query = "zzz").isEmpty)
    }

    @Test
    fun quickFindMatchesTheSameScoresAsLibrarySearch() {
        val items = listOf(
            item("title", "Clair de Lune"),
            item("composer", "Arabesque", composer = "Claude Debussy"),
            item("collection", "Prelude", collection = "Lunar Favourites"),
            item("tag", "Waltz", tags = listOf("lunatic")),
            item("none", "Gymnopedie", composer = "Satie"),
            item("hidden", "Lune", isHidden = true),
        )

        listOf("lun", "DEBUSSY", "  favourites ", "e", "satie", "xyz").forEach { query ->
            val librarySearchIds = items.applyLibraryQuery(LibraryQuery(searchText = query)).map { it.id }.toSet()
            val quickFindIds = quickFind(items, emptyList(), emptyList(), query).scores.map { it.id }.toSet()
            assertEquals(librarySearchIds, quickFindIds, "query '$query'")
        }
    }

    @Test
    fun rankQuickFindKeepsOrderForEmptyQueryAndRanksArbitraryCandidates() {
        val actions = listOf("Switch skin", "Open random score", "Start metronome")

        assertEquals(actions, rankQuickFind(actions, "", { listOf(it) }, { it }))
        assertEquals(
            listOf("Start metronome", "Switch skin", "Open random score"),
            rankQuickFind(actions, "s", { listOf(it) }, { it }),
        )
    }

    private fun item(
        id: String,
        title: String,
        composer: String? = null,
        collection: String? = null,
        tags: List<String> = emptyList(),
        isHidden: Boolean = false,
        lastOpened: Long? = null,
    ) = SheetMusicItem(
        id = id,
        title = title,
        composer = composer,
        collection = collection,
        tags = tags,
        isHidden = isHidden,
        document = PdfDocumentReference(storedPath = "/scores/$id.pdf", originalFileName = "$id.pdf"),
        dateAddedEpochMillis = 1L,
        lastOpenedEpochMillis = lastOpened,
    )

    private fun setlist(id: String, name: String) = LibrarySetlist(
        id = id,
        name = name,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )

    private fun songbook(id: String, name: String) = LibrarySongbook(
        id = id,
        name = name,
        document = PdfDocumentReference(storedPath = "/songbooks/$id.pdf", originalFileName = "$id.pdf"),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
    )
}
