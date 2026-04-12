package com.ryanjames.lunar.library.data

import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.library.model.RemoteSyncMetadata
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

    data class Syncing(
        val providerName: String,
    ) : SyncStatus

    data class Ready(
        val providerName: String,
        val lastSyncedEpochMillis: Long? = null,
    ) : SyncStatus

    data class Error(
        val providerName: String,
        val message: String,
        val lastAttemptEpochMillis: Long? = null,
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

data class SyncedSheetMusicDescriptor(
    val remoteId: String,
    val remoteVersion: String? = null,
    val storedPath: String,
    val originalFileName: String,
    val sourceUri: String? = null,
    val title: String,
    val composer: String? = null,
    val tags: List<String> = emptyList(),
    val collection: String? = null,
    val pageCount: Int? = null,
    val dateAddedEpochMillis: Long? = null,
)

interface SheetMusicRepository {
    val library: StateFlow<LibrarySnapshot>

    suspend fun initialize()

    suspend fun importDocuments(documents: List<ImportedPdfDescriptor>): List<SheetMusicItem>

    suspend fun importDocumentsForSource(
        sourceId: String?,
        documents: List<ImportedPdfDescriptor>,
    ): List<SheetMusicItem>

    suspend fun removeItemsBySource(sourceId: String)

    suspend fun updateMetadata(itemId: String, metadata: SheetMusicMetadataInput)

    suspend fun toggleFavorite(itemId: String)

    suspend fun recordOpened(itemId: String, pageIndex: Int)

    suspend fun updateCurrentPage(itemId: String, pageIndex: Int)

    suspend fun updatePageCount(itemId: String, pageCount: Int)

    suspend fun deleteItem(itemId: String)

    suspend fun applyRemoteSync(
        providerId: String,
        providerName: String,
        items: List<SyncedSheetMusicDescriptor>,
        syncedAtEpochMillis: Long,
        sourceId: String? = null,
    )

    suspend fun updateSyncStatus(syncStatus: SyncStatus)

    fun getItem(itemId: String): SheetMusicItem?

    fun itemCountForSource(sourceId: String): Int
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
        return importDocumentsForSource(sourceId = null, documents = documents)
    }

    override suspend fun importDocumentsForSource(
        sourceId: String?,
        documents: List<ImportedPdfDescriptor>,
    ): List<SheetMusicItem> {
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
                sourceId = sourceId,
            )
        }

        mutateItems { items -> items + importedItems }
        return importedItems
    }

    override suspend fun removeItemsBySource(sourceId: String) {
        ensureInitialized()

        val toRemove = _library.value.items.filter { it.sourceId == sourceId }
        toRemove.forEach { item ->
            runCatching { storedDocumentCleaner.deleteStoredDocument(item.document.storedPath) }
        }

        mutateItems { items ->
            items.filterNot { it.sourceId == sourceId }
        }
    }

    override suspend fun updateMetadata(itemId: String, metadata: SheetMusicMetadataInput) {
        ensureInitialized()

        mutateItem(itemId) { item ->
            item.copy(
                title = metadata.title.trim().ifEmpty { item.title },
                composer = metadata.composer?.trim()?.ifEmpty { null },
                tags = normalizeTags(metadata.tags),
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

    override suspend fun applyRemoteSync(
        providerId: String,
        providerName: String,
        items: List<SyncedSheetMusicDescriptor>,
        syncedAtEpochMillis: Long,
        sourceId: String?,
    ) {
        ensureInitialized()

        val stalePaths = mutableListOf<String>()

        mutationLock.withLock {
            val existingItems = _library.value.items
            val existingRemoteItems = existingItems.filter { it.syncMetadata?.providerId == providerId }
            val existingRemoteById = existingRemoteItems.associateBy { it.syncMetadata?.remoteId.orEmpty() }
            val keptLocalItems = existingItems.filterNot { it.syncMetadata?.providerId == providerId }
            val incomingRemoteIds = items.mapTo(mutableSetOf()) { it.remoteId }

            val syncedItems = items.map { descriptor ->
                val existing = existingRemoteById[descriptor.remoteId]
                val nextDocument = PdfDocumentReference(
                    storedPath = descriptor.storedPath,
                    originalFileName = descriptor.originalFileName,
                    sourceUri = descriptor.sourceUri,
                )

                if (existing != null && existing.document.storedPath != descriptor.storedPath) {
                    stalePaths += existing.document.storedPath
                }

                existing?.copy(
                    title = descriptor.title,
                    composer = descriptor.composer,
                    tags = normalizeTags(descriptor.tags),
                    collection = descriptor.collection?.trim()?.ifEmpty { null },
                    document = nextDocument,
                    pageCount = descriptor.pageCount ?: existing.pageCount,
                    syncMetadata = RemoteSyncMetadata(
                        providerId = providerId,
                        providerName = providerName,
                        remoteId = descriptor.remoteId,
                        remoteVersion = descriptor.remoteVersion,
                        lastSyncedEpochMillis = syncedAtEpochMillis,
                    ),
                ) ?: SheetMusicItem(
                    id = buildRemoteItemId(providerId, descriptor.remoteId),
                    title = descriptor.title,
                    composer = descriptor.composer,
                    tags = normalizeTags(descriptor.tags),
                    collection = descriptor.collection?.trim()?.ifEmpty { null },
                    document = nextDocument,
                    pageCount = descriptor.pageCount,
                    dateAddedEpochMillis = descriptor.dateAddedEpochMillis ?: syncedAtEpochMillis,
                    syncMetadata = RemoteSyncMetadata(
                        providerId = providerId,
                        providerName = providerName,
                        remoteId = descriptor.remoteId,
                        remoteVersion = descriptor.remoteVersion,
                        lastSyncedEpochMillis = syncedAtEpochMillis,
                    ),
                    sourceId = sourceId,
                )
            }

            existingRemoteItems
                .filter { existing -> existing.syncMetadata?.remoteId !in incomingRemoteIds }
                .forEach { stalePaths += it.document.storedPath }

            val updatedItems = keptLocalItems + syncedItems
            storage.writeItems(updatedItems)
            _library.value = _library.value.copy(items = updatedItems)
        }

        stalePaths
            .distinct()
            .forEach { stalePath ->
                runCatching { storedDocumentCleaner.deleteStoredDocument(stalePath) }
            }
    }

    override suspend fun updateSyncStatus(syncStatus: SyncStatus) {
        ensureInitialized()
        mutationLock.withLock {
            _library.value = _library.value.copy(syncStatus = syncStatus)
        }
    }

    override fun getItem(itemId: String): SheetMusicItem? = _library.value.items.firstOrNull { it.id == itemId }

    override fun itemCountForSource(sourceId: String): Int =
        _library.value.items.count { it.sourceId == sourceId }

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

    private fun buildRemoteItemId(
        providerId: String,
        remoteId: String,
    ): String = "remote_${providerId}_${remoteId}"
}

private fun normalizeTitle(
    suggestedTitle: String,
    originalFileName: String,
): String = suggestedTitle.trim()
    .ifEmpty { originalFileName.substringBeforeLast('.') }
    .ifEmpty { "Untitled score" }

private fun normalizeTags(tags: List<String>): List<String> = tags
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(String::lowercase)
    .sortedWith(String.CASE_INSENSITIVE_ORDER)
