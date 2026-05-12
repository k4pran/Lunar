package com.ryanjames.lunar.library.data

import com.ryanjames.lunar.library.model.ImportedPdfDescriptor
import com.ryanjames.lunar.library.model.LibrarySongbook
import com.ryanjames.lunar.library.model.LibrarySetlist
import com.ryanjames.lunar.library.model.PdfDocumentReference
import com.ryanjames.lunar.library.model.RemoteSyncMetadata
import com.ryanjames.lunar.library.model.ScoreMetadata
import com.ryanjames.lunar.library.model.ScoreMetadataComposer
import com.ryanjames.lunar.library.model.ScoreMetadataSource
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
    val setlists: List<LibrarySetlist> = emptyList(),
    val songbooks: List<LibrarySongbook> = emptyList(),
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

interface StoredDocumentFingerprinter {
    suspend fun fingerprint(storedPath: String): String?
}

object NoOpStoredDocumentFingerprinter : StoredDocumentFingerprinter {
    override suspend fun fingerprint(storedPath: String): String? = null
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
    val scoreMetadata: ScoreMetadata? = null,
)

data class SkippedImportDocument(
    val originalFileName: String,
    val reason: String,
)

data class DocumentImportResult(
    val importedItems: List<SheetMusicItem> = emptyList(),
    val skippedDocuments: List<SkippedImportDocument> = emptyList(),
)

interface SheetMusicRepository {
    val library: StateFlow<LibrarySnapshot>

    suspend fun initialize()

    suspend fun importDocuments(documents: List<ImportedPdfDescriptor>): DocumentImportResult

    suspend fun importDocumentsForSource(
        sourceId: String?,
        documents: List<ImportedPdfDescriptor>,
    ): DocumentImportResult

    suspend fun removeItemsBySource(sourceId: String)

    suspend fun updateMetadata(itemId: String, metadata: SheetMusicMetadataInput)

    suspend fun toggleFavorite(itemId: String)

    suspend fun updateHidden(itemId: String, isHidden: Boolean)

    suspend fun recordOpened(itemId: String, pageIndex: Int)

    suspend fun updateCurrentPage(itemId: String, pageIndex: Int)

    suspend fun updatePageCount(itemId: String, pageCount: Int)

    suspend fun deleteItem(itemId: String)

    suspend fun createSetlist(name: String, itemIds: List<String>): LibrarySetlist

    suspend fun addItemsToSetlist(setlistId: String, itemIds: List<String>): LibrarySetlist

    suspend fun deleteSetlist(setlistId: String)

    suspend fun createSongbook(
        name: String,
        itemIds: List<String>,
        document: PdfDocumentReference,
        pageCount: Int? = null,
    ): LibrarySongbook

    suspend fun addItemsToSongbook(
        songbookId: String,
        itemIds: List<String>,
        document: PdfDocumentReference,
        pageCount: Int? = null,
    ): LibrarySongbook

    suspend fun deleteSongbook(songbookId: String)

    suspend fun applyRemoteSync(
        providerId: String,
        providerName: String,
        items: List<SyncedSheetMusicDescriptor>,
        syncedAtEpochMillis: Long,
        sourceId: String? = null,
    )

    suspend fun updateSyncStatus(syncStatus: SyncStatus)

    suspend fun getScoreMetadata(itemId: String): ScoreMetadata?

    fun getItem(itemId: String): SheetMusicItem?

    fun getSetlist(setlistId: String): LibrarySetlist?

    fun getSongbook(songbookId: String): LibrarySongbook?

    fun itemCountForSource(sourceId: String): Int
}

class DefaultSheetMusicRepository(
    private val storage: LibraryStorage,
    private val scoreMetadataStorage: ScoreMetadataStorage = InMemoryScoreMetadataStorage(),
    private val syncGateway: LibrarySyncGateway = LocalOnlySyncGateway,
    private val storedDocumentCleaner: StoredDocumentCleaner = NoOpStoredDocumentCleaner,
    private val storedDocumentFingerprinter: StoredDocumentFingerprinter = NoOpStoredDocumentFingerprinter,
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

            val storedLibrary = storage.readLibraryData()
            val synchronizedItems = synchronizeItemsWithStoredMetadata(storedLibrary.items)
            val sanitizedSetlists = sanitizeSetlists(
                setlists = storedLibrary.setlists,
                items = synchronizedItems,
            )
            val sanitizedSongbooks = sanitizeSongbooks(storedLibrary.songbooks)
            if (
                synchronizedItems != storedLibrary.items ||
                sanitizedSetlists != storedLibrary.setlists ||
                sanitizedSongbooks != storedLibrary.songbooks
            ) {
                storage.writeLibraryData(
                    storedLibrary.copy(
                        items = synchronizedItems,
                        setlists = sanitizedSetlists,
                        songbooks = sanitizedSongbooks,
                    )
                )
            }
            _library.value = LibrarySnapshot(
                items = synchronizedItems,
                setlists = sanitizedSetlists,
                songbooks = sanitizedSongbooks,
                syncStatus = syncGateway.currentStatus(),
            )
            initialized = true
        }
    }

    override suspend fun importDocuments(documents: List<ImportedPdfDescriptor>): DocumentImportResult {
        return importDocumentsForSource(sourceId = null, documents = documents)
    }

    override suspend fun importDocumentsForSource(
        sourceId: String?,
        documents: List<ImportedPdfDescriptor>,
    ): DocumentImportResult {
        ensureInitialized()
        if (documents.isEmpty()) {
            return DocumentImportResult()
        }

        val importedAt = clock.now().toEpochMilliseconds()
        val existingFingerprints = loadExistingFingerprints()
        val importedFingerprints = mutableSetOf<String>()
        val stalePaths = mutableListOf<String>()
        val skippedDocuments = mutableListOf<SkippedImportDocument>()
        val importedRecords = buildList {
            documents.forEach { document ->
                val fingerprint = normalizeContentFingerprint(document.contentFingerprint)
                val duplicateReason = when {
                    fingerprint != null && fingerprint in existingFingerprints ->
                        "matching file contents already exist in the library."

                    fingerprint != null && !importedFingerprints.add(fingerprint) ->
                        "matching file contents were already selected in this import."

                    else -> null
                }

                if (duplicateReason != null) {
                    skippedDocuments += SkippedImportDocument(
                        originalFileName = document.originalFileName,
                        reason = duplicateReason,
                    )
                    stalePaths += document.storedPath
                    return@forEach
                }

                val importIndex = size
                val itemId = buildStableId(importedAt, importIndex)
                val fallbackTitle = normalizeTitle(document.suggestedTitle, document.originalFileName)
                val resolvedMetadata = resolveImportedScoreMetadata(
                    itemId = itemId,
                    storedMetadata = null,
                    incomingMetadata = document.scoreMetadata,
                    fallbackTitle = fallbackTitle,
                    fallbackComposer = null,
                    fallbackTags = emptyList(),
                    originalFileName = document.originalFileName,
                    sourceUri = document.sourceUri,
                    pageCount = document.pageCount,
                    contentFingerprint = fingerprint,
                )
                val libraryFields = metadataFieldsForLibraryItem(
                    metadata = resolvedMetadata,
                    originalFileName = document.originalFileName,
                )
                add(
                    ImportedItemRecord(
                        item = SheetMusicItem(
                            id = itemId,
                            title = libraryFields.title,
                            composer = libraryFields.composer,
                            tags = libraryFields.tags,
                            document = PdfDocumentReference(
                                storedPath = document.storedPath,
                                originalFileName = document.originalFileName,
                                sourceUri = document.sourceUri,
                                contentFingerprint = fingerprint,
                            ),
                            pageCount = libraryFields.pageCount,
                            dateAddedEpochMillis = importedAt + importIndex,
                            sourceId = sourceId,
                        ),
                        metadata = resolvedMetadata,
                    )
                )
            }
        }
        val importedItems = importedRecords.map(ImportedItemRecord::item)

        mutateItems { items -> items + importedItems }
        persistMetadataRecords(importedRecords)
        stalePaths
            .distinct()
            .forEach { stalePath ->
                runCatching { storedDocumentCleaner.deleteStoredDocument(stalePath) }
            }

        return DocumentImportResult(
            importedItems = importedItems,
            skippedDocuments = skippedDocuments,
        )
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
        toRemove.forEach { item ->
            runCatching { scoreMetadataStorage.deleteMetadata(item.id) }
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
        getItem(itemId)?.let { item ->
            val existingMetadata = scoreMetadataStorage.readMetadata(item.id)
            persistMetadataRecord(
                itemId = item.id,
                metadata = metadataForLibraryItem(
                    item = item,
                    metadataId = existingMetadata?.id,
                ),
            )
        }
    }

    override suspend fun toggleFavorite(itemId: String) {
        ensureInitialized()
        mutateItem(itemId) { item -> item.copy(isFavorite = !item.isFavorite) }
    }

    override suspend fun updateHidden(itemId: String, isHidden: Boolean) {
        ensureInitialized()
        mutateItem(itemId) { item ->
            if (item.isHidden == isHidden) item else item.copy(isHidden = isHidden)
        }
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
        getItem(itemId)?.let { item ->
            val existingMetadata = scoreMetadataStorage.readMetadata(item.id)
            persistMetadataRecord(
                itemId = item.id,
                metadata = metadataForLibraryItem(
                    item = item,
                    metadataId = existingMetadata?.id,
                ),
            )
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
        runCatching { scoreMetadataStorage.deleteMetadata(itemId) }
    }

    override suspend fun createSetlist(name: String, itemIds: List<String>): LibrarySetlist {
        ensureInitialized()

        val createdAt = clock.now().toEpochMilliseconds()
        var createdSetlist: LibrarySetlist? = null
        mutateSetlists { setlists, items ->
            val normalizedName = normalizeSetlistName(name)
                ?: error("Setlist name cannot be blank.")
            val validItemIds = items.mapTo(mutableSetOf()) { it.id }
            val nextSetlist = LibrarySetlist(
                id = buildSetlistId(createdAt),
                name = normalizedName,
                itemIds = sanitizeSetlistItemIds(itemIds, validItemIds),
                createdAtEpochMillis = createdAt,
                updatedAtEpochMillis = createdAt,
            )
            createdSetlist = nextSetlist
            setlists + nextSetlist
        }
        return requireNotNull(createdSetlist)
    }

    override suspend fun addItemsToSetlist(
        setlistId: String,
        itemIds: List<String>,
    ): LibrarySetlist {
        ensureInitialized()

        val updatedAt = clock.now().toEpochMilliseconds()
        var updatedSetlist: LibrarySetlist? = null
        mutateSetlists { setlists, items ->
            val validItemIds = items.mapTo(mutableSetOf()) { it.id }
            val sanitizedItems = sanitizeSetlistItemIds(itemIds, validItemIds)
            setlists.map { setlist ->
                if (setlist.id != setlistId) {
                    setlist
                } else {
                    setlist.copy(
                        itemIds = (setlist.itemIds + sanitizedItems).distinct(),
                        updatedAtEpochMillis = updatedAt,
                    ).also { updatedSetlist = it }
                }
            }.also {
                if (updatedSetlist == null) {
                    error("Setlist not found.")
                }
            }
        }
        return requireNotNull(updatedSetlist)
    }

    override suspend fun deleteSetlist(setlistId: String) {
        ensureInitialized()
        mutateSetlists { setlists, _ ->
            setlists.filterNot { setlist -> setlist.id == setlistId }
        }
    }

    override suspend fun createSongbook(
        name: String,
        itemIds: List<String>,
        document: PdfDocumentReference,
        pageCount: Int?,
    ): LibrarySongbook {
        ensureInitialized()

        val createdAt = clock.now().toEpochMilliseconds()
        var createdSongbook: LibrarySongbook? = null
        mutateSongbooks { songbooks, _ ->
            val normalizedName = normalizeSongbookName(name)
                ?: error("Songbook name cannot be blank.")
            val nextSongbook = LibrarySongbook(
                id = buildSongbookId(createdAt),
                name = normalizedName,
                itemIds = sanitizeSongbookItemIds(itemIds),
                document = sanitizeSongbookDocument(document)
                    ?: error("Songbook PDF is unavailable."),
                createdAtEpochMillis = createdAt,
                updatedAtEpochMillis = createdAt,
                pageCount = pageCount?.takeIf { it > 0 },
            )
            createdSongbook = nextSongbook
            songbooks + nextSongbook
        }
        return requireNotNull(createdSongbook)
    }

    override suspend fun addItemsToSongbook(
        songbookId: String,
        itemIds: List<String>,
        document: PdfDocumentReference,
        pageCount: Int?,
    ): LibrarySongbook {
        ensureInitialized()

        val updatedAt = clock.now().toEpochMilliseconds()
        val stalePaths = mutableListOf<String>()
        var updatedSongbook: LibrarySongbook? = null
        mutateSongbooks { songbooks, _ ->
            val sanitizedDocument = sanitizeSongbookDocument(document)
                ?: error("Songbook PDF is unavailable.")
            val sanitizedItems = sanitizeSongbookItemIds(itemIds)
            songbooks.map { songbook ->
                if (songbook.id != songbookId) {
                    songbook
                } else {
                    if (songbook.document.storedPath != sanitizedDocument.storedPath) {
                        stalePaths += songbook.document.storedPath
                    }
                    songbook.copy(
                        itemIds = (songbook.itemIds + sanitizedItems).distinct(),
                        document = sanitizedDocument,
                        updatedAtEpochMillis = updatedAt,
                        pageCount = pageCount?.takeIf { it > 0 } ?: songbook.pageCount,
                    ).also { updatedSongbook = it }
                }
            }.also {
                if (updatedSongbook == null) {
                    error("Songbook not found.")
                }
            }
        }

        stalePaths
            .distinct()
            .forEach { stalePath ->
                runCatching { storedDocumentCleaner.deleteStoredDocument(stalePath) }
            }

        return requireNotNull(updatedSongbook)
    }

    override suspend fun deleteSongbook(songbookId: String) {
        ensureInitialized()

        val songbook = getSongbook(songbookId) ?: return
        runCatching {
            storedDocumentCleaner.deleteStoredDocument(songbook.document.storedPath)
        }

        mutateSongbooks { songbooks, _ ->
            songbooks.filterNot { existing -> existing.id == songbookId }
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
        val removedRemoteItemIds = mutableListOf<String>()
        val syncedMetadataRecords = mutableListOf<ImportedItemRecord>()

        mutationLock.withLock {
            val existingItems = _library.value.items
            val existingRemoteItems = existingItems.filter { it.syncMetadata?.providerId == providerId }
            val existingRemoteById = existingRemoteItems.associateBy { it.syncMetadata?.remoteId.orEmpty() }
            val keptLocalItems = existingItems.filterNot { it.syncMetadata?.providerId == providerId }
            val incomingRemoteIds = items.mapTo(mutableSetOf()) { it.remoteId }

            val syncedItems = items.map { descriptor ->
                val existing = existingRemoteById[descriptor.remoteId]
                val itemId = existing?.id ?: buildRemoteItemId(providerId, descriptor.remoteId)
                val nextDocument = PdfDocumentReference(
                    storedPath = descriptor.storedPath,
                    originalFileName = descriptor.originalFileName,
                    sourceUri = descriptor.sourceUri,
                )
                val resolvedMetadata = resolveImportedScoreMetadata(
                    itemId = itemId,
                    storedMetadata = scoreMetadataStorage.readMetadata(itemId),
                    incomingMetadata = descriptor.scoreMetadata,
                    fallbackTitle = normalizeTitle(descriptor.title, descriptor.originalFileName),
                    fallbackComposer = descriptor.composer,
                    fallbackTags = descriptor.tags,
                    originalFileName = descriptor.originalFileName,
                    sourceUri = descriptor.sourceUri,
                    pageCount = descriptor.pageCount ?: existing?.pageCount,
                    contentFingerprint = existing?.document?.contentFingerprint,
                )
                val libraryFields = metadataFieldsForLibraryItem(
                    metadata = resolvedMetadata,
                    originalFileName = descriptor.originalFileName,
                )
                val resolvedCollection = descriptor.collection?.trim()?.ifEmpty { null }
                    ?: existing?.collection?.trim()?.ifEmpty { null }

                if (existing != null && existing.document.storedPath != descriptor.storedPath) {
                    stalePaths += existing.document.storedPath
                }

                val syncedItem = existing?.copy(
                    title = libraryFields.title,
                    composer = libraryFields.composer,
                    tags = libraryFields.tags,
                    collection = resolvedCollection,
                    document = nextDocument,
                    pageCount = libraryFields.pageCount ?: existing.pageCount,
                    syncMetadata = RemoteSyncMetadata(
                        providerId = providerId,
                        providerName = providerName,
                        remoteId = descriptor.remoteId,
                        remoteVersion = descriptor.remoteVersion,
                        lastSyncedEpochMillis = syncedAtEpochMillis,
                    ),
                ) ?: SheetMusicItem(
                    id = itemId,
                    title = libraryFields.title,
                    composer = libraryFields.composer,
                    tags = libraryFields.tags,
                    collection = resolvedCollection,
                    document = nextDocument,
                    pageCount = libraryFields.pageCount,
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
                syncedMetadataRecords += ImportedItemRecord(
                    item = syncedItem,
                    metadata = resolvedMetadata,
                )
                syncedItem
            }

            existingRemoteItems
                .filter { existing -> existing.syncMetadata?.remoteId !in incomingRemoteIds }
                .forEach {
                    stalePaths += it.document.storedPath
                    removedRemoteItemIds += it.id
                }

            val updatedItems = keptLocalItems + syncedItems
            val updatedSetlists = sanitizeSetlists(_library.value.setlists, updatedItems)
            val updatedSongbooks = sanitizeSongbooks(_library.value.songbooks)
            storage.writeLibraryData(
                StoredLibraryData(
                    items = updatedItems,
                    setlists = updatedSetlists,
                    songbooks = updatedSongbooks,
                )
            )
            _library.value = _library.value.copy(
                items = updatedItems,
                setlists = updatedSetlists,
                songbooks = updatedSongbooks,
            )
        }

        stalePaths
            .distinct()
            .forEach { stalePath ->
                runCatching { storedDocumentCleaner.deleteStoredDocument(stalePath) }
            }
        persistMetadataRecords(syncedMetadataRecords)
        removedRemoteItemIds
            .distinct()
            .forEach { itemId ->
                runCatching { scoreMetadataStorage.deleteMetadata(itemId) }
            }
    }

    override suspend fun updateSyncStatus(syncStatus: SyncStatus) {
        ensureInitialized()
        mutationLock.withLock {
            _library.value = _library.value.copy(syncStatus = syncStatus)
        }
    }

    override suspend fun getScoreMetadata(itemId: String): ScoreMetadata? {
        ensureInitialized()
        val item = getItem(itemId) ?: return null
        val existingMetadata = scoreMetadataStorage.readMetadata(itemId)
        val resolvedMetadata = resolveImportedScoreMetadata(
            itemId = item.id,
            storedMetadata = existingMetadata,
            incomingMetadata = null,
            fallbackTitle = normalizeTitle(item.title, item.document.originalFileName),
            fallbackComposer = item.composer,
            fallbackTags = item.tags,
            originalFileName = item.document.originalFileName,
            sourceUri = item.document.sourceUri,
            pageCount = item.pageCount,
            contentFingerprint = item.document.contentFingerprint,
        )
        persistMetadataRecord(itemId = itemId, metadata = resolvedMetadata)
        return resolvedMetadata
    }

    override fun getItem(itemId: String): SheetMusicItem? = _library.value.items.firstOrNull { it.id == itemId }

    override fun getSetlist(setlistId: String): LibrarySetlist? =
        _library.value.setlists.firstOrNull { it.id == setlistId }

    override fun getSongbook(songbookId: String): LibrarySongbook? =
        _library.value.songbooks.firstOrNull { it.id == songbookId }

    override fun itemCountForSource(sourceId: String): Int =
        _library.value.items.count { it.sourceId == sourceId }

    private suspend fun ensureInitialized() {
        if (!initialized) {
            initialize()
        }
    }

    private suspend fun loadExistingFingerprints(): Set<String> {
        val fingerprintBackfills = mutableMapOf<String, String>()
        val fingerprints = _library.value.items.mapNotNullTo(mutableSetOf()) { item ->
            val normalized = normalizeContentFingerprint(item.document.contentFingerprint)
                ?: normalizeContentFingerprint(
                    storedDocumentFingerprinter.fingerprint(item.document.storedPath)
                )?.also { fingerprintBackfills[item.id] = it }
            normalized
        }
        if (fingerprintBackfills.isNotEmpty()) {
            mutateItems { items ->
                items.map { item ->
                    fingerprintBackfills[item.id]?.let { fingerprint ->
                        item.copy(
                            document = item.document.copy(contentFingerprint = fingerprint)
                        )
                    } ?: item
                }
            }
        }
        return fingerprints
    }

    private suspend fun synchronizeItemsWithStoredMetadata(items: List<SheetMusicItem>): List<SheetMusicItem> =
        items.map { item ->
            val resolvedMetadata = resolveImportedScoreMetadata(
                itemId = item.id,
                storedMetadata = scoreMetadataStorage.readMetadata(item.id),
                incomingMetadata = null,
                fallbackTitle = normalizeTitle(item.title, item.document.originalFileName),
                fallbackComposer = item.composer,
                fallbackTags = item.tags,
                originalFileName = item.document.originalFileName,
                sourceUri = item.document.sourceUri,
                pageCount = item.pageCount,
                contentFingerprint = item.document.contentFingerprint,
            )
            scoreMetadataStorage.writeMetadata(item.id, resolvedMetadata)
            applyMetadataToLibraryItem(item, resolvedMetadata)
        }

    private suspend fun persistMetadataRecords(records: List<ImportedItemRecord>) {
        records.forEach { record ->
            persistMetadataRecord(
                itemId = record.item.id,
                metadata = record.metadata,
            )
        }
    }

    private suspend fun persistMetadataRecord(
        itemId: String,
        metadata: ScoreMetadata,
    ) {
        val mergedMetadata = mergeScoreMetadata(
            existingMetadata = scoreMetadataStorage.readMetadata(itemId),
            incomingMetadata = metadata,
        )
        scoreMetadataStorage.writeMetadata(itemId, mergedMetadata)
    }

    private fun resolveImportedScoreMetadata(
        itemId: String,
        storedMetadata: ScoreMetadata?,
        incomingMetadata: ScoreMetadata?,
        fallbackTitle: String,
        fallbackComposer: String?,
        fallbackTags: List<String>,
        originalFileName: String,
        sourceUri: String?,
        pageCount: Int?,
        contentFingerprint: String?,
    ): ScoreMetadata {
        val fallbackMetadata = defaultScoreMetadata(
            itemId = itemId,
            title = fallbackTitle,
            composer = fallbackComposer,
            tags = fallbackTags,
            originalFileName = originalFileName,
            sourceUri = sourceUri,
            pageCount = pageCount,
            contentFingerprint = contentFingerprint,
        )
        val storedResolvedMetadata = mergeScoreMetadata(
            existingMetadata = fallbackMetadata,
            incomingMetadata = storedMetadata,
        )
        return mergeScoreMetadata(
            existingMetadata = storedResolvedMetadata,
            incomingMetadata = incomingMetadata,
        )
    }

    private fun metadataForLibraryItem(
        item: SheetMusicItem,
        metadataId: String? = null,
    ): ScoreMetadata = defaultScoreMetadata(
        itemId = item.id,
        title = item.title,
        composer = item.composer,
        tags = item.tags,
        originalFileName = item.document.originalFileName,
        sourceUri = item.document.sourceUri,
        pageCount = item.pageCount,
        contentFingerprint = item.document.contentFingerprint,
        metadataId = metadataId,
    )

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
        mutateLibrary { snapshot ->
            val updatedItems = transform(snapshot.items)
            snapshot.copy(
                items = updatedItems,
                setlists = sanitizeSetlists(snapshot.setlists, updatedItems),
                songbooks = sanitizeSongbooks(snapshot.songbooks),
            )
        }
    }

    private suspend fun mutateSetlists(
        transform: (List<LibrarySetlist>, List<SheetMusicItem>) -> List<LibrarySetlist>,
    ) {
        mutateLibrary { snapshot ->
            snapshot.copy(
                setlists = sanitizeSetlists(
                    setlists = transform(snapshot.setlists, snapshot.items),
                    items = snapshot.items,
                )
            )
        }
    }

    private suspend fun mutateSongbooks(
        transform: (List<LibrarySongbook>, List<SheetMusicItem>) -> List<LibrarySongbook>,
    ) {
        mutateLibrary { snapshot ->
            snapshot.copy(
                songbooks = sanitizeSongbooks(
                    transform(snapshot.songbooks, snapshot.items)
                )
            )
        }
    }

    private suspend fun mutateLibrary(
        transform: (LibrarySnapshot) -> LibrarySnapshot,
    ) {
        mutationLock.withLock {
            val updatedLibrary = transform(_library.value)
            storage.writeLibraryData(
                StoredLibraryData(
                    items = updatedLibrary.items,
                    setlists = updatedLibrary.setlists,
                    songbooks = updatedLibrary.songbooks,
                )
            )
            _library.value = updatedLibrary
        }
    }

    private fun buildStableId(importedAt: Long, index: Int): String =
        "${importedAt}_${index}_${Random.nextInt(1000, 9999)}"

    private fun buildSetlistId(createdAt: Long): String =
        "setlist_${createdAt}_${Random.nextInt(1000, 9999)}"

    private fun buildSongbookId(createdAt: Long): String =
        "songbook_${createdAt}_${Random.nextInt(1000, 9999)}"

    private fun buildRemoteItemId(
        providerId: String,
        remoteId: String,
    ): String = "remote_${providerId}_${remoteId}"
}

private data class ImportedItemRecord(
    val item: SheetMusicItem,
    val metadata: ScoreMetadata,
)

private data class LibraryItemMetadataFields(
    val title: String,
    val composer: String?,
    val tags: List<String>,
    val pageCount: Int?,
)

private fun metadataFieldsForLibraryItem(
    metadata: ScoreMetadata,
    originalFileName: String,
): LibraryItemMetadataFields = LibraryItemMetadataFields(
    title = normalizeTitle(metadata.title, originalFileName),
    composer = metadata.composer.name.takeIf(String::isNotBlank),
    tags = normalizeTags(metadata.tags),
    pageCount = metadata.pageCount,
)

private fun applyMetadataToLibraryItem(
    item: SheetMusicItem,
    metadata: ScoreMetadata,
): SheetMusicItem {
    val fields = metadataFieldsForLibraryItem(
        metadata = metadata,
        originalFileName = item.document.originalFileName,
    )
    return item.copy(
        title = fields.title,
        composer = fields.composer,
        tags = fields.tags,
        pageCount = fields.pageCount ?: item.pageCount,
    )
}

private fun normalizeTitle(
    suggestedTitle: String,
    originalFileName: String,
): String = suggestedTitle.trim()
    .ifEmpty { originalFileName.substringBeforeLast('.') }
    .ifEmpty { "Untitled score" }

private fun normalizeContentFingerprint(fingerprint: String?): String? = fingerprint
    ?.trim()
    ?.lowercase()
    ?.ifEmpty { null }

private fun normalizeSetlistName(name: String): String? = name
    .trim()
    .ifEmpty { null }

private fun normalizeSongbookName(name: String): String? = name
    .trim()
    .ifEmpty { null }

private fun sanitizeSetlists(
    setlists: List<LibrarySetlist>,
    items: List<SheetMusicItem>,
): List<LibrarySetlist> {
    val validItemIds = items.mapTo(mutableSetOf()) { it.id }
    return setlists.mapNotNull { setlist ->
        val normalizedName = normalizeSetlistName(setlist.name) ?: return@mapNotNull null
        setlist.copy(
            name = normalizedName,
            itemIds = sanitizeSetlistItemIds(setlist.itemIds, validItemIds),
        )
    }
}

private fun sanitizeSongbooks(songbooks: List<LibrarySongbook>): List<LibrarySongbook> =
    songbooks.mapNotNull { songbook ->
        val normalizedName = normalizeSongbookName(songbook.name) ?: return@mapNotNull null
        val sanitizedDocument = sanitizeSongbookDocument(songbook.document) ?: return@mapNotNull null
        songbook.copy(
            name = normalizedName,
            itemIds = sanitizeSongbookItemIds(songbook.itemIds),
            document = sanitizedDocument,
            pageCount = songbook.pageCount?.takeIf { it > 0 },
        )
    }

private fun sanitizeSetlistItemIds(
    itemIds: List<String>,
    validItemIds: Set<String>,
): List<String> = itemIds
    .map(String::trim)
    .filter(String::isNotEmpty)
    .filter { it in validItemIds }
    .distinct()

private fun sanitizeSongbookItemIds(itemIds: List<String>): List<String> = itemIds
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinct()

private fun sanitizeSongbookDocument(document: PdfDocumentReference): PdfDocumentReference? {
    val storedPath = document.storedPath.trim()
    val originalFileName = document.originalFileName.trim()
    if (storedPath.isEmpty() || originalFileName.isEmpty()) {
        return null
    }

    return document.copy(
        storedPath = storedPath,
        originalFileName = originalFileName,
        sourceUri = document.sourceUri?.trim()?.ifEmpty { null },
        contentFingerprint = document.contentFingerprint?.trim()?.ifEmpty { null },
    )
}

private fun normalizeTags(tags: List<String>): List<String> = tags
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(String::lowercase)
    .sortedWith(String.CASE_INSENSITIVE_ORDER)

private fun sourceFileTypeFromName(fileName: String): String = fileName
    .substringAfterLast('.', missingDelimiterValue = "pdf")
    .trim()
    .lowercase()
    .ifEmpty { "pdf" }

private fun defaultScoreMetadata(
    itemId: String,
    title: String,
    composer: String?,
    tags: List<String>,
    originalFileName: String,
    sourceUri: String?,
    pageCount: Int?,
    contentFingerprint: String?,
    metadataId: String? = null,
): ScoreMetadata {
    val fallbackId = buildScoreMetadataId(
        itemId = itemId,
        contentFingerprint = contentFingerprint,
        metadataId = metadataId,
    )
    return sanitizeScoreMetadata(
        metadata = ScoreMetadata(
            id = fallbackId,
            title = title,
            composer = ScoreMetadataComposer(name = composer.orEmpty()),
            pageCount = pageCount,
            tags = normalizeMetadataStringList(tags),
            source = ScoreMetadataSource(
                filename = originalFileName,
                fileType = sourceFileTypeFromName(originalFileName),
                url = sourceUri.orEmpty(),
            ),
        ),
        fallbackId = fallbackId,
    )
}

private fun mergeScoreMetadata(
    existingMetadata: ScoreMetadata?,
    incomingMetadata: ScoreMetadata?,
): ScoreMetadata {
    val fallbackId = incomingMetadata?.id?.trim().orEmpty()
        .ifEmpty { existingMetadata?.id?.trim().orEmpty() }
        .ifEmpty { "score" }
    val merged = when {
        existingMetadata == null && incomingMetadata == null -> ScoreMetadata(id = fallbackId)
        existingMetadata == null -> incomingMetadata ?: ScoreMetadata(id = fallbackId)
        incomingMetadata == null -> existingMetadata
        else -> existingMetadata.copy(
            schemaId = incomingMetadata.schemaId.takeIf { it.isNotBlank() } ?: existingMetadata.schemaId,
            schemaVersion = incomingMetadata.schemaVersion.takeIf { it.isNotBlank() } ?: existingMetadata.schemaVersion,
            id = incomingMetadata.id.takeIf { it.isNotBlank() } ?: existingMetadata.id,
            title = incomingMetadata.title.takeIf { it.isNotBlank() } ?: existingMetadata.title,
            subtitle = incomingMetadata.subtitle.takeIf { it.isNotBlank() } ?: existingMetadata.subtitle,
            alternativeTitles = incomingMetadata.alternativeTitles.takeIf { it.any(String::isNotBlank) }
                ?: existingMetadata.alternativeTitles,
            composer = mergeScoreMetadataComposer(
                existing = existingMetadata.composer,
                incoming = incomingMetadata.composer,
            ),
            catalogueNumber = incomingMetadata.catalogueNumber.takeIf { it.isNotBlank() } ?: existingMetadata.catalogueNumber,
            opusNumber = incomingMetadata.opusNumber.takeIf { it.isNotBlank() } ?: existingMetadata.opusNumber,
            workNumber = incomingMetadata.workNumber.takeIf { it.isNotBlank() } ?: existingMetadata.workNumber,
            genre = incomingMetadata.genre.takeIf { it.isNotBlank() } ?: existingMetadata.genre,
            form = incomingMetadata.form.takeIf { it.isNotBlank() } ?: existingMetadata.form,
            stylePeriod = incomingMetadata.stylePeriod.takeIf { it.isNotBlank() } ?: existingMetadata.stylePeriod,
            instrumentation = incomingMetadata.instrumentation.takeIf { it.any(String::isNotBlank) }
                ?: existingMetadata.instrumentation,
            key = incomingMetadata.key.takeIf { it.isNotBlank() } ?: existingMetadata.key,
            timeSignature = incomingMetadata.timeSignature.takeIf { it.isNotBlank() } ?: existingMetadata.timeSignature,
            tempoMarkings = incomingMetadata.tempoMarkings.takeIf { it.any(String::isNotBlank) }
                ?: existingMetadata.tempoMarkings,
            movements = incomingMetadata.movements.takeIf { it.any(String::isNotBlank) }
                ?: existingMetadata.movements,
            yearComposed = incomingMetadata.yearComposed ?: existingMetadata.yearComposed,
            publisher = incomingMetadata.publisher.takeIf { it.isNotBlank() } ?: existingMetadata.publisher,
            editor = incomingMetadata.editor.takeIf { it.isNotBlank() } ?: existingMetadata.editor,
            arranger = incomingMetadata.arranger.takeIf { it.isNotBlank() } ?: existingMetadata.arranger,
            edition = incomingMetadata.edition.takeIf { it.isNotBlank() } ?: existingMetadata.edition,
            language = incomingMetadata.language.takeIf { it.isNotBlank() } ?: existingMetadata.language,
            pageCount = incomingMetadata.pageCount ?: existingMetadata.pageCount,
            durationSeconds = incomingMetadata.durationSeconds ?: existingMetadata.durationSeconds,
            difficulty = incomingMetadata.difficulty.takeIf { it.isNotBlank() } ?: existingMetadata.difficulty,
            tags = incomingMetadata.tags.takeIf { it.any(String::isNotBlank) } ?: existingMetadata.tags,
            notes = mergeScoreMetadataNotes(
                existing = existingMetadata.notes,
                incoming = incomingMetadata.notes,
            ),
            source = mergeScoreMetadataSource(
                existing = existingMetadata.source,
                incoming = incomingMetadata.source,
            ),
        )
    }
    return sanitizeScoreMetadata(
        metadata = merged,
        fallbackId = fallbackId,
    )
}

private fun mergeScoreMetadataComposer(
    existing: ScoreMetadataComposer,
    incoming: ScoreMetadataComposer,
): ScoreMetadataComposer = ScoreMetadataComposer(
    name = incoming.name.takeIf { it.isNotBlank() } ?: existing.name,
    birthYear = incoming.birthYear ?: existing.birthYear,
    deathYear = incoming.deathYear ?: existing.deathYear,
)

private fun mergeScoreMetadataSource(
    existing: ScoreMetadataSource,
    incoming: ScoreMetadataSource,
): ScoreMetadataSource = ScoreMetadataSource(
    filename = incoming.filename.takeIf { it.isNotBlank() } ?: existing.filename,
    fileType = incoming.fileType.takeIf { it.isNotBlank() } ?: existing.fileType,
    url = incoming.url.takeIf { it.isNotBlank() } ?: existing.url,
)

private fun mergeScoreMetadataNotes(
    existing: String,
    incoming: String,
): String {
    val normalizedExisting = existing.trim()
    val normalizedIncoming = incoming.trim()
    return when {
        normalizedIncoming.isEmpty() -> normalizedExisting
        normalizedExisting.isEmpty() -> normalizedIncoming
        normalizedIncoming in normalizedExisting -> normalizedExisting
        else -> "$normalizedExisting\n$normalizedIncoming"
    }
}

private fun sanitizeScoreMetadata(
    metadata: ScoreMetadata,
    fallbackId: String,
): ScoreMetadata = metadata.copy(
    schemaId = metadata.schemaId.trim().ifEmpty { com.ryanjames.lunar.library.model.SCORE_METADATA_SCHEMA_ID },
    schemaVersion = metadata.schemaVersion.trim().ifEmpty { com.ryanjames.lunar.library.model.SCORE_METADATA_SCHEMA_VERSION },
    id = metadata.id.trim().ifEmpty { fallbackId.trim().ifEmpty { "score" } },
    title = metadata.title.trim(),
    subtitle = metadata.subtitle.trim(),
    alternativeTitles = normalizeMetadataStringList(metadata.alternativeTitles),
    composer = metadata.composer.copy(
        name = metadata.composer.name.trim(),
    ),
    catalogueNumber = metadata.catalogueNumber.trim(),
    opusNumber = metadata.opusNumber.trim(),
    workNumber = metadata.workNumber.trim(),
    genre = metadata.genre.trim(),
    form = metadata.form.trim(),
    stylePeriod = metadata.stylePeriod.trim(),
    instrumentation = normalizeMetadataStringList(metadata.instrumentation),
    key = metadata.key.trim(),
    timeSignature = metadata.timeSignature.trim(),
    tempoMarkings = normalizeMetadataStringList(metadata.tempoMarkings),
    movements = normalizeMetadataStringList(metadata.movements),
    publisher = metadata.publisher.trim(),
    editor = metadata.editor.trim(),
    arranger = metadata.arranger.trim(),
    edition = metadata.edition.trim(),
    language = metadata.language.trim(),
    pageCount = metadata.pageCount?.takeIf { it > 0 },
    durationSeconds = metadata.durationSeconds?.takeIf { it > 0 },
    difficulty = metadata.difficulty.trim(),
    tags = normalizeMetadataStringList(metadata.tags),
    notes = metadata.notes.trim(),
    source = metadata.source.copy(
        filename = metadata.source.filename.trim(),
        fileType = metadata.source.fileType.trim().ifEmpty { "pdf" },
        url = metadata.source.url.trim(),
    ),
)

private fun normalizeMetadataStringList(values: List<String>): List<String> = values
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(String::lowercase)

private fun buildScoreMetadataId(
    itemId: String,
    contentFingerprint: String?,
    metadataId: String? = null,
): String = metadataId
    ?.trim()
    ?.ifEmpty { null }
    ?: normalizeContentFingerprint(contentFingerprint)
        ?.take(16)
        ?.ifEmpty { null }
    ?: itemId.trim().ifEmpty { "score" }
