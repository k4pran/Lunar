package com.ryanjames.lunar

import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.InMemoryLibraryStorage
import com.ryanjames.lunar.library.data.SheetMusicMetadataInput
import com.ryanjames.lunar.library.data.SyncedSheetMusicDescriptor
import com.ryanjames.lunar.library.data.StoredDocumentFingerprinter
import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import com.ryanjames.lunar.library.model.LibraryQuery
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.library.model.applyLibraryQuery
import com.ryanjames.lunar.library.model.collectionDuplicateKey
import com.ryanjames.lunar.library.model.composerDuplicateKey
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SharedCommonTest {
    @Test
    fun repositoryImportsAndUpdatesMetadata() = runBlocking {
        val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())

        repository.initialize()
        val imported = repository.importDocuments(
            listOf(
                ImportedPdfDescriptor(
                    storedPath = "/scores/moonlight-sonata.pdf",
                    originalFileName = "Moonlight Sonata.pdf",
                    pageCount = 6,
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
    fun repositoryDeleteRemovesStoredItem() = runBlocking {
        val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())

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
        assertEquals(emptyList(), repository.library.value.items)
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
    fun repositoryApplyRemoteSyncUpsertsProviderItems() = runBlocking {
        val repository = DefaultSheetMusicRepository(InMemoryLibraryStorage())

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
    dateAddedEpochMillis: Long,
    lastOpenedEpochMillis: Long? = null,
) = com.ryanjames.lunar.library.model.SheetMusicItem(
    id = id,
    title = title,
    composer = composer,
    tags = tags,
    collection = collection,
    isFavorite = isFavorite,
    document = com.ryanjames.lunar.library.model.PdfDocumentReference(
        storedPath = "/scores/$id.pdf",
        originalFileName = "$title.pdf",
    ),
    dateAddedEpochMillis = dateAddedEpochMillis,
    lastOpenedEpochMillis = lastOpenedEpochMillis,
)
