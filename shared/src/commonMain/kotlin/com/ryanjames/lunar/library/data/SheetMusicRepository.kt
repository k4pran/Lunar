package com.ryanjames.lunar.library.data

import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.library.model.SheetMusicItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.time.Clock

data class LibrarySnapshot(
    val items: List<SheetMusicItem> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.LocalOnly,
)

sealed interface SyncStatus {
    data object LocalOnly : SyncStatus

    data class Ready(
        val providerName: String,
        val lastSyncedEpochMillis: Long? = null,
    ) : SyncStatus
}

interface LibrarySyncGateway {
    suspend fun currentStatus(): SyncStatus
}

object LocalOnlySyncGateway : LibrarySyncGateway {
    override suspend fun currentStatus(): SyncStatus = SyncStatus.LocalOnly
}

interface StoredDocumentCleaner {
    suspend fun deleteStoredDocument(storedPath: String)
}

object NoOpStoredDocumentCleaner : StoredDocumentCleaner {
    override suspend fun deleteStoredDocument(storedPath: String) = Unit
}

class OkioStoredDocumentCleaner(
    private val fileSystem: FileSystem,
) : StoredDocumentCleaner {
    override suspend fun deleteStoredDocument(storedPath: String) {
        val path = storedPath.toPath()
        if (fileSystem.metadataOrNull(path) != null) {
            fileSystem.delete(path, mustExist = false)
        }
    }
}

data class SheetMusicMetadataInput(
    val title: String,
    val composer: String?,
    val tags: List<String>,
    val collection: String?,
    val isFavorite: Boolean,
)

interface SheetMusicRepository {
    val library: StateFlow<LibrarySnapshot>

    suspend fun initialize()

    suspend fun importDocuments(documents: List<ImportedPdfDescriptor>): List<SheetMusicItem>

    suspend fun updateMetadata(itemId: String, metadata: SheetMusicMetadataInput)

    suspend fun toggleFavorite(itemId: String)

    suspend fun recordOpened(itemId: String, pageIndex: Int)

    suspend fun updateCurrentPage(itemId: String, pageIndex: Int)

    suspend fun updatePageCount(itemId: String, pageCount: Int)

    suspend fun deleteItem(itemId: String)

    fun getItem(itemId: String): SheetMusicItem?
}

class DefaultSheetMusicRepository(
    private val storage: LibraryStorage,
    private val syncGateway: LibrarySyncGateway = LocalOnlySyncGateway,
    private val storedDocumentCleaner: StoredDocumentCleaner = NoOpStoredDocumentCleaner,
    private val clock: Clock = Clock.System,
) : SheetMusicRepository {
    private val mutationLock = Mutex()
    private val _library = MutableStateFlow(LibrarySnapshot())
    private var initialized = false

    override val library: StateFlow<LibrarySnapshot> = _library.asStateFlow()

    override suspend fun initialize() {
        mutationLock.withLock {
            if (initialized) {
                return
            }

            val items = storage.readItems()
            _library.value = LibrarySnapshot(
                items = items,
                syncStatus = syncGateway.currentStatus(),
            )
            initialized = true
        }
    }

    override suspend fun importDocuments(documents: List<ImportedPdfDescriptor>): List<SheetMusicItem> {
        ensureInitialized()
        if (documents.isEmpty()) {
            return emptyList()
        }

        val importedAt = clock.now().toEpochMilliseconds()
        val importedItems = documents.mapIndexed { index, document ->
            SheetMusicItem(
                id = buildStableId(importedAt, index),
                title = normalizeTitle(document.suggestedTitle, document.originalFileName),
                document = PdfDocumentReference(
                    storedPath = document.storedPath,
                    originalFileName = document.originalFileName,
                    sourceUri = document.sourceUri,
                ),
                pageCount = document.pageCount,
                dateAddedEpochMillis = importedAt + index,
            )
        }

        mutateItems { items -> items + importedItems }
        return importedItems
    }

    override suspend fun updateMetadata(itemId: String, metadata: SheetMusicMetadataInput) {
        ensureInitialized()

        mutateItem(itemId) { item ->
            item.copy(
                title = metadata.title.trim().ifEmpty { item.title },
                composer = metadata.composer?.trim()?.ifEmpty { null },
                tags = metadata.tags
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinctBy(String::lowercase)
                    .sortedWith(String.CASE_INSENSITIVE_ORDER),
                collection = metadata.collection?.trim()?.ifEmpty { null },
                isFavorite = metadata.isFavorite,
            )
        }
    }

    override suspend fun toggleFavorite(itemId: String) {
        ensureInitialized()
        mutateItem(itemId) { item -> item.copy(isFavorite = !item.isFavorite) }
    }

    override suspend fun recordOpened(itemId: String, pageIndex: Int) {
        ensureInitialized()
        val openedAt = clock.now().toEpochMilliseconds()
        mutateItem(itemId) { item ->
            item.copy(
                lastOpenedEpochMillis = openedAt,
                lastViewedPage = pageIndex.coerceAtLeast(0),
            )
        }
    }

    override suspend fun updateCurrentPage(itemId: String, pageIndex: Int) {
        ensureInitialized()
        mutateItem(itemId) { item -> item.copy(lastViewedPage = pageIndex.coerceAtLeast(0)) }
    }

    override suspend fun updatePageCount(itemId: String, pageCount: Int) {
        ensureInitialized()
        mutateItem(itemId) { item ->
            if (pageCount <= 0 || item.pageCount == pageCount) {
                item
            } else {
                item.copy(pageCount = pageCount)
            }
        }
    }

    override suspend fun deleteItem(itemId: String) {
        ensureInitialized()

        val item = getItem(itemId) ?: return
        runCatching {
            storedDocumentCleaner.deleteStoredDocument(item.document.storedPath)
        }

        mutateItems { items ->
            items.filterNot { existing -> existing.id == itemId }
        }
    }

    override fun getItem(itemId: String): SheetMusicItem? = _library.value.items.firstOrNull { it.id == itemId }

    private suspend fun ensureInitialized() {
        if (!initialized) {
            initialize()
        }
    }

    private suspend fun mutateItem(
        itemId: String,
        transform: (SheetMusicItem) -> SheetMusicItem,
    ) {
        mutateItems { items ->
            items.map { item ->
                if (item.id == itemId) transform(item) else item
            }
        }
    }

    private suspend fun mutateItems(
        transform: (List<SheetMusicItem>) -> List<SheetMusicItem>,
    ) {
        mutationLock.withLock {
            val updatedItems = transform(_library.value.items)
            storage.writeItems(updatedItems)
            _library.value = _library.value.copy(items = updatedItems)
        }
    }

    private fun buildStableId(importedAt: Long, index: Int): String =
        "${importedAt}_${index}_${Random.nextInt(1000, 9999)}"
}

private fun normalizeTitle(
    suggestedTitle: String,
    originalFileName: String,
): String = suggestedTitle.trim()
    .ifEmpty { originalFileName.substringBeforeLast('.') }
    .ifEmpty { "Untitled score" }
