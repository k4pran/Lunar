package com.ryanjames.lunar

import com.ryanjames.lunar.library.data.DefaultSheetMusicRepository
import com.ryanjames.lunar.library.data.InMemoryLibraryStorage
import com.ryanjames.lunar.library.data.SheetMusicMetadataInput
import com.ryanjames.lunar.library.data.SyncedSheetMusicDescriptor
import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import com.ryanjames.lunar.library.model.LibraryQuery
import com.ryanjames.lunar.library.model.LibrarySortOption
import com.ryanjames.lunar.library.model.SortDirection
import com.ryanjames.lunar.library.model.applyLibraryQuery
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
        )

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
        )

        repository.deleteItem(imported.single().id)

        assertNull(repository.getItem(imported.single().id))
        assertEquals(emptyList(), repository.library.value.items)
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
