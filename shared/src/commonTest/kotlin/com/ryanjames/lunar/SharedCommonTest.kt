package com.ryanjames.lunar

import com.ryanjames.lunar.composition.CompositionNotation
import com.ryanjames.lunar.composition.InMemoryCompositionDraftStore
import com.ryanjames.lunar.composition.createCompositionDraft
import com.ryanjames.lunar.composition.withUpdatedContents
import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.InMemoryLibraryStorage
import com.ryanjames.lunar.library.data.InMemoryScoreMetadataStorage
import com.ryanjames.lunar.library.data.SheetMusicMetadataInput
import com.ryanjames.lunar.library.data.SyncedSheetMusicDescriptor
import com.ryanjames.lunar.library.data.StoredDocumentFingerprinter
import com.ryanjames.lunar.library.model.HiddenScoreFilter
import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.library.model.LibraryQuery
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.ScoreMetadata
import com.ryanjames.lunar.library.model.ScoreMetadataComposer
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.library.model.ViewerSupportFilter
import com.ryanjames.lunar.library.model.applyLibraryQuery
import com.ryanjames.lunar.library.model.collectionDuplicateKey
import com.ryanjames.lunar.library.model.composerDuplicateKey
import com.ryanjames.lunar.library.model.contents
import com.ryanjames.lunar.library.model.resolveHasCover
import com.ryanjames.lunar.library.model.songbookStartPages
import com.ryanjames.lunar.library.model.jumpBackInItems
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedCommonTest {
    @Test
    fun compositionDraftStoreUpsertsAndDeletesDrafts() = runBlocking {
        val store = InMemoryCompositionDraftStore()
        val draft = createCompositionDraft(
            title = "Sketch",
            composer = "Composer",
            sourceText = "\\version \"2.24.0\"",
            notation = CompositionNotation.LILYPOND,
        )

        store.initialize()
        store.upsertDraft(draft)
        assertEquals(listOf(draft), store.drafts.value)
        assertEquals(draft, store.getDraft(draft.id))

        val updated = draft.withUpdatedContents(
            title = "Finished Sketch",
            composer = "Composer",
            sourceText = "{ c'4 d' }",
        )
        store.upsertDraft(updated)
        assertEquals(listOf(updated), store.drafts.value)

        store.deleteDraft(draft.id)
        assertTrue(store.drafts.value.isEmpty())
        assertNull(store.getDraft(draft.id))
    }

    @Test
    fun repositoryImportsAndUpdatesMetadata() = runBlocking {
        val metadataStorage = InMemoryScoreMetadataStorage()
        val repository = DefaultSheetMusicRepository(
            storage = InMemoryLibraryStorage(),
            scoreMetadataStorage = metadataStorage,
        )

        repository.initialize()
        val imported = repository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/moonlight-sonata.pdf",
                    originalFileName = "Moonlight Sonata.pdf",
                    pageCount = 6,
                    contentFingerprint = "1234567890abcdef1234567890abcdef",
                )
            )
        ).importedItems

        repository.updateMetadata(
            itemId = imported.single().id,
            metadata = SheetMusicMetadataInput(
                title = "Moonlight Sonata",
                composer = "Beethoven",
                tags = listOf("piano", " recital ", "piano"),
                collection = "Practice",
                isFavorite = true,
            ),
        )

        val stored = repository.getItem(imported.single().id)
        assertEquals("Moonlight Sonata", stored?.title)
        assertEquals("Beethoven", stored?.composer)
        assertEquals(listOf("piano", "recital"), stored?.tags)
        assertEquals("Practice", stored?.collection)
        assertEquals(true, stored?.isFavorite)

        val storedMetadata = metadataStorage.readMetadata(imported.single().id)
        assertEquals("1234567890abcdef", storedMetadata?.id)
        assertEquals("Moonlight Sonata", storedMetadata?.title)
        assertEquals("Beethoven", storedMetadata?.composer?.name)
        assertEquals(listOf("piano", "recital"), storedMetadata?.tags)
        assertEquals(6, storedMetadata?.pageCount)
        assertEquals("Moonlight Sonata.pdf", storedMetadata?.source?.filename)
    }

    @Test
    fun repositoryUsesImportedScoreMetadataWhenAvailable() = runBlocking {
        val repository = DefaultSheetMusicRepository(
            storage = InMemoryLibraryStorage(),
            scoreMetadataStorage = InMemoryScoreMetadataStorage(),
        )

        repository.initialize()
        val importedItem = repository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/the-bell.pdf",
                    originalFileName = "H.Stief_The_Bell.pdf",
                    pageCount = 6,
                    scoreMetadata = ScoreMetadata(
                        id = "266ec2f19ddb0cbb",
                        title = "The Bell",
                        alternativeTitles = listOf("H.Stief The Bell"),
                        composer = ScoreMetadataComposer(name = "Holger Stief"),
                        tags = listOf("the", "bell", "stief"),
                    ),
                )
            )
        ).importedItems.single()

        assertEquals("The Bell", importedItem.title)
        assertEquals("Holger Stief", importedItem.composer)
        assertEquals(listOf("bell", "stief", "the"), importedItem.tags)

        val storedMetadata = repository.getScoreMetadata(importedItem.id)
        assertEquals("266ec2f19ddb0cbb", storedMetadata?.id)
        assertEquals(listOf("H.Stief The Bell"), storedMetadata?.alternativeTitles)
        assertEquals("H.Stief_The_Bell.pdf", storedMetadata?.source?.filename)
        assertEquals(6, storedMetadata?.pageCount)
    }

    @Test
    fun repositoryInfersSourceFileTypeFromImportedFileName() = runBlocking {
        val repository = DefaultSheetMusicRepository(
            storage = InMemoryLibraryStorage(),
            scoreMetadataStorage = InMemoryScoreMetadataStorage(),
        )

        repository.initialize()
        val importedItem = repository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/moon_river.pdf",
                    originalFileName = "Moon River.ly",
                    pageCount = 1,
                )
            )
        ).importedItems.single()

        assertEquals("ly", repository.getScoreMetadata(importedItem.id)?.source?.fileType)
    }

    @Test
    fun libraryQueryFiltersAndSortsAcrossMetadata() {
        val items = listOf(
            testItem(
                id = "1",
                title = "Moon River",
                composer = "Mancini",
                tags = listOf("jazz"),
                collection = "Standards",
                isFavorite = true,
                dateAddedEpochMillis = 10L,
                lastOpenedEpochMillis = 40L,
            ),
            testItem(
                id = "2",
                title = "Clair de Lune",
                composer = "Debussy",
                tags = listOf("piano", "solo"),
                collection = "Recital",
                dateAddedEpochMillis = 30L,
                lastOpenedEpochMillis = 20L,
            ),
            testItem(
                id = "3",
                title = "Take Five",
                composer = "Desmond",
                tags = listOf("jazz", "quartet"),
                collection = "Standards",
                dateAddedEpochMillis = 20L,
            ),
        )

        val result = items.applyLibraryQuery(
            LibraryQuery(
                searchText = "jazz",
                selectedCollection = "Standards",
                favoritesOnly = true,
                sortOption = LibrarySortOption.LAST_OPENED,
                sortDirection = SortDirection.DESCENDING,
            )
        )

        assertEquals(listOf("Moon River"), result.map { it.title })
    }

    @Test
    fun libraryQueryRequiresAllSelectedTagsWhenMultipleTagsAreChosen() {
        val items = listOf(
            testItem(
                id = "1",
                title = "Moon River",
                composer = "Mancini",
                tags = listOf("jazz"),
                collection = "Standards",
                dateAddedEpochMillis = 10L,
            ),
            testItem(
                id = "2",
                title = "Take Five",
                composer = "Desmond",
                tags = listOf("jazz", "quartet"),
                collection = "Standards",
                dateAddedEpochMillis = 20L,
            ),
            testItem(
                id = "3",
                title = "Blue Rondo",
                composer = "Brubeck",
                tags = listOf("quartet"),
                collection = "Standards",
                dateAddedEpochMillis = 30L,
            ),
        )

        val result = items.applyLibraryQuery(
            LibraryQuery(
                selectedTags = setOf("jazz", "quartet"),
                sortOption = LibrarySortOption.TITLE,
                sortDirection = SortDirection.ASCENDING,
            )
        )

        assertEquals(listOf("Take Five"), result.map { it.title })
    }

    @Test
    fun jumpBackInListsMostRecentlyOpenedVisibleScoresUpToTheLimit() {
        val items = listOf(
            testItem(id = "old", title = "Old", composer = "A", tags = emptyList(), dateAddedEpochMillis = 1L, lastOpenedEpochMillis = 10L),
            testItem(id = "never", title = "Never", composer = "A", tags = emptyList(), dateAddedEpochMillis = 2L),
            testItem(id = "newest", title = "Newest", composer = "A", tags = emptyList(), dateAddedEpochMillis = 3L, lastOpenedEpochMillis = 50L),
            testItem(id = "hidden", title = "Hidden", composer = "A", tags = emptyList(), isHidden = true, dateAddedEpochMillis = 4L, lastOpenedEpochMillis = 99L),
            testItem(id = "middle", title = "Middle", composer = "A", tags = emptyList(), dateAddedEpochMillis = 5L, lastOpenedEpochMillis = 30L),
            testItem(id = "recent", title = "Recent", composer = "A", tags = emptyList(), dateAddedEpochMillis = 6L, lastOpenedEpochMillis = 40L),
        )

        assertEquals(listOf("newest", "recent", "middle"), items.jumpBackInItems().map { it.id })
        assertEquals(listOf("newest"), items.jumpBackInItems(limit = 1).map { it.id })
        assertEquals(listOf("newest", "recent", "middle", "old"), items.jumpBackInItems(limit = 10).map { it.id })
        assertTrue(items.jumpBackInItems(limit = 0).isEmpty())
    }

    @Test
    fun jumpBackInIsEmptyWhenOnlyHiddenOrUnopenedScoresExist() {
        val items = listOf(
            testItem(id = "never", title = "Never", composer = "A", tags = emptyList(), dateAddedEpochMillis = 1L),
            testItem(id = "hidden", title = "Hidden", composer = "A", tags = emptyList(), isHidden = true, dateAddedEpochMillis = 2L, lastOpenedEpochMillis = 5L),
        )

        assertTrue(items.jumpBackInItems().isEmpty())
    }

    @Test
    fun libraryQueryFiltersByViewerSupport() {
        val items = listOf(
            testItem(
                id = "pdf",
                title = "Moon River",
                composer = "Mancini",
                tags = emptyList(),
                dateAddedEpochMillis = 10L,
                originalFileName = "Moon River.pdf",
            ),
            testItem(
                id = "lilypond",
                title = "Prelude",
                composer = "Bach",
                tags = emptyList(),
                dateAddedEpochMillis = 20L,
                originalFileName = "Prelude.ly",
            ),
        )

        assertEquals(
            listOf("Moon River"),
            items.applyLibraryQuery(
                LibraryQuery(
                    viewerSupport = ViewerSupportFilter.PDF,
                    sortOption = LibrarySortOption.TITLE,
                    sortDirection = SortDirection.ASCENDING,
                )
            ).map { it.title },
        )
        assertEquals(
            listOf("Prelude"),
            items.applyLibraryQuery(
                LibraryQuery(
                    viewerSupport = ViewerSupportFilter.LILYPOND,
                    sortOption = LibrarySortOption.TITLE,
                    sortDirection = SortDirection.ASCENDING,
                )
            ).map { it.title },
        )
    }

    @Test
    fun libraryQueryExcludesHiddenScoresUnlessHiddenViewIsRequested() {
        val visible = testItem(
            id = "visible",
            title = "Visible Score",
            composer = "Composer",
            tags = listOf("piano"),
            dateAddedEpochMillis = 10L,
        )
        val hidden = testItem(
            id = "hidden",
            title = "Hidden Score",
            composer = "Composer",
            tags = listOf("piano"),
            dateAddedEpochMillis = 20L,
            isHidden = true,
        )
        val items = listOf(visible, hidden)

        assertEquals(
            listOf("Visible Score"),
            items.applyLibraryQuery(LibraryQuery()).map { it.title },
        )
        assertEquals(
            listOf("Hidden Score"),
            items.applyLibraryQuery(
                LibraryQuery(hiddenFilter = HiddenScoreFilter.HIDDEN)
            ).map { it.title },
        )
    }

    @Test
    fun repositoryDeleteRemovesStoredItem() = runBlocking {
        val metadataStorage = InMemoryScoreMetadataStorage()
        val repository = DefaultSheetMusicRepository(
            storage = InMemoryLibraryStorage(),
            scoreMetadataStorage = metadataStorage,
        )

        repository.initialize()
        val imported = repository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/delete-me.pdf",
                    originalFileName = "Delete Me.pdf",
                )
            )
        ).importedItems

        repository.deleteItem(imported.single().id)

        assertNull(repository.getItem(imported.single().id))
        assertNull(metadataStorage.readMetadata(imported.single().id))
        assertEquals(emptyList(), repository.library.value.items)
    }

    @Test
    fun repositoryUpdatesHiddenStateWithoutDeletingScore() = runBlocking {
        val item = testItem(
            id = "bad_scan",
            title = "Bad Scan",
            composer = "Composer",
            tags = emptyList(),
            dateAddedEpochMillis = 1L,
        )
        val repository = DefaultSheetMusicRepository(
            storage = InMemoryLibraryStorage(initialItems = listOf(item)),
        )

        repository.initialize()
        repository.updateHidden(item.id, isHidden = true)

        assertTrue(repository.getItem(item.id)?.isHidden == true)
        assertEquals(
            emptyList(),
            repository.library.value.items.applyLibraryQuery(LibraryQuery()),
        )

        repository.updateHidden(item.id, isHidden = false)

        assertEquals(false, repository.getItem(item.id)?.isHidden)
        assertEquals(
            listOf("Bad Scan"),
            repository.library.value.items.applyLibraryQuery(LibraryQuery()).map { it.title },
        )
    }

    @Test
    fun repositoryInitializeBackfillsMissingScoreMetadataForExistingItems() = runBlocking {
        val metadataStorage = InMemoryScoreMetadataStorage()
        val repository = DefaultSheetMusicRepository(
            storage = InMemoryLibraryStorage(
                initialItems = listOf(
                    testItem(
                        id = "existing",
                        title = "Existing Score",
                        composer = "Existing Composer",
                        tags = listOf("practice"),
                        dateAddedEpochMillis = 1L,
                    )
                )
            ),
            scoreMetadataStorage = metadataStorage,
        )

        repository.initialize()

        val storedMetadata = metadataStorage.readMetadata("existing")
        assertEquals("Existing Score", storedMetadata?.title)
        assertEquals("Existing Composer", storedMetadata?.composer?.name)
        assertEquals(listOf("practice"), storedMetadata?.tags)
    }

    @Test
    fun repositoryInitializeUsesStoredScoreMetadataToOrganizeExistingItems() = runBlocking {
        val metadataStorage = InMemoryScoreMetadataStorage(
            initialMetadata = mapOf(
                "existing" to ScoreMetadata(
                    id = "266ec2f19ddb0cbb",
                    title = "The Bell",
                    composer = ScoreMetadataComposer(name = "Holger Stief"),
                    tags = listOf("bell", "solo"),
                )
            )
        )
        val repository = DefaultSheetMusicRepository(
            storage = InMemoryLibraryStorage(
                initialItems = listOf(
                    testItem(
                        id = "existing",
                        title = "H.Stief_The_Bell",
                        composer = "Fallback Composer",
                        tags = listOf("cloud"),
                        collection = "Recital",
                        dateAddedEpochMillis = 1L,
                    )
                )
            ),
            scoreMetadataStorage = metadataStorage,
        )

        repository.initialize()

        val item = repository.library.value.items.single()
        assertEquals("The Bell", item.title)
        assertEquals("Holger Stief", item.composer)
        assertEquals(listOf("bell", "solo"), item.tags)
        assertEquals("Recital", item.collection)
    }

    @Test
    fun repositoryPersistsSetlistsAcrossInitializations() = runBlocking {
        val storage = InMemoryLibraryStorage()
        val firstRepository = DefaultSheetMusicRepository(storage)

        firstRepository.initialize()
        val importedItems = firstRepository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/one.pdf",
                    originalFileName = "One.pdf",
                ),
                ImportedPdfDescriptor(
                    storedPath = "/scores/two.pdf",
                    originalFileName = "Two.pdf",
                ),
            )
        ).importedItems
        val createdSetlist = firstRepository.createSetlist(
            name = "Friday rehearsal",
            itemIds = importedItems.map { it.id },
        )

        val secondRepository = DefaultSheetMusicRepository(storage)
        secondRepository.initialize()

        assertEquals(1, secondRepository.library.value.setlists.size)
        assertEquals(createdSetlist.name, secondRepository.library.value.setlists.single().name)
        assertEquals(
            importedItems.map { it.id },
            secondRepository.library.value.setlists.single().itemIds,
        )
    }

    @Test
    fun repositoryAddItemsToSetlistKeepsOrderAndDeduplicates() = runBlocking {
        val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())

        repository.initialize()
        val importedItems = repository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/a.pdf",
                    originalFileName = "A.pdf",
                ),
                ImportedPdfDescriptor(
                    storedPath = "/scores/b.pdf",
                    originalFileName = "B.pdf",
                ),
                ImportedPdfDescriptor(
                    storedPath = "/scores/c.pdf",
                    originalFileName = "C.pdf",
                ),
            )
        ).importedItems
        val setlist = repository.createSetlist(
            name = "Main set",
            itemIds = listOf(importedItems[1].id, importedItems[0].id),
        )

        val updatedSetlist = repository.addItemsToSetlist(
            setlistId = setlist.id,
            itemIds = listOf(importedItems[0].id, importedItems[2].id),
        )

        assertEquals(
            listOf(importedItems[1].id, importedItems[0].id, importedItems[2].id),
            updatedSetlist.itemIds,
        )
    }

    @Test
    fun repositoryPrunesDeletedScoresAndDeletesSetlists() = runBlocking {
        val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())

        repository.initialize()
        val importedItems = repository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/alpha.pdf",
                    originalFileName = "Alpha.pdf",
                ),
                ImportedPdfDescriptor(
                    storedPath = "/scores/beta.pdf",
                    originalFileName = "Beta.pdf",
                ),
            )
        ).importedItems
        val setlist = repository.createSetlist(
            name = "Cleanup test",
            itemIds = importedItems.map { it.id },
        )

        repository.deleteItem(importedItems.first().id)

        assertEquals(
            listOf(importedItems.last().id),
            repository.library.value.setlists.single().itemIds,
        )

        repository.deleteSetlist(setlist.id)

        assertEquals(emptyList(), repository.library.value.setlists)
    }

    @Test
    fun repositoryReordersSetlistKeepingEveryItemExactlyOnce() = runBlocking {
        val storage = InMemoryLibraryStorage()
        val repository = DefaultSheetMusicRepository(storage)
        repository.initialize()
        val ids = repository.importSetlistFixtures()
        val setlist = repository.createSetlist(name = "Programme", itemIds = ids)

        val reordered = repository.reorderSetlist(setlist.id, listOf(ids[2], ids[0], ids[1]))

        assertEquals(listOf(ids[2], ids[0], ids[1]), reordered.itemIds)
        assertFailsWith<IllegalArgumentException> {
            repository.reorderSetlist(setlist.id, listOf(ids[0], ids[1], "unknown"))
        }
        assertFailsWith<IllegalArgumentException> {
            repository.reorderSetlist(setlist.id, listOf(ids[0], ids[0], ids[1]))
        }
        assertFailsWith<IllegalArgumentException> {
            repository.reorderSetlist(setlist.id, listOf(ids[0], ids[1]))
        }
        assertFailsWith<IllegalStateException> {
            repository.reorderSetlist("missing", ids)
        }

        val reloaded = DefaultSheetMusicRepository(storage)
        reloaded.initialize()
        assertEquals(listOf(ids[2], ids[0], ids[1]), reloaded.getSetlist(setlist.id)?.itemIds)
    }

    @Test
    fun repositoryRenamesSetlistTrimmingAndRejectingBlankNames() = runBlocking {
        val storage = InMemoryLibraryStorage()
        val repository = DefaultSheetMusicRepository(storage)
        repository.initialize()
        val setlist = repository.createSetlist(name = "Draft", itemIds = repository.importSetlistFixtures())

        val renamed = repository.renameSetlist(setlist.id, "  Spring recital  ")

        assertEquals("Spring recital", renamed.name)
        assertFailsWith<IllegalArgumentException> {
            repository.renameSetlist(setlist.id, "   ")
        }
        assertEquals("Spring recital", repository.getSetlist(setlist.id)?.name)

        val reloaded = DefaultSheetMusicRepository(storage)
        reloaded.initialize()
        assertEquals("Spring recital", reloaded.getSetlist(setlist.id)?.name)
    }

    @Test
    fun repositoryRemovesItemsFromSetlistAndBumpsUpdatedAt() = runBlocking {
        val storage = InMemoryLibraryStorage()
        var now = 1_000L
        val clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(now)
        }
        val repository = DefaultSheetMusicRepository(storage, clock = clock)
        repository.initialize()
        val ids = repository.importSetlistFixtures()
        val setlist = repository.createSetlist(name = "Programme", itemIds = ids)
        now = 5_000L

        val updated = repository.removeFromSetlist(setlist.id, listOf(ids[1], "not-in-setlist"))

        assertEquals(listOf(ids[0], ids[2]), updated.itemIds)
        assertEquals(1_000L, updated.createdAtEpochMillis)
        assertEquals(5_000L, updated.updatedAtEpochMillis)

        val reloaded = DefaultSheetMusicRepository(storage)
        reloaded.initialize()
        assertEquals(listOf(ids[0], ids[2]), reloaded.getSetlist(setlist.id)?.itemIds)
        assertEquals(5_000L, reloaded.getSetlist(setlist.id)?.updatedAtEpochMillis)
    }

    @Test
    fun songbookStartPagesAccumulatePageCounts() {
        assertEquals(listOf(1, 3, 7), songbookStartPages(listOf(2, 4, 1)))
        assertEquals(listOf(2, 4), songbookStartPages(listOf(2, 4), firstPage = 2))
    }

    @Test
    fun songbookStartPagesBecomeUnknownAfterAnUnknownCount() {
        assertEquals(listOf(1, 3, null, null), songbookStartPages(listOf(2, null, 3, 1)))
        assertEquals(listOf(1, null), songbookStartPages(listOf(0, 3)))
    }

    @Test
    fun songbookStartPagesForAnEmptySongbook() {
        assertEquals(emptyList(), songbookStartPages(emptyList()))
        val songbook = LibrarySongbook(
            id = "empty",
            name = "Empty",
            document = PdfDocumentReference(storedPath = "/books/empty.pdf", originalFileName = "Empty.pdf"),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
        )
        assertEquals(emptyList(), songbook.contents(emptyMap()))
    }

    @Test
    fun songbookContentsSkipTheCoverAndKeepMissingScores() {
        val first = testItem(
            id = "first",
            title = "First",
            composer = "Composer",
            tags = emptyList(),
            dateAddedEpochMillis = 1L,
        ).copy(pageCount = 3)
        val songbook = LibrarySongbook(
            id = "book",
            name = "Book",
            itemIds = listOf("first", "gone", "last"),
            document = PdfDocumentReference(storedPath = "/books/book.pdf", originalFileName = "Book.pdf"),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
            hasCover = true,
            boundItemIds = listOf("first", "gone", "last"),
        )

        val contents = songbook.contents(mapOf(first.id to first))

        assertEquals(listOf("first", "gone", "last"), contents.map { it.itemId })
        assertEquals(listOf(2, 5, null), contents.map { it.startPage })
        assertNull(contents[1].item)
    }

    @Test
    fun legacySongbooksDecodeWithUnknownCoverAndBoundOrder() {
        val legacyJson = """
            {"id":"book","name":"Book","itemIds":["a"],
             "document":{"storedPath":"/books/book.pdf","originalFileName":"Book.pdf"},
             "createdAtEpochMillis":1,"updatedAtEpochMillis":1,"pageCount":4}
        """.trimIndent()

        val songbook = Json { ignoreUnknownKeys = true }.decodeFromString<LibrarySongbook>(legacyJson)

        assertNull(songbook.hasCover)
        assertNull(songbook.boundItemIds)
    }

    @Test
    fun legacySongbookCoverIsInferredOnlyWhenPageCountsAreUnambiguous() {
        val items = listOf(
            testItem(id = "a", title = "A", composer = "C", tags = emptyList(), dateAddedEpochMillis = 1L)
                .copy(pageCount = 3),
            testItem(id = "b", title = "B", composer = "C", tags = emptyList(), dateAddedEpochMillis = 1L)
                .copy(pageCount = 2),
        ).associateBy { it.id }
        fun legacy(pageCount: Int?) = LibrarySongbook(
            id = "book",
            name = "Book",
            itemIds = listOf("a", "b"),
            document = PdfDocumentReference(storedPath = "/books/book.pdf", originalFileName = "Book.pdf"),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
            pageCount = pageCount,
        )

        // One surplus page could be a cover or a one-page score bound twice.
        assertNull(legacy(6).resolveHasCover(items))
        assertEquals(listOf(null, null), legacy(6).contents(items).map { it.startPage })
        // With the bound order known, the surplus page can only be a cover.
        val ordered = legacy(6).copy(boundItemIds = listOf("a", "b"))
        assertEquals(true, ordered.resolveHasCover(items))
        assertEquals(listOf(2, 5), ordered.contents(items).map { it.startPage })
        assertEquals(false, legacy(5).resolveHasCover(items))
        assertEquals(listOf(1, 4), legacy(5).contents(items).map { it.startPage })
        // Extra pages, such as a score that was bound twice, leave the layout unknown.
        assertNull(legacy(8).resolveHasCover(items))
        assertEquals(listOf(null, null), legacy(8).contents(items).map { it.startPage })
        assertNull(legacy(null).resolveHasCover(items))
        assertEquals(listOf(null, null), legacy(null).contents(items).map { it.startPage })
        assertNull(legacy(6).resolveHasCover(emptyMap()))
        assertEquals(listOf(null, null), legacy(6).contents(emptyMap()).map { it.startPage })
    }

    @Test
    fun songbookContentsFollowTheBoundOrderWhenAScoreIsAppendedTwice() = runBlocking {
        val storage = InMemoryLibraryStorage()
        val repository = DefaultSheetMusicRepository(storage)
        repository.initialize()
        val items = listOf("a" to 3, "b" to 2, "c" to 4).associate { (id, pages) ->
            id to testItem(id = id, title = id.uppercase(), composer = "C", tags = emptyList(), dateAddedEpochMillis = 1L)
                .copy(pageCount = pages)
        }
        val created = repository.createSongbook(
            name = "Book",
            itemIds = listOf("a", "b"),
            document = PdfDocumentReference(storedPath = "/books/v1.pdf", originalFileName = "Book.pdf"),
            pageCount = 6,
            hasCover = true,
        )
        val appended = repository.addItemsToSongbook(
            songbookId = created.id,
            itemIds = listOf("a", "c"),
            document = PdfDocumentReference(storedPath = "/books/v2.pdf", originalFileName = "Book.pdf"),
            pageCount = 13,
        )

        assertEquals(listOf("a", "b", "c"), appended.itemIds)
        assertEquals(listOf("a", "b", "a", "c"), appended.boundItemIds)
        val contents = appended.contents(items)
        assertEquals(listOf("a", "b", "a", "c"), contents.map { it.itemId })
        assertEquals(listOf(2, 5, 7, 10), contents.map { it.startPage })

        val reloaded = DefaultSheetMusicRepository(storage)
        reloaded.initialize()
        assertEquals(listOf("a", "b", "a", "c"), reloaded.getSongbook(created.id)?.boundItemIds)
    }

    @Test
    fun songbookContentsHideStartPagesWhenScorePagesContradictTheBoundPdf() {
        val items = listOf(
            testItem(id = "a", title = "A", composer = "C", tags = emptyList(), dateAddedEpochMillis = 1L)
                .copy(pageCount = 3),
        ).associateBy { it.id }
        val songbook = LibrarySongbook(
            id = "book",
            name = "Book",
            itemIds = listOf("a"),
            document = PdfDocumentReference(storedPath = "/books/book.pdf", originalFileName = "Book.pdf"),
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
            pageCount = 9,
            hasCover = false,
            boundItemIds = listOf("a"),
        )

        assertEquals(listOf<Int?>(null), songbook.contents(items).map { it.startPage })
    }

    @Test
    fun repositoryAppendToALegacySongbookKeepsItsBoundOrderUnknown() = runBlocking {
        val storage = InMemoryLibraryStorage()
        val repository = DefaultSheetMusicRepository(storage)
        repository.initialize()
        val created = repository.createSongbook(
            name = "Book",
            itemIds = listOf("a"),
            document = PdfDocumentReference(storedPath = "/books/v1.pdf", originalFileName = "Book.pdf"),
        )
        storage.writeLibraryData(
            storage.readLibraryData().let { data ->
                data.copy(songbooks = data.songbooks.map { it.copy(hasCover = null, boundItemIds = null) })
            },
        )
        val legacyRepository = DefaultSheetMusicRepository(storage)
        legacyRepository.initialize()

        val appended = legacyRepository.addItemsToSongbook(
            songbookId = created.id,
            itemIds = listOf("b"),
            document = PdfDocumentReference(storedPath = "/books/v2.pdf", originalFileName = "Book.pdf"),
        )

        assertNull(appended.boundItemIds)
        assertNull(appended.hasCover)
    }

    @Test
    fun repositoryReplacesSongbookDocumentAndRecordsCover() = runBlocking {
        val storage = InMemoryLibraryStorage()
        val repository = DefaultSheetMusicRepository(storage)
        repository.initialize()
        val songbook = repository.createSongbook(
            name = "Book",
            itemIds = listOf("a"),
            document = PdfDocumentReference(storedPath = "/books/v1.pdf", originalFileName = "Book.pdf"),
            pageCount = 3,
        )
        assertEquals(false, songbook.hasCover)

        val rebound = repository.replaceSongbookDocument(
            songbookId = songbook.id,
            document = PdfDocumentReference(storedPath = "/books/v2.pdf", originalFileName = "Book.pdf"),
            pageCount = 4,
            hasCover = true,
        )

        assertEquals("/books/v2.pdf", rebound.document.storedPath)
        assertEquals(4, rebound.pageCount)
        assertEquals(true, rebound.hasCover)
        val reloaded = DefaultSheetMusicRepository(storage)
        reloaded.initialize()
        assertEquals(true, reloaded.getSongbook(songbook.id)?.hasCover)
    }

    @Test
    fun repositoryPersistsSongbooksAcrossInitializations() = runBlocking {
        val storage = InMemoryLibraryStorage()
        val firstRepository = DefaultSheetMusicRepository(storage)

        firstRepository.initialize()
        val importedItems = firstRepository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/songbook-one.pdf",
                    originalFileName = "Songbook One.pdf",
                ),
                ImportedPdfDescriptor(
                    storedPath = "/scores/songbook-two.pdf",
                    originalFileName = "Songbook Two.pdf",
                ),
            )
        ).importedItems
        val createdSongbook = firstRepository.createSongbook(
            name = "Sunday book",
            itemIds = importedItems.map { it.id },
            document = PdfDocumentReference(
                storedPath = "/songbooks/sunday-book.pdf",
                originalFileName = "Sunday book.pdf",
            ),
            pageCount = 12,
        )

        val secondRepository = DefaultSheetMusicRepository(storage)
        secondRepository.initialize()

        assertEquals(1, secondRepository.library.value.songbooks.size)
        assertEquals(createdSongbook.name, secondRepository.library.value.songbooks.single().name)
        assertEquals(
            createdSongbook.document.storedPath,
            secondRepository.library.value.songbooks.single().document.storedPath,
        )
    }

    @Test
    fun repositoryAddItemsToSongbookKeepsOrderAndUpdatesMergedPdf() = runBlocking {
        val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())

        repository.initialize()
        val importedItems = repository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/a.pdf",
                    originalFileName = "A.pdf",
                ),
                ImportedPdfDescriptor(
                    storedPath = "/scores/b.pdf",
                    originalFileName = "B.pdf",
                ),
                ImportedPdfDescriptor(
                    storedPath = "/scores/c.pdf",
                    originalFileName = "C.pdf",
                ),
            )
        ).importedItems
        val songbook = repository.createSongbook(
            name = "Merged book",
            itemIds = listOf(importedItems[1].id, importedItems[0].id),
            document = PdfDocumentReference(
                storedPath = "/songbooks/merged-book-v1.pdf",
                originalFileName = "Merged book.pdf",
            ),
            pageCount = 8,
        )

        val updatedSongbook = repository.addItemsToSongbook(
            songbookId = songbook.id,
            itemIds = listOf(importedItems[0].id, importedItems[2].id),
            document = PdfDocumentReference(
                storedPath = "/songbooks/merged-book-v2.pdf",
                originalFileName = "Merged book.pdf",
            ),
            pageCount = 11,
        )

        assertEquals(
            listOf(importedItems[1].id, importedItems[0].id, importedItems[2].id),
            updatedSongbook.itemIds,
        )
        assertEquals("/songbooks/merged-book-v2.pdf", updatedSongbook.document.storedPath)
        assertEquals(11, updatedSongbook.pageCount)
    }

    @Test
    fun repositoryDeletesSongbookWithoutRemovingScores() = runBlocking {
        val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())

        repository.initialize()
        val importedItems = repository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/songbook-delete.pdf",
                    originalFileName = "Delete Songbook Score.pdf",
                )
            )
        ).importedItems
        val songbook = repository.createSongbook(
            name = "Delete me",
            itemIds = importedItems.map { it.id },
            document = PdfDocumentReference(
                storedPath = "/songbooks/delete-me.pdf",
                originalFileName = "Delete me.pdf",
            ),
        )

        repository.deleteSongbook(songbook.id)

        assertTrue(repository.library.value.songbooks.isEmpty())
        assertEquals(importedItems.map { it.id }, repository.library.value.items.map { it.id })
    }

    @Test
    fun repositoryApplyRemoteSyncUpsertsProviderItems() = runBlocking {
        val repository = DefaultSheetMusicRepository(
            storage = InMemoryLibraryStorage(),
            scoreMetadataStorage = InMemoryScoreMetadataStorage(),
        )

        repository.initialize()
        repository.applyRemoteSync(
            providerId = "supabase_public_storage",
            providerName = "Supabase Public Storage",
            syncedAtEpochMillis = 100L,
            items = listOf(
                SyncedSheetMusicDescriptor(
                    remoteId = "moonlight",
                    remoteVersion = "v1",
                    storedPath = "/scores/moonlight.pdf",
                    originalFileName = "Moonlight Sonata.pdf",
                    sourceUri = "https://example.test/moonlight.pdf",
                    title = "Moonlight Sonata",
                    composer = "Beethoven",
                    tags = listOf("piano", "sonata"),
                    collection = "Cloud",
                    pageCount = 6,
                )
            ),
        )

        val item = repository.library.value.items.single()
        assertEquals("Moonlight Sonata", item.title)
        assertEquals("Beethoven", item.composer)
        assertEquals("supabase_public_storage", item.syncMetadata?.providerId)
        assertEquals("moonlight", item.syncMetadata?.remoteId)
        assertEquals("/scores/moonlight.pdf", item.document.storedPath)
        assertEquals("Moonlight Sonata", repository.getScoreMetadata(item.id)?.title)
        assertEquals("Beethoven", repository.getScoreMetadata(item.id)?.composer?.name)
    }

    @Test
    fun repositoryApplyRemoteSyncUsesScoreMetadataWhenAvailable() = runBlocking {
        val repository = DefaultSheetMusicRepository(
            storage = InMemoryLibraryStorage(),
            scoreMetadataStorage = InMemoryScoreMetadataStorage(),
        )

        repository.initialize()
        repository.applyRemoteSync(
            providerId = "src_google_drive",
            providerName = "Google Drive",
            syncedAtEpochMillis = 100L,
            sourceId = "src_google_drive",
            items = listOf(
                SyncedSheetMusicDescriptor(
                    remoteId = "the-bell",
                    remoteVersion = "v1",
                    storedPath = "/scores/the-bell.pdf",
                    originalFileName = "H.Stief_The_Bell.pdf",
                    title = "Fallback Title",
                    composer = "Fallback Composer",
                    tags = listOf("cloud"),
                    collection = "Drive",
                    pageCount = 6,
                    scoreMetadata = ScoreMetadata(
                        id = "266ec2f19ddb0cbb",
                        title = "The Bell",
                        composer = ScoreMetadataComposer(name = "Holger Stief"),
                        tags = listOf("bell", "stief"),
                    ),
                )
            ),
        )

        val item = repository.library.value.items.single()
        assertEquals("The Bell", item.title)
        assertEquals("Holger Stief", item.composer)
        assertEquals(listOf("bell", "stief"), item.tags)
        assertEquals("Drive", item.collection)
        assertEquals("The Bell", repository.getScoreMetadata(item.id)?.title)
    }

    @Test
    fun repositoryApplyRemoteSyncKeepsDifferentProvidersAggregated() = runBlocking {
        val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())

        repository.initialize()
        repository.applyRemoteSync(
            providerId = "src_supabase",
            providerName = "Supabase",
            syncedAtEpochMillis = 100L,
            sourceId = "src_supabase",
            items = listOf(
                SyncedSheetMusicDescriptor(
                    remoteId = "supabase-1",
                    storedPath = "/scores/cloud-a.pdf",
                    originalFileName = "Cloud A.pdf",
                    title = "Cloud A",
                )
            ),
        )
        repository.applyRemoteSync(
            providerId = "src_google_drive",
            providerName = "Google Drive",
            syncedAtEpochMillis = 200L,
            sourceId = "src_google_drive",
            items = listOf(
                SyncedSheetMusicDescriptor(
                    remoteId = "drive-1",
                    storedPath = "/scores/cloud-b.pdf",
                    originalFileName = "Cloud B.pdf",
                    title = "Cloud B",
                )
            ),
        )

        val titles = repository.library.value.items.map { it.title }.sorted()
        assertEquals(listOf("Cloud A", "Cloud B"), titles)
    }

    @Test
    fun duplicateKeysAreCaseInsensitiveAndRequireMetadataPairing() {
        assertEquals(
            "moonlight sonata|beethoven",
            composerDuplicateKey(" Moonlight Sonata ", " Beethoven "),
        )
        assertEquals(
            "moonlight sonata|recital",
            collectionDuplicateKey(" Moonlight Sonata ", " Recital "),
        )
        assertNull(composerDuplicateKey("Moonlight Sonata", null))
        assertNull(collectionDuplicateKey("Moonlight Sonata", " "))
    }

    @Test
    fun repositorySkipsDuplicateLocalDocumentsByContentFingerprint() = runBlocking {
        val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())

        repository.initialize()
        val firstImport = repository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/moonlight-a.pdf",
                    originalFileName = "Moonlight Sonata.pdf",
                    contentFingerprint = "abc123",
                )
            )
        )
        val secondImport = repository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/moonlight-b.pdf",
                    originalFileName = "Moonlight Sonata Copy.pdf",
                    contentFingerprint = "abc123",
                )
            )
        )

        assertEquals(1, firstImport.importedItems.size)
        assertEquals(0, secondImport.importedItems.size)
        assertEquals(1, secondImport.skippedDocuments.size)
        assertEquals("Moonlight Sonata Copy.pdf", secondImport.skippedDocuments.single().originalFileName)
        assertEquals(1, repository.library.value.items.size)
    }

    @Test
    fun repositoryBackfillsLegacyFingerprintsDuringDuplicateCheck() = runBlocking {
        val repository = DefaultSheetMusicRepository(
            storage = InMemoryLibraryStorage(
                initialItems = listOf(
                    testItem(
                        id = "legacy",
                        title = "Moonlight Sonata",
                        composer = "Beethoven",
                        tags = emptyList(),
                        dateAddedEpochMillis = 1L,
                    )
                )
            ),
            storedDocumentFingerprinter = object : StoredDocumentFingerprinter {
                override suspend fun fingerprint(storedPath: String): String? = when (storedPath) {
                    "/scores/legacy.pdf" -> "legacy-hash"
                    else -> null
                }
            },
        )

        repository.initialize()
        val importResult = repository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/new-copy.pdf",
                    originalFileName = "Moonlight Duplicate.pdf",
                    contentFingerprint = "legacy-hash",
                )
            )
        )

        assertEquals(0, importResult.importedItems.size)
        assertEquals(1, importResult.skippedDocuments.size)
        assertEquals(
            "legacy-hash",
            repository.library.value.items.single().document.contentFingerprint,
        )
    }
}

private fun testItem(
    id: String,
    title: String,
    composer: String,
    tags: List<String>,
    collection: String? = null,
    isFavorite: Boolean = false,
    isHidden: Boolean = false,
    dateAddedEpochMillis: Long,
    lastOpenedEpochMillis: Long? = null,
    originalFileName: String = "$title.pdf",
) = com.ryanjames.lunar.library.model.SheetMusicItem(
    id = id,
    title = title,
    composer = composer,
    tags = tags,
    collection = collection,
    isFavorite = isFavorite,
    isHidden = isHidden,
    document = com.ryanjames.lunar.library.model.PdfDocumentReference(
        storedPath = "/scores/$id.pdf",
        originalFileName = originalFileName,
    ),
    dateAddedEpochMillis = dateAddedEpochMillis,
    lastOpenedEpochMillis = lastOpenedEpochMillis,
)

private suspend fun DefaultSheetMusicRepository.importSetlistFixtures(): List<String> = importDocuments(
    listOf("one", "two", "three").map { name ->
        ImportedPdfDescriptor(
            storedPath = "/scores/$name.pdf",
            originalFileName = "$name.pdf",
        )
    }
).importedItems.map { it.id }
