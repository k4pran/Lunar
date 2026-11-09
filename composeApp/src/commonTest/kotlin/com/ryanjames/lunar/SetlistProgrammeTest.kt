package com.ryanjames.lunar

import com.ryanjames.lunar.platform.SelectedCoverImage
import com.ryanjames.lunar.platform.SongbookBuildResult
import com.ryanjames.lunar.platform.SongbookPdfBuilder
import com.ryanjames.lunar.ui.LibraryBrowseMode
import com.ryanjames.lunar.ui.TEMPORARY_SETLIST_ID
import com.ryanjames.lunar.ui.ViewerTarget
import com.ryanjames.lunar.ui.clothSwatchIndex
import com.ryanjames.lunar.ui.mergeVisibleSetlistOrder
import com.ryanjames.lunar.ui.moved
import com.ryanjames.lunar.ui.next
import com.ryanjames.lunar.ui.setlistSummaryLabel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetlistProgrammeTest {

    @Test
    fun setlistViewerTargetMovesForwardAndStopsAtTheEnd() {
        val first = ViewerTarget.Setlist(setlistId = "set", index = 0)

        assertEquals(ViewerTarget.Setlist("set", 1), first.next(itemCount = 3))
        assertEquals(ViewerTarget.Setlist("set", 2), first.next(3)?.next(3))
        assertNull(ViewerTarget.Setlist("set", 2).next(itemCount = 3))
        assertNull(first.next(itemCount = 1))
        assertNull(first.next(itemCount = 0))
    }

    @Test
    fun mergingVisibleOrderKeepsHiddenScoresInTheirSlots() {
        assertEquals(
            listOf("c", "hidden", "a", "b"),
            mergeVisibleSetlistOrder(
                allItemIds = listOf("a", "hidden", "b", "c"),
                visibleItemIds = listOf("c", "a", "b"),
            ),
        )
        assertEquals(listOf("a", "b"), mergeVisibleSetlistOrder(listOf("a", "b"), emptyList()))
    }

    @Test
    fun movedListShiftsOneElement() {
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 2))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
        assertEquals(listOf("a", "b"), listOf("a", "b").moved(0, 5))
    }

    @Test
    fun setlistSummaryCountsPiecesAndKnownPages() {
        assertEquals("0 pieces", setlistSummaryLabel(emptyList()))
        assertEquals(
            "2 pieces · 6 pages",
            setlistSummaryLabel(
                listOf(
                    testSheetMusicItem(id = "a", pageCount = 2),
                    testSheetMusicItem(id = "b", pageCount = 4),
                )
            ),
        )
        assertEquals(
            "2 pieces · 2+ pages",
            setlistSummaryLabel(
                listOf(
                    testSheetMusicItem(id = "a", pageCount = 2),
                    testSheetMusicItem(id = "b"),
                )
            ),
        )
    }

    @Test
    fun clothSwatchIsStablePerName() {
        assertEquals(clothSwatchIndex("Standards", 6), clothSwatchIndex(" standards ", 6))
        val slots = listOf("Standards", "Recital", "Hymns", "Jazz", "Etudes", "Carols")
            .map { clothSwatchIndex(it, 6) }
        assertTrue(slots.all { it in 0 until 6 })
        assertTrue(slots.toSet().size > 1)
    }

    @Test
    fun startTheSetStepsThroughVisiblePiecesAndReturnsToProgramme() = runBlocking {
        val items = listOf(
            testSheetMusicItem(id = "one", title = "One"),
            testSheetMusicItem(id = "hidden", title = "Hidden", isHidden = true),
            testSheetMusicItem(id = "two", title = "Two"),
        )
        val setlist = testLibrarySetlist(
            id = "set",
            name = "Recital",
            itemIds = items.map { it.id },
        )
        val runtime = createTestPlatformRuntime(initialItems = items, initialSetlists = listOf(setlist))
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.startSetlist(setlist.id)

        assertEquals(ViewerTarget.Setlist(setlist.id, 0), appState.previewTarget)
        assertEquals(setlist.id, appState.selectedSetlistId)
        assertTrue(appState.hasNextInSetlist(appState.previewTarget))

        appState.openFullscreen(appState.previewTarget!!)
        appState.nextInSetlist()

        assertEquals(ViewerTarget.Setlist(setlist.id, 1), appState.previewTarget)
        assertEquals(ViewerTarget.Setlist(setlist.id, 1), appState.fullscreenTarget)
        assertEquals("two", appState.setlistItems(setlist.id)[1].id)
        assertFalse(appState.hasNextInSetlist(appState.previewTarget))

        appState.nextInSetlist()
        assertEquals(ViewerTarget.Setlist(setlist.id, 1), appState.previewTarget)
        assertEquals("That was the last piece in the set.", appState.bannerMessage)

        appState.closeFullscreen()
        appState.updateBrowseMode(LibraryBrowseMode.ALL)
        appState.closePreview()

        assertNull(appState.previewTarget)
        assertEquals(LibraryBrowseMode.SETLISTS, appState.browseMode)
        assertEquals(setlist.id, appState.selectedSetlistId)
    }

    @Test
    fun startingAnEmptySetlistShowsABanner() = runBlocking {
        val runtime = createTestPlatformRuntime(
            initialSetlists = listOf(testLibrarySetlist(id = "empty", name = "Empty", itemIds = emptyList())),
        )
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.startSetlist("empty")

        assertNull(appState.previewTarget)
        assertEquals("Add scores to this setlist before starting it.", appState.bannerMessage)
    }

    @Test
    fun reorderingAProgrammePersistsThroughTheRepository() = runBlocking {
        val items = listOf(
            testSheetMusicItem(id = "a"),
            testSheetMusicItem(id = "hidden", isHidden = true),
            testSheetMusicItem(id = "b"),
        )
        val runtime = createTestPlatformRuntime(
            initialItems = items,
            initialSetlists = listOf(testLibrarySetlist(id = "set", name = "Set", itemIds = items.map { it.id })),
        )
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.reorderSetlist("set", listOf("b", "a"))
        waitUntil { runtime.repository.getSetlist("set")?.itemIds == listOf("b", "hidden", "a") }

        appState.removeFromSetlist("set", "b")
        waitUntil { runtime.repository.getSetlist("set")?.itemIds == listOf("hidden", "a") }

        appState.renameSetlist("set", "  Encore  ")
        waitUntil { runtime.repository.getSetlist("set")?.name == "Encore" }
    }

    @Test
    fun randomMixCanBeReorderedAndPlayedLikeASetlist() = runBlocking {
        val items = listOf(testSheetMusicItem(id = "a"), testSheetMusicItem(id = "b"))
        val runtime = createTestPlatformRuntime(initialItems = items)
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.createTemporaryRandomSetlist(items = items, requestedCount = 2)
        val shuffled = appState.temporarySetlist!!.itemIds
        appState.reorderSetlist(TEMPORARY_SETLIST_ID, shuffled.reversed())
        assertEquals(shuffled.reversed(), appState.temporarySetlist!!.itemIds)

        appState.startSetlist(TEMPORARY_SETLIST_ID)
        assertEquals(ViewerTarget.Setlist(TEMPORARY_SETLIST_ID, 0), appState.previewTarget)
        assertTrue(appState.temporarySetlistOpen)

        appState.discardTemporarySetlist()
        assertNull(appState.previewTarget)
        assertNull(appState.temporarySetlist)
    }

    @Test
    fun changingASongbookCoverRebuildsItFromItsScores() = runBlocking {
        val items = listOf(testSheetMusicItem(id = "a"), testSheetMusicItem(id = "b"))
        val builds = mutableListOf<Pair<List<String>, SelectedCoverImage?>>()
        val runtime = createTestPlatformRuntime(
            initialItems = items,
            initialSongbooks = listOf(testLibrarySongbook(id = "book", name = "Book", itemIds = listOf("a", "b"))),
            songbookCreationSupported = true,
            songbookCoverImageSupported = true,
            songbookBuilder = object : SongbookPdfBuilder {
                override suspend fun buildSongbook(
                    songbookName: String,
                    appendedDocumentPaths: List<String>,
                    existingSongbookPath: String?,
                    coverImage: SelectedCoverImage?,
                ): SongbookBuildResult {
                    builds += appendedDocumentPaths to coverImage
                    return SongbookBuildResult(
                        document = com.ryanjames.lunar.library.model.PdfDocumentReference(
                            storedPath = "/songbooks/rebuilt.pdf",
                            originalFileName = "Book.pdf",
                        ),
                        pageCount = 5,
                    )
                }
            },
            coverImagePicker = object : com.ryanjames.lunar.platform.CoverImagePicker {
                override suspend fun pickCoverImage(): SelectedCoverImage =
                    SelectedCoverImage(displayName = "cover.png", bytes = byteArrayOf(1))
            },
        )
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.changeSongbookCover("book")
        waitUntil { runtime.repository.getSongbook("book")?.hasCover == true }

        val rebuilt = runtime.repository.getSongbook("book")!!
        assertEquals("/songbooks/rebuilt.pdf", rebuilt.document.storedPath)
        assertEquals(5, rebuilt.pageCount)
        assertEquals(listOf("/scores/a.pdf", "/scores/b.pdf"), builds.single().first)
        assertEquals("cover.png", builds.single().second?.displayName)
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            yield()
        }
        assertTrue(condition(), "Condition was not met in time.")
    }
}
