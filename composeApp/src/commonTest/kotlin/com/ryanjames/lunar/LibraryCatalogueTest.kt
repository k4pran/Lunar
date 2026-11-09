package com.ryanjames.lunar

import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.ui.AlphabetIndexOtherLetter
import com.ryanjames.lunar.ui.CatalogueGrouping
import com.ryanjames.lunar.ui.LibraryBrowseMode
import com.ryanjames.lunar.ui.alphabetIndex
import com.ryanjames.lunar.ui.buildCatalogueGroups
import com.ryanjames.lunar.ui.catalogueGroupingFor
import com.ryanjames.lunar.ui.catalogueIndexAnchors
import com.ryanjames.lunar.ui.catalogueLazyIndexOf
import com.ryanjames.lunar.ui.collectionSwatchSlot
import com.ryanjames.lunar.ui.nextFocusedItemId
import com.ryanjames.lunar.ui.openScoreLabel
import com.ryanjames.lunar.ui.relativeTimeLabel
import com.ryanjames.lunar.ui.scoreResumeLine
import com.ryanjames.lunar.ui.selectionRange
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryCatalogueTest {

    @Test
    fun clickFocusesAndDropsMultiSelectionWhileToggleKeepsFocus() = runBlocking {
        val appState = createTestLunarAppState(this)

        appState.toggleScoreSelection("one")
        appState.toggleScoreSelection("two")
        assertEquals(setOf("one", "two"), appState.selectedScoreIds)
        assertEquals(null, appState.focusedScoreId)

        appState.focusScore("three")
        assertEquals("three", appState.focusedScoreId)
        assertTrue(appState.selectedScoreIds.isEmpty())
        assertEquals(setOf("three"), appState.pickerTargetIds)

        appState.toggleScoreSelection("one")
        assertEquals("three", appState.focusedScoreId)
        assertEquals(setOf("one"), appState.selectedScoreIds)
        assertEquals(setOf("one"), appState.pickerTargetIds)
    }

    @Test
    fun shiftClickSelectsTheRangeFromTheAnchor() = runBlocking {
        val appState = createTestLunarAppState(this)
        val ordered = listOf("a", "b", "c", "d", "e")

        appState.focusScore("b")
        appState.extendScoreSelection("d", ordered)
        assertEquals(setOf("b", "c", "d"), appState.selectedScoreIds)

        appState.toggleScoreSelection("e")
        appState.extendScoreSelection("a", ordered)
        assertEquals(ordered.toSet(), appState.selectedScoreIds)
    }

    @Test
    fun shiftClickWithoutAnchorTogglesTheRow() = runBlocking {
        val appState = createTestLunarAppState(this)

        appState.extendScoreSelection("c", listOf("a", "b", "c"))

        assertEquals(setOf("c"), appState.selectedScoreIds)
    }

    @Test
    fun focusClearsWhenTheFocusedScoreIsFilteredOut() = runBlocking {
        val appState = createTestLunarAppState(this)
        appState.focusScore("one")
        appState.startEditing("one")

        appState.reconcileFocus(setOf("one", "two"))
        assertEquals("one", appState.focusedScoreId)
        assertEquals("one", appState.editingItemId)

        appState.reconcileFocus(setOf("two"))
        assertEquals(null, appState.focusedScoreId)
        assertEquals(null, appState.editingItemId)
    }

    @Test
    fun focusClearsWhenTheFocusedScoreIsDeleted() = runBlocking {
        val item = testSheetMusicItem(id = "moon_river", title = "Moon River")
        val runtime = createTestPlatformRuntime(initialItems = listOf(item))
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.focusScore(item.id)
        appState.requestDelete(item.id)
        appState.confirmDelete()
        repeat(10) {
            if (runtime.repository.library.value.items.isEmpty()) return@repeat
            yield()
        }
        yield()

        assertTrue(runtime.repository.library.value.items.isEmpty())
        assertEquals(null, appState.focusedScoreId)
    }

    @Test
    fun focusClearsWhenTheFocusedScoreIsHiddenButNotForOtherScores() = runBlocking {
        val focused = testSheetMusicItem(id = "focused", title = "Focused")
        val other = testSheetMusicItem(id = "other", title = "Other")
        val runtime = createTestPlatformRuntime(initialItems = listOf(focused, other))
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.focusScore(focused.id)
        appState.hideScore(other.id)
        repeat(10) { yield() }
        assertEquals(focused.id, appState.focusedScoreId)

        appState.hideScore(focused.id)
        repeat(10) { yield() }
        assertEquals(null, appState.focusedScoreId)
    }

    @Test
    fun switchingToSetlistsOrLeavingLibraryClearsFocus() = runBlocking {
        val appState = createTestLunarAppState(this)

        appState.focusScore("one")
        appState.updateBrowseMode(LibraryBrowseMode.BY_COMPOSER)
        assertEquals("one", appState.focusedScoreId)

        appState.selectSection(com.ryanjames.lunar.ui.AppSection.SETLISTS)
        assertEquals(null, appState.focusedScoreId)

        appState.selectSection(com.ryanjames.lunar.ui.AppSection.LIBRARY)
        appState.focusScore("one")
        appState.selectSection(com.ryanjames.lunar.ui.AppSection.SETTINGS)
        assertEquals(null, appState.focusedScoreId)
    }

    @Test
    fun changingFocusClosesTheEditorAndDetailsOfThePreviousScore() = runBlocking {
        val item = testSheetMusicItem(id = "one")
        val runtime = createTestPlatformRuntime(initialItems = listOf(item, testSheetMusicItem(id = "two")))
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.startEditing("one")
        assertEquals("one", appState.focusedScoreId)
        appState.showSetlistPicker()
        assertTrue(appState.setlistPickerVisible)

        appState.focusScore("two")

        assertEquals(null, appState.editingItemId)
        assertFalse(appState.setlistPickerVisible)
    }

    @Test
    fun arrowKeysMoveFocusThroughTheList() {
        val ordered = listOf("a", "b", "c")

        assertEquals("a", nextFocusedItemId(ordered, null, 1))
        assertEquals("b", nextFocusedItemId(ordered, "a", 1))
        assertEquals("c", nextFocusedItemId(ordered, "c", 1))
        assertEquals("a", nextFocusedItemId(ordered, "a", -1))
        assertEquals(null, nextFocusedItemId(emptyList(), "a", 1))
        assertEquals(listOf("b", "c"), selectionRange(ordered, "c", "b"))
        assertTrue(selectionRange(ordered, "missing", "b").isEmpty())
    }

    @Test
    fun alphabetIndexEnablesLettersThatHaveGroups() {
        val entries = alphabetIndex(listOf("Bach", "Brahms", "Chopin", "debussy", "", "3 Pieces"))
        val byLetter = entries.associateBy { it.letter }

        assertEquals(27, entries.size)
        assertEquals('A', entries.first().letter)
        assertEquals(AlphabetIndexOtherLetter, entries.last().letter)
        assertEquals(0, byLetter.getValue('B').targetIndex)
        assertEquals(2, byLetter.getValue('C').targetIndex)
        assertEquals(3, byLetter.getValue('D').targetIndex)
        assertEquals(4, byLetter.getValue(AlphabetIndexOtherLetter).targetIndex)
        assertFalse(byLetter.getValue('A').enabled)
        assertEquals(listOf('B', 'C', 'D', AlphabetIndexOtherLetter), entries.filter { it.enabled }.map { it.letter })
    }

    @Test
    fun catalogueGroupsFollowArrangeByAndSort() {
        assertEquals(CatalogueGrouping.COMPOSER, catalogueGroupingFor(LibraryBrowseMode.BY_COMPOSER, LibrarySortOption.TITLE))
        assertEquals(CatalogueGrouping.COLLECTION, catalogueGroupingFor(LibraryBrowseMode.BY_COLLECTION, LibrarySortOption.DATE_ADDED))
        assertEquals(CatalogueGrouping.COMPOSER, catalogueGroupingFor(LibraryBrowseMode.ALL, LibrarySortOption.COMPOSER))
        assertEquals(CatalogueGrouping.NONE, catalogueGroupingFor(LibraryBrowseMode.ALL, LibrarySortOption.TITLE))
        assertEquals(CatalogueGrouping.NONE, catalogueGroupingFor(LibraryBrowseMode.HIDDEN, LibrarySortOption.DATE_ADDED))

        val items = listOf(
            testSheetMusicItem(id = "nocturne", title = "Nocturne", composer = "chopin"),
            testSheetMusicItem(id = "untitled", title = "Untitled", composer = " "),
            testSheetMusicItem(id = "prelude", title = "Prelude", composer = "Bach"),
            testSheetMusicItem(id = "ballade", title = "Ballade", composer = "Chopin"),
        )
        val groups = buildCatalogueGroups(items, CatalogueGrouping.COMPOSER, LibrarySortOption.TITLE, SortDirection.ASCENDING)

        assertEquals(listOf("Bach", "chopin", "No composer"), groups.map { it.header })
        assertEquals(listOf("nocturne", "ballade"), groups[1].items.map { it.id })
        assertEquals("", groups.last().indexKey)

        val descending = buildCatalogueGroups(items, CatalogueGrouping.COMPOSER, LibrarySortOption.COMPOSER, SortDirection.DESCENDING)
        assertEquals(listOf("chopin", "Bach", "No composer"), descending.map { it.header })

        val flat = buildCatalogueGroups(items, CatalogueGrouping.NONE, LibrarySortOption.TITLE, SortDirection.ASCENDING)
        assertEquals(1, flat.size)
        assertEquals(null, flat.single().header)
        assertTrue(buildCatalogueGroups(emptyList(), CatalogueGrouping.NONE, LibrarySortOption.TITLE, SortDirection.ASCENDING).isEmpty())
    }

    @Test
    fun indexAnchorsPointAtHeadersOrTitleRows() {
        val items = listOf(
            testSheetMusicItem(id = "a1", title = "Air", composer = "Bach"),
            testSheetMusicItem(id = "a2", title = "Bourree", composer = "Bach"),
            testSheetMusicItem(id = "c1", title = "Etude", composer = "Chopin"),
        )
        val grouped = buildCatalogueGroups(items, CatalogueGrouping.COMPOSER, LibrarySortOption.TITLE, SortDirection.ASCENDING)

        assertEquals(
            listOf("Bach" to 1, "Chopin" to 4),
            catalogueIndexAnchors(grouped, LibrarySortOption.TITLE, leadingItemCount = 1),
        )

        val flat = buildCatalogueGroups(items, CatalogueGrouping.NONE, LibrarySortOption.TITLE, SortDirection.ASCENDING)
        assertEquals(
            listOf("Air" to 0, "Bourree" to 1, "Etude" to 2),
            catalogueIndexAnchors(flat, LibrarySortOption.TITLE),
        )
        assertTrue(catalogueIndexAnchors(flat, LibrarySortOption.DATE_ADDED).isEmpty())
    }

    @Test
    fun lazyIndexOfCountsTheLeadingStripAndGroupHeaders() {
        val items = listOf(
            testSheetMusicItem(id = "a1", title = "Air", composer = "Bach"),
            testSheetMusicItem(id = "a2", title = "Bourree", composer = "Bach"),
            testSheetMusicItem(id = "c1", title = "Etude", composer = "Chopin"),
        )
        val grouped = buildCatalogueGroups(items, CatalogueGrouping.COMPOSER, LibrarySortOption.TITLE, SortDirection.ASCENDING)

        // [jump back in] [BACH] Air Bourree [CHOPIN] Etude
        assertEquals(2, catalogueLazyIndexOf(grouped, "a1", leadingItemCount = 1))
        assertEquals(3, catalogueLazyIndexOf(grouped, "a2", leadingItemCount = 1))
        assertEquals(5, catalogueLazyIndexOf(grouped, "c1", leadingItemCount = 1))

        val flat = buildCatalogueGroups(items, CatalogueGrouping.NONE, LibrarySortOption.TITLE, SortDirection.ASCENDING)
        assertEquals(2, catalogueLazyIndexOf(flat, "c1"))
        assertEquals(null, catalogueLazyIndexOf(flat, "missing"))
        assertEquals(null, catalogueLazyIndexOf(flat, null))
    }

    @Test
    fun resumeLineAndOpenLabelDescribeWhereTheScoreWasLeft() {
        val minute = 60_000L
        val now = 100L * 24 * 60 * minute
        val unopened = testSheetMusicItem(id = "fresh", pageCount = 4)
        val opened = unopened.copy(lastViewedPage = 2, lastOpenedEpochMillis = now - 3 * 60 * minute)

        assertEquals("Not opened yet, 4 pages", scoreResumeLine(unopened, now))
        assertEquals("Open", openScoreLabel(unopened))
        assertEquals("Last on page 3 of 4, opened 3 hours ago", scoreResumeLine(opened, now))
        assertEquals("Open at page 3", openScoreLabel(opened))
        assertEquals("Last on page 3, opened 3 hours ago", scoreResumeLine(opened.copy(pageCount = null), now))
    }

    @Test
    fun relativeTimeLabelsRoundDown() {
        val minute = 60_000L
        val day = 24 * 60 * minute
        val now = 1_000L * day

        assertEquals("just now", relativeTimeLabel(now - 10_000L, now))
        assertEquals("just now", relativeTimeLabel(now + minute, now))
        assertEquals("1 minute ago", relativeTimeLabel(now - minute, now))
        assertEquals("59 minutes ago", relativeTimeLabel(now - 59 * minute, now))
        assertEquals("1 hour ago", relativeTimeLabel(now - 60 * minute, now))
        assertEquals("yesterday", relativeTimeLabel(now - day, now))
        assertEquals("6 days ago", relativeTimeLabel(now - 6 * day, now))
        assertEquals("2 weeks ago", relativeTimeLabel(now - 14 * day, now))
        assertEquals("3 months ago", relativeTimeLabel(now - 95 * day, now))
        assertEquals("2 years ago", relativeTimeLabel(now - 800 * day, now))
    }

    @Test
    fun collectionSwatchSlotIsStableAndInRange() {
        assertEquals(collectionSwatchSlot("Standards", 5), collectionSwatchSlot(" standards ", 5))
        val slots = listOf("Standards", "Recital", "Jazz", "Etudes", "Hymns", "Pop").map { collectionSwatchSlot(it, 5) }
        assertTrue(slots.all { it in 0 until 5 })
        assertTrue(slots.toSet().size > 1)
    }
}
