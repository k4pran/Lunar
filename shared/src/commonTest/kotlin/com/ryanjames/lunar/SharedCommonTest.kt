package com.ryanjames.lunar

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
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.ScoreMetadata
import com.ryanjames.lunar.library.model.ScoreMetadataComposer
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.library.model.applyLibraryQuery
import com.ryanjames.lunar.library.model.collectionDuplicateKey
import com.ryanjames.lunar.library.model.composerDuplicateKey
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedCommonTest {
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
        originalFileName = "$title.pdf",
    ),
    dateAddedEpochMillis = dateAddedEpochMillis,
    lastOpenedEpochMillis = lastOpenedEpochMillis,
)
