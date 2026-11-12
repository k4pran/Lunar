package com.ryanjames.lunar

import com.ryanjames.lunar.platform.SelectedCoverImage
import com.ryanjames.lunar.platform.SongbookBuildResult
import com.ryanjames.lunar.platform.SongbookPdfBuilder
import com.ryanjames.lunar.ui.AppSection
import com.ryanjames.lunar.ui.TEMPORARY_SETLIST_ID
import com.ryanjames.lunar.ui.ViewerTarget
import com.ryanjames.lunar.ui.clothSwatchIndex
import com.ryanjames.lunar.ui.mergeVisibleSetlistOrder
import com.ryanjames.lunar.ui.moved
import com.ryanjames.lunar.ui.next
import com.ryanjames.lunar.ui.setlistSummaryLabel
import kotlinx.coroutines.CompletableDeferred
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
            "2 pieces \u00B7 6 pages",
            setlistSummaryLabel(
                listOf(
                    testSheetMusicItem(id = "a", pageCount = 2),
                    testSheetMusicItem(id = "b", pageCount = 4),
                )
            ),
        )
        assertEquals(
            "2 pieces \u00B7 2+ pages",
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

        assertEquals(ViewerTarget.Setlist(setlist.id, 0), appState.fullscreenTarget)
        assertEquals(setlist.id, appState.selectedSetlistId)
        assertTrue(appState.hasNextInSetlist(appState.fullscreenTarget))

        appState.nextInSetlist()

        assertEquals(ViewerTarget.Setlist(setlist.id, 1), appState.fullscreenTarget)
        assertEquals("two", appState.setlistItems(setlist.id)[1].id)
        assertFalse(appState.hasNextInSetlist(appState.fullscreenTarget))

        appState.nextInSetlist()
        assertEquals(ViewerTarget.Setlist(setlist.id, 1), appState.fullscreenTarget)
        assertEquals("That was the last piece in the set.", appState.bannerMessage)

        appState.selectSection(AppSection.LIBRARY)
        appState.closeFullscreen()

        assertNull(appState.fullscreenTarget)
        assertEquals(AppSection.SETLISTS, appState.selectedSection)
        assertEquals(setlist.id, appState.selectedSetlistId)
    }

    @Test
    fun startingAnEmptySetlistShowsABanner() = runBlocking {
        val runtime = createTestPlatformRuntime(
            initialSetlists = listOf(testLibrarySetlist(id = "empty", name = "Empty", itemIds = emptyList())),
        )
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.startSetlist("empty")

        assertNull(appState.fullscreenTarget)
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
        assertEquals(ViewerTarget.Setlist(TEMPORARY_SETLIST_ID, 0), appState.fullscreenTarget)
        assertTrue(appState.temporarySetlistOpen)

        appState.discardTemporarySetlist()
        assertNull(appState.fullscreenTarget)
        assertNull(appState.temporarySetlist)
    }

    @Test
    fun changingASongbookCoverRebindsTheExistingPdfInsteadOfItsDistinctScores() = runBlocking {
        // "a" was appended twice, but the repository only records distinct item ids.
        val items = listOf(testSheetMusicItem(id = "a"), testSheetMusicItem(id = "b"))
        val builds = mutableListOf<CoverBuild>()
        val runtime = createTestPlatformRuntime(
            initialItems = items,
            initialSongbooks = listOf(
                testLibrarySongbook(id = "book", name = "Book", itemIds = listOf("a", "b"), pageCount = 3)
                    .copy(hasCover = true),
            ),
            songbookCreationSupported = true,
            songbookCoverImageSupported = true,
            songbookBuilder = object : SongbookPdfBuilder {
                override suspend fun buildSongbook(
                    songbookName: String,
                    appendedDocumentPaths: List<String>,
                    existingSongbookPath: String?,
                    coverImage: SelectedCoverImage?,
                    dropExistingCoverPage: Boolean,
                ): SongbookBuildResult {
                    builds += CoverBuild(existingSongbookPath, appendedDocumentPaths, coverImage, dropExistingCoverPage)
                    return SongbookBuildResult(
                        document = com.ryanjames.lunar.library.model.PdfDocumentReference(
                            storedPath = "/songbooks/rebuilt.pdf",
                            originalFileName = "Book.pdf",
                        ),
                        pageCount = 3,
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
        waitUntil { runtime.repository.getSongbook("book")?.document?.storedPath == "/songbooks/rebuilt.pdf" }

        val rebuilt = runtime.repository.getSongbook("book")!!
        assertEquals(true, rebuilt.hasCover)
        assertEquals(3, rebuilt.pageCount)
        assertEquals(listOf("a", "b"), rebuilt.itemIds)
        val build = builds.single()
        assertEquals("/songbooks/book.pdf", build.existingSongbookPath)
        assertEquals(emptyList(), build.appendedDocumentPaths)
        assertEquals("cover.png", build.coverImage?.displayName)
        assertTrue(build.dropExistingCoverPage, "The previous cover page must be replaced, not kept.")
    }

    @Test
    fun addingAFirstCoverKeepsEveryExistingPage() = runBlocking {
        val builds = mutableListOf<CoverBuild>()
        val runtime = createTestPlatformRuntime(
            // The scores are gone from the library, but the bound PDF still holds their pages.
            initialItems = emptyList(),
            initialSongbooks = listOf(testLibrarySongbook(id = "book", name = "Book", itemIds = listOf("a"))),
            songbookCreationSupported = true,
            songbookCoverImageSupported = true,
            songbookBuilder = object : SongbookPdfBuilder {
                override suspend fun buildSongbook(
                    songbookName: String,
                    appendedDocumentPaths: List<String>,
                    existingSongbookPath: String?,
                    coverImage: SelectedCoverImage?,
                    dropExistingCoverPage: Boolean,
                ): SongbookBuildResult {
                    builds += CoverBuild(existingSongbookPath, appendedDocumentPaths, coverImage, dropExistingCoverPage)
                    return SongbookBuildResult(
                        document = com.ryanjames.lunar.library.model.PdfDocumentReference(
                            storedPath = "/songbooks/covered.pdf",
                            originalFileName = "Book.pdf",
                        ),
                        pageCount = 2,
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

        assertEquals("/songbooks/book.pdf", builds.single().existingSongbookPath)
        assertEquals(false, builds.single().dropExistingCoverPage)
        assertEquals("/songbooks/covered.pdf", runtime.repository.getSongbook("book")!!.document.storedPath)
    }

    @Test
    fun changingTheCoverOfALegacySongbookInfersNoCoverWhenItsPagesMatchTheScores() = runBlocking {
        val builds = mutableListOf<CoverBuild>()
        val runtime = legacyCoverRuntime(songbookPageCount = 3, builds = builds)
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.changeSongbookCover("book")
        waitUntil { runtime.repository.getSongbook("book")?.hasCover == true }

        assertFalse(builds.single().dropExistingCoverPage, "No score page may be dropped for the new cover.")
    }

    @Test
    fun changingTheCoverOfALegacySongbookWithAnUnknownLayoutLeavesItUnchanged() = runBlocking {
        val builds = mutableListOf<CoverBuild>()
        // One surplus page could be a cover or a one-page score bound twice.
        val runtime = legacyCoverRuntime(songbookPageCount = 4, builds = builds)
        val appState = createTestLunarAppState(scope = this, runtime = runtime)

        appState.changeSongbookCover("book")
        waitUntil { appState.bannerMessage != null }

        assertEquals(
            "Couldn't tell whether \"Book\" already has a cover, so it was left unchanged.",
            appState.bannerMessage,
        )
        assertEquals(emptyList(), builds)
        val songbook = runtime.repository.getSongbook("book")!!
        assertNull(songbook.hasCover)
        assertEquals("/songbooks/book.pdf", songbook.document.storedPath)
    }

    /** A pre-cover-tracking songbook binding one 3-page score into [songbookPageCount] pages. */
    private suspend fun legacyCoverRuntime(
        songbookPageCount: Int,
        builds: MutableList<CoverBuild>,
    ) = createTestPlatformRuntime(
        initialItems = listOf(testSheetMusicItem(id = "a", pageCount = 3)),
        initialSongbooks = listOf(
            testLibrarySongbook(
                id = "book",
                name = "Book",
                itemIds = listOf("a"),
                pageCount = songbookPageCount,
                hasCover = null,
                boundItemIds = null,
            ),
        ),
        songbookCreationSupported = true,
        songbookCoverImageSupported = true,
        songbookBuilder = object : SongbookPdfBuilder {
            override suspend fun buildSongbook(
                songbookName: String,
                appendedDocumentPaths: List<String>,
                existingSongbookPath: String?,
                coverImage: SelectedCoverImage?,
                dropExistingCoverPage: Boolean,
            ): SongbookBuildResult {
                builds += CoverBuild(existingSongbookPath, appendedDocumentPaths, coverImage, dropExistingCoverPage)
                return SongbookBuildResult(
                    document = com.ryanjames.lunar.library.model.PdfDocumentReference(
                        storedPath = "/songbooks/rebuilt.pdf",
                        originalFileName = "Book.pdf",
                    ),
                    pageCount = 4,
                )
            }
        },
        coverImagePicker = object : com.ryanjames.lunar.platform.CoverImagePicker {
            override suspend fun pickCoverImage(): SelectedCoverImage =
                SelectedCoverImage(displayName = "cover.png", bytes = byteArrayOf(1))
        },
    )

    private data class CoverBuild(
        val existingSongbookPath: String?,
        val appendedDocumentPaths: List<String>,
        val coverImage: SelectedCoverImage?,
        val dropExistingCoverPage: Boolean,
    )

    @Test
    fun addingScoresDuringACoverRebuildWaitsAndAppendsToTheRebuiltSongbook() = runBlocking {
        val items = listOf(
            testSheetMusicItem(id = "a"),
            testSheetMusicItem(id = "b"),
            testSheetMusicItem(id = "c"),
        )
        val coverBuildGate = CompletableDeferred<Unit>()
        val builds = mutableListOf<Pair<String?, List<String>>>()
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
                    dropExistingCoverPage: Boolean,
                ): SongbookBuildResult {
                    builds += existingSongbookPath to appendedDocumentPaths
                    if (coverImage != null) coverBuildGate.await()
                    return SongbookBuildResult(
                        document = com.ryanjames.lunar.library.model.PdfDocumentReference(
                            storedPath = "/songbooks/build-${builds.size}.pdf",
                            originalFileName = "Book.pdf",
                        ),
                        pageCount = builds.size * 10,
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
        waitUntil { builds.size == 1 }
        appState.addSelectionToSongbook("book", listOf(items[2]))
        repeat(20) { yield() }
        assertEquals(1, builds.size, "The append must wait for the cover rebuild to finish.")

        coverBuildGate.complete(Unit)
        waitUntil { runtime.repository.getSongbook("book")?.itemIds == listOf("a", "b", "c") }

        val songbook = runtime.repository.getSongbook("book")!!
        assertEquals("/songbooks/build-1.pdf", builds[1].first)
        assertEquals(listOf("/scores/c.pdf"), builds[1].second)
        assertEquals("/songbooks/build-2.pdf", songbook.document.storedPath)
        assertEquals(true, songbook.hasCover)
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        repeat(200) {
            if (condition()) return
            yield()
        }
        assertTrue(condition(), "Condition was not met in time.")
    }
}
